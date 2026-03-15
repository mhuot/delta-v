# EventTranslator Spring Boot 4 Migration Design Spec

## Overview

Migrate the EventTranslator daemon from Karaf/OSGi to Spring Boot 4.0.3. This is the second daemon migration after Alarmd (PR #28), following the same established pattern: Spring Boot fat JAR, `java -jar` in Docker, Kafka event transport, Spring Boot Actuator healthcheck.

## Goals

- EventTranslator boots as a Spring Boot 4.0.3 application
- Receives events via Kafka, applies translation rules, sends translated events back to Kafka
- Reads `translator-configuration.xml` from filesystem (`${OPENNMS_HOME}/etc/`)
- Uses Spring-managed `DataSource` (HikariCP) for SQL value specs — no `DataSourceFactory` static singleton
- Delete the Karaf daemon-loader module and overlay

## Non-Goals

- Changing the translation rule engine or configuration format
- JPA/Hibernate entity integration (EventTranslator uses raw JDBC only)
- REST API endpoints

## Architecture

### Module: `core/daemon-boot-eventtranslator`

Follows the Alarmd pattern exactly. No `opennms-model-jakarta` dependency — EventTranslator doesn't use JPA entities.

### Dependencies

| Dependency | Purpose |
|-----------|---------|
| `daemon-common` | DaemonDataSourceConfiguration, DaemonSmartLifecycle, KafkaEventTransportConfiguration |
| `opennms-services` | EventTranslator daemon class (with ServiceMix/Karaf exclusions) |
| `opennms-config` | EventTranslatorConfigFactory (translation rule engine) |
| `opennms-config-model` | JAXB config models (EventTranslatorConfiguration, EventTranslationSpec, Mapping) |
| `spring-boot-starter-web` | Actuator health endpoint |
| `javax.persistence-api:2.2` | Runtime — legacy classes on classpath reference javax.persistence |
| `javax.xml.bind:jaxb-api:2.3.1` | Runtime — JAXB event XML unmarshalling in KafkaEventSubscriptionService |

### Bean Wiring (`EventTranslatorConfiguration.java`)

```java
@Configuration
public class EventTranslatorConfiguration {

    @Bean
    EventTranslatorConfigFactory eventTranslatorConfig(DataSource dataSource) {
        // Reads ${opennms.home}/etc/translator-configuration.xml
        // Receives Spring-managed DataSource for SQL value specs
    }

    @Bean
    EventTranslator eventTranslator(EventIpcManager eventIpcManager,
                                     EventTranslatorConfig config,
                                     DataSource dataSource) {
        // Setter-injected (EventTranslator uses setters, not constructor injection)
    }

    @Bean
    AnnotationBasedEventListenerAdapter eventTranslatorEventListenerAdapter(
            EventTranslator eventTranslator,
            @Qualifier("kafkaEventSubscriptionService")
            EventSubscriptionService eventSubscriptionService) {
        // Bridges EventTranslator to Kafka event subscription
    }

    @Bean
    SmartLifecycle eventTranslatorLifecycle(EventTranslator eventTranslator) {
        return new DaemonSmartLifecycle(eventTranslator);
    }
}
```

### Application Class

```java
@SpringBootApplication(scanBasePackages = {
    "org.opennms.core.daemon.common",
    "org.opennms.netmgt.translator.boot"
})
public class EventTranslatorApplication { ... }
```

Scans only `daemon.common` (infrastructure) and `translator.boot` (this module's config). Does NOT scan `org.opennms.netmgt.translator` broadly — EventTranslator is explicitly wired in the configuration class.

### Configuration Files

**`application.yml`** — same structure as Alarmd:
- `spring.datasource.*` — HikariCP PostgreSQL connection
- `opennms.home` — path to OpenNMS etc directory
- `opennms.kafka.*` — bootstrap servers, event topic, consumer group
- `server.port: 8080` — Actuator

**`${OPENNMS_HOME}/etc/translator-configuration.xml`** — translation rules, mounted via Docker volume. Not embedded in JAR.

### Docker Compose Changes

```yaml
eventtranslator:
  profiles: [passive, full]
  image: opennms/daemon-deltav:${VERSION}
  entrypoint: []
  command: ["java", "-Xms256m", "-Xmx512m", "-jar", "/opt/daemon-boot-eventtranslator.jar"]
  environment:
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/opennms
    SPRING_DATASOURCE_USERNAME: opennms
    SPRING_DATASOURCE_PASSWORD: opennms
    KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    OPENNMS_HOME: /opt/sentinel
  healthcheck:
    test: ["CMD-SHELL", "curl -sf http://localhost:8080/actuator/health || exit 1"]
```

### What Gets Deleted

| Path | Reason |
|------|--------|
| `core/daemon-loader-eventtranslator/` | Replaced by `daemon-boot-eventtranslator` |
| `opennms-container/delta-v/eventtranslator-overlay/` | Karaf featuresBoot.d no longer needed |
| `opennms-daemon-eventtranslator` feature in `features.xml` | Karaf feature no longer needed |
| `daemon-loader-eventtranslator` module in `core/pom.xml` | Replaced by new module |

### What Gets Added to build.sh

Staging entry for the fat JAR:
```bash
"core/daemon-boot-eventtranslator/target/org.opennms.core.daemon-boot-eventtranslator-$VERSION-boot.jar:daemon-boot-eventtranslator.jar"
```

Dockerfile.daemon COPY line:
```dockerfile
COPY staging/daemon/daemon-boot-eventtranslator.jar /opt/daemon-boot-eventtranslator.jar
```

### DataSource Strategy

EventTranslator uses `DataSource` for SQL value specs in translation rules (e.g., looking up `snmpIfDescr` from the `snmpInterface` table). In the Spring Boot app:

- Spring Boot auto-configures `HikariDataSource` from `spring.datasource.*` properties
- `DaemonDataSourceConfiguration` provides `@EnableTransactionManagement`
- `EventTranslatorConfigFactory` receives the `DataSource` via its constructor
- No JPA `EntityManager` needed — pure JDBC

### Testing

- **Build verification:** Module compiles, fat JAR produced
- **E2E verification:** `test-minion-e2e.sh` Phase 2 validates EventTranslator — it checks for `Translated SNMP_Link_Down event seen in Kafka`. If this passes with the Spring Boot EventTranslator, the migration is validated.

### Risks

| Risk | Mitigation |
|------|-----------|
| `EventTranslatorConfigFactory` uses `ConfigFileConstants` which requires `opennms.home` | Set via environment variable `OPENNMS_HOME` — same as Alarmd |
| Static singleton `EventTranslator.setInstance()` pattern | Wire bean normally, call `setInstance()` in config if needed by other code |
| `DataSourceFactory.init()` called somewhere in init chain | Override with Spring-managed DataSource before init |
