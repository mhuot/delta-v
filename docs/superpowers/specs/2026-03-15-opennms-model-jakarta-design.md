# opennms-model-jakarta Design Spec

## Overview

Create `core/opennms-model-jakarta`, an Alarmd-scoped module containing Jakarta Persistence (Hibernate 7) entity classes, JPA `AttributeConverter` implementations, and JPA DAO classes. This module replaces the legacy `opennms-model` + `opennms-dao` dependency chain for the Spring Boot 4 Alarmd microservice, eliminating the javax→jakarta bytecode transformation step entirely.

## Goals

- Alarmd boots as a Spring Boot 4.0.3 application with a real PostgreSQL DataSource, Hibernate 7, and JPA DAOs
- Entity classes use `jakarta.persistence.*` natively — no Eclipse Transformer
- Custom Hibernate 3.6 `UserType` implementations replaced with standard JPA `AttributeConverter`
- JAXB annotations stripped — Jackson-only serialization for the microservice
- Alarmd can receive events via Kafka, create/update/clear alarms, and persist them to PostgreSQL

## Non-Goals

- Migrating all ~32 entity classes from `opennms-model` — only Alarmd's transitive closure
- Maintaining backward compatibility with Karaf consumers — this module is Spring Boot-only
- REST API endpoints — separate future concern
- OpenNMS Criteria → JPA CriteriaBuilder translation (DAOs use HQL for now)

## Entity Scope

Only entities in Alarmd's transitive dependency graph:

| Entity | Table | Why Alarmd Needs It |
|--------|-------|-------------------|
| `OnmsAlarm` | `alarms` | Primary — Alarmd creates/updates/clears alarms |
| `OnmsEvent` | `events` | Every alarm references its triggering event |
| `OnmsNode` | `node` | Alarms reference the node they belong to |
| `OnmsMonitoringSystem` | `monitoringSystems` | Alarms reference the monitoring system |
| `OnmsDistPoller` | `monitoringSystems` | Single-table inheritance subclass of OnmsMonitoringSystem |
| `OnmsServiceType` | `service` | Alarms can reference a service type |
| `OnmsCategory` | `categories` | Nodes have categories (ManyToMany, needed for `@Filter` auth) |
| `OnmsIpInterface` | `ipInterface` | OnmsNode cascades to interfaces; OnmsAlarm has FK to node |
| `OnmsSnmpInterface` | `snmpInterface` | Referenced by OnmsIpInterface |
| `OnmsMonitoredService` | `ifServices` | Referenced by OnmsAlarm directly |

Enums (`OnmsSeverity`, `NodeType`, `NodeLabelSource`, `PrimaryType`) and utility classes (`InetAddressUtils`) are **referenced from existing modules**, not copied.

## Module Structure

```
core/opennms-model-jakarta/
  pom.xml
  src/main/java/org/opennms/netmgt/model/jakarta/
    entity/
      OnmsAlarm.java
      OnmsEvent.java
      OnmsNode.java
      OnmsMonitoringSystem.java
      OnmsDistPoller.java
      OnmsServiceType.java
      OnmsCategory.java
      OnmsIpInterface.java
      OnmsSnmpInterface.java
      OnmsMonitoredService.java
    converter/
      InetAddressConverter.java
      OnmsSeverityConverter.java
      PrimaryTypeConverter.java
      NodeTypeConverter.java
      NodeLabelSourceConverter.java
    dao/
      AlarmDaoJpa.java
      EventDaoJpa.java
      NodeDaoJpa.java
      MonitoringSystemDaoJpa.java
      ServiceTypeDaoJpa.java
  src/test/java/org/opennms/netmgt/model/jakarta/
    converter/
      InetAddressConverterTest.java
      OnmsSeverityConverterTest.java
      PrimaryTypeConverterTest.java
      NodeTypeConverterTest.java
      NodeLabelSourceConverterTest.java
```

## AttributeConverter Design

Five converters replace Hibernate 3.6 `UserType` implementations:

| Converter | Java Type | DB Column Type | Replaces |
|-----------|-----------|---------------|----------|
| `InetAddressConverter` | `InetAddress` ↔ `String` | VARCHAR | `InetAddressUserType` |
| `OnmsSeverityConverter` | `OnmsSeverity` ↔ `Integer` | INTEGER | `OnmsSeverityUserType` |
| `PrimaryTypeConverter` | `PrimaryType` ↔ `String` | CHAR(1) | `PrimaryTypeUserType` + `CharacterUserType` |
| `NodeTypeConverter` | `NodeType` ↔ `String` | CHAR(1) | `NodeTypeUserType` |
| `NodeLabelSourceConverter` | `NodeLabelSource` ↔ `String` | CHAR(1) | `NodeLabelSourceUserType` |

### Converter Pattern

```java
@Converter
public class OnmsSeverityConverter implements AttributeConverter<OnmsSeverity, Integer> {
    @Override
    public Integer convertToDatabaseColumn(OnmsSeverity severity) {
        return severity == null ? null : severity.getId();
    }

    @Override
    public OnmsSeverity convertToEntityAttribute(Integer id) {
        return id == null ? null : OnmsSeverity.get(id);
    }
}
```

All converters are null-safe and bidirectional. Applied to entity fields via `@Convert(converter = XxxConverter.class)`, replacing `@Type(type="org.opennms.netmgt.model.XxxUserType")`.

## Entity Migration Patterns

### Namespace Change

```java
// Before (opennms-model)
import javax.persistence.*;

// After (opennms-model-jakarta)
import jakarta.persistence.*;
```

### @Type → @Convert

```java
// Before
@Type(type="org.opennms.netmgt.model.InetAddressUserType")
@Column(name="ipAddr")
private InetAddress ipAddr;

// After
@Convert(converter = InetAddressConverter.class)
@Column(name="ipAddr")
private InetAddress ipAddr;
```

### JAXB Annotations Stripped

All `@XmlRootElement`, `@XmlElement`, `@XmlAttribute`, `@XmlTransient`, `@XmlJavaTypeAdapter` annotations removed. Jackson `@JsonIgnoreProperties` / `@JsonIgnore` retained where needed for serialization control.

### Hibernate-Specific Annotations Retained

These annotations are valid in Hibernate 7 and remain unchanged:

- `@Filter(name=..., condition="...")` — row-level security
- `@FilterDef(name=...)` — filter definitions
- `@Formula(value="(SELECT ...)")` — read-only computed properties
- `@DiscriminatorOptions(force=true)` — single-table inheritance
- `@Where(clause="...")` — collection filtering (if present)

### Single-Table Inheritance

`OnmsMonitoringSystem` → `OnmsDistPoller` inheritance is standard JPA:

```java
@Entity
@Table(name = "monitoringSystems")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type")
public class OnmsMonitoringSystem { ... }

@Entity
@DiscriminatorValue("OpenNMS")
public class OnmsDistPoller extends OnmsMonitoringSystem { ... }
```

Works identically in Hibernate 7.

### @Temporal Handling

Retained on `java.util.Date` fields for explicitness, though Hibernate 7 infers `TIMESTAMP` automatically.

## DAO Design

Each DAO extends `AbstractDaoJpa<T, K>` (from `daemon-common`) and implements the corresponding interface from `opennms-dao-api`.

| DAO Class | Entity | Interface | Key Queries |
|-----------|--------|-----------|-------------|
| `AlarmDaoJpa` | `OnmsAlarm` | `AlarmDao` | `findByReductionKey(String)`, `findByAlarm(OnmsAlarm)` |
| `EventDaoJpa` | `OnmsEvent` | `EventDao` | `findByEventId(Integer)` |
| `NodeDaoJpa` | `OnmsNode` | `NodeDao` | `get(Integer)`, `findByLabel(String)` |
| `MonitoringSystemDaoJpa` | `OnmsMonitoringSystem` | `MonitoringSystemDao` | `get(String)` |
| `ServiceTypeDaoJpa` | `OnmsServiceType` | `ServiceTypeDao` | `findByName(String)` |

DAOs use HQL via `AbstractDaoJpa.find()` and `findUnique()` helpers. `findMatching(Criteria)` and `countMatching(Criteria)` remain `UnsupportedOperationException` — Alarmd does not use the OpenNMS Criteria API.

DAOs are annotated with `@Repository` for Spring auto-detection and `@Transactional` where needed.

## Maven Dependencies

### opennms-model-jakarta pom.xml

```xml
<dependencies>
    <!-- Jakarta Persistence API (from Spring Boot 4 BOM) -->
    <dependency>
        <groupId>jakarta.persistence</groupId>
        <artifactId>jakarta.persistence-api</artifactId>
    </dependency>

    <!-- Hibernate 7 core (from Spring Boot 4 BOM) -->
    <dependency>
        <groupId>org.hibernate.orm</groupId>
        <artifactId>hibernate-core</artifactId>
    </dependency>

    <!-- DAO interfaces -->
    <dependency>
        <groupId>org.opennms</groupId>
        <artifactId>opennms-dao-api</artifactId>
        <version>${project.version}</version>
        <!-- Exclude all javax/Hibernate 3.6 transitive deps -->
    </dependency>

    <!-- Shared enums and utilities (OnmsSeverity, InetAddressUtils, etc.) -->
    <dependency>
        <groupId>org.opennms</groupId>
        <artifactId>opennms-model</artifactId>
        <version>${project.version}</version>
        <!-- Exclude javax.persistence, Hibernate 3.6, JAXB -->
    </dependency>

    <!-- AbstractDaoJpa base class -->
    <dependency>
        <groupId>org.opennms.core</groupId>
        <artifactId>org.opennms.core.daemon-common</artifactId>
        <version>${project.version}</version>
    </dependency>

    <!-- Jackson for @JsonIgnoreProperties -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-annotations</artifactId>
    </dependency>

    <!-- Testing -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

The module inherits Spring Boot 4.0.3 BOM from `daemon-common`'s dependency management, which pins Hibernate ORM, Jakarta Persistence, and Spring versions.

### daemon-boot-alarmd pom.xml Changes

- **Remove:** `opennms-model` direct dependency
- **Remove:** `javax.persistence:javax.persistence-api:2.2`
- **Add:** `opennms-model-jakarta`
- **Remove:** Eclipse Transformer profile (no longer needed for Alarmd)

## Integration with daemon-boot-alarmd

### EntityScan Update

`DaemonDataSourceConfiguration` in `daemon-common` currently scans `org.opennms.netmgt.model`. For Alarmd, this changes to `org.opennms.netmgt.model.jakarta.entity`. This is configured in the Alarmd boot module, not in daemon-common (daemon-common should not hardcode entity packages).

### AlarmdConfiguration Bean Wiring

The JPA DAOs are auto-discovered via `@Repository` + component scanning of `org.opennms.netmgt.model.jakarta.dao`. They implement the same DAO interfaces (`AlarmDao`, `NodeDao`, etc.) that `AlarmPersisterImpl` and other Alarmd classes inject — no changes to Alarmd source code needed.

### Integration Test

`AlarmdApplicationIT` will be enabled with:
- Testcontainers PostgreSQL (schema loaded via Liquibase or SQL script)
- Testcontainers Kafka
- Verify: Spring context loads → Alarmd starts → send test event via Kafka → alarm created in PostgreSQL → alarm queryable via `AlarmDaoJpa`

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|-----------|
| `@Formula` SQL subqueries behave differently in Hibernate 7 | Alarm `isSituation`/`isInSituation` fields break | Integration test specifically validates formula-derived properties |
| `@Filter` for auth not activated in microservice context | Security gap | Alarmd is internal-only (no external REST yet); filter activation deferred to REST API migration |
| Enum/utility class imports from `opennms-model` pull in javax transitives | Classpath conflicts | Careful `<exclusions>` in POM; only enum classes and `InetAddressUtils` needed |
| `opennms-dao-api` DAO interfaces reference `org.opennms.core.criteria.Criteria` | DAOs must implement or throw | Already handled: `AbstractDaoJpa` throws `UnsupportedOperationException`; Alarmd doesn't use Criteria API |
| HQL queries from legacy DAOs use positional parameters differently | Query failures at runtime | Integration test with real PostgreSQL validates all DAO queries |
