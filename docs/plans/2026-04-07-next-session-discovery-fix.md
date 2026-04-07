# Next Session: Fix Discovery Daemon Wiring + Image Bloat

> Copy everything below the line into the next Claude Code conversation.

---

## Context

PR3 (#122) is merged: Delta-V now consumes horizon modules as pre-built JARs from GitHub Packages. All daemons are healthy except **Discovery**, which fails to start with a missing `ServiceDetectorRegistry` bean. Discovery's Docker image is also 24% larger than expected due to a fallback extraction path caused by ServiceMix Spring 4.x JARs on the classpath.

Both issues share the same root cause: Discovery needs the `daemon-registry` module (which provides `DetectorRegistryConfiguration`), but adding it naively pulls in `CollectorRegistryConfiguration` and `MonitorRegistryConfiguration` which demand beans Discovery doesn't have, plus ServiceMix Spring 4.x transitive deps that conflict with Spring 7.

## The Two Problems

### 1. Missing `ServiceDetectorRegistry` bean (Discovery won't start)

```
Error creating bean with name 'detectorClientRpcModule':
  Unsatisfied dependency expressed through field 'serviceDetectorRegistry':
  No qualifying bean of type 'ServiceDetectorRegistry' available
```

- `DetectorClientRpcModule` (in `opennms-detectorclient-rpc`) has `@Autowired ServiceDetectorRegistry`
- The bean is created by `DetectorRegistryConfiguration` in `core/daemon-registry`
- Discovery's `DiscoveryApplication` scans `org.opennms.core.daemon.common` and `org.opennms.netmgt.discovery.boot` — does NOT scan `org.opennms.core.daemon.registry`
- Simply adding daemon-registry as a dep + adding the package to `scanBasePackages` fails because `CollectorRegistryConfiguration` and `MonitorRegistryConfiguration` (same package) demand `LocationAwareSnmpClient` and other beans Discovery doesn't provide

### 2. +24% Docker image bloat (487 MB → 596 MB)

- ServiceMix Spring 4.x bundles (`org.apache.servicemix.bundles.spring-core-4.2.9.RELEASE_1.jar`, etc.) leak into Discovery's fat JAR via horizon transitive deps
- These have a DIFFERENT Maven groupId (`org.apache.servicemix.bundles`) so Spring Boot's BOM doesn't suppress them
- `compute-shared-libs.sh` uses `java -Djarmode=tools -jar` to extract fat JARs — this fails when old Spring 4 `Assert.state(boolean, Supplier)` is missing, causing fallback to `jar xf` which doesn't strip Boot loader overhead
- Other daemons have this same ServiceMix contamination but it doesn't trigger the extraction failure (they have fewer ServiceMix bundles — Discovery has ALL of them)

## Proposed Fix

### Option A: Split registry configurations (recommended)

1. In `core/daemon-registry`, split `DetectorRegistryConfiguration`, `MonitorRegistryConfiguration`, and `CollectorRegistryConfiguration` into separate packages or add `@ConditionalOnBean` guards:

```java
@Configuration
@ConditionalOnBean(LocationAwareSnmpClient.class)
public class CollectorRegistryConfiguration { ... }

@Configuration  
@ConditionalOnBean(LocationAwareSnmpClient.class)
public class MonitorRegistryConfiguration { ... }

@Configuration  // No condition — always available
public class DetectorRegistryConfiguration { ... }
```

2. Add `daemon-registry` as a dependency to `daemon-boot-discovery` with wildcard ServiceMix exclusion:
```xml
<dependency>
    <groupId>org.opennms.core</groupId>
    <artifactId>org.opennms.core.daemon-registry</artifactId>
    <version>${project.version}</version>
    <exclusions>
        <exclusion>
            <groupId>org.apache.servicemix.bundles</groupId>
            <artifactId>*</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

3. Add `org.opennms.core.daemon.registry` to `DiscoveryApplication.scanBasePackages`

4. Verify: `jarmode=tools` extraction succeeds → image size drops back to ~500 MB range

### Option B: Wire DetectorRegistry directly in Discovery

Skip the daemon-registry dependency entirely. Create the `ServiceDetectorRegistry` bean in `DiscoveryBootConfiguration`:

```java
@Bean
public ServiceDetectorRegistry serviceDetectorRegistry() {
    // Discovery only needs detector registry, not the full set of factories
    return new LocalServiceDetectorRegistry(List.of(
        new IcmpDetectorFactory(),
        new SnmpDetectorFactory(snmpAgentConfigFactory),
        // ... minimal set needed for discovery
    ));
}
```

This avoids the ServiceMix contamination entirely but duplicates the factory list.

## Key Files

- `core/daemon-registry/src/main/java/org/opennms/core/daemon/registry/DetectorRegistryConfiguration.java`
- `core/daemon-registry/src/main/java/org/opennms/core/daemon/registry/CollectorRegistryConfiguration.java`  
- `core/daemon-registry/src/main/java/org/opennms/core/daemon/registry/MonitorRegistryConfiguration.java`
- `core/daemon-boot-discovery/pom.xml`
- `core/daemon-boot-discovery/src/main/java/org/opennms/netmgt/discovery/boot/DiscoveryApplication.java`
- `core/daemon-boot-discovery/src/main/java/org/opennms/netmgt/discovery/boot/DiscoveryBootConfiguration.java`
- `opennms-container/delta-v/compute-shared-libs.sh` (fallback extraction — revert once ServiceMix is excluded)

## Verification

1. `./mvnw clean install -DskipTests` — still passes
2. Discovery starts healthy in Docker
3. Discovery image size ≤ 520 MB (down from 596 MB)
4. `compute-shared-libs.sh` uses `jarmode=tools` for all 12 daemons (no fallback)
5. All 88 E2E tests still pass

## Important Reminders

- **Never create PRs against OpenNMS/opennms** — always `--repo pbrane/delta-v`
- **Always `git pull develop` before creating feature branches**
- **Delta-V version is now `0.0.1-SNAPSHOT`** (not `36.0.0-SNAPSHOT`)
- **Horizon JARs are version `1.0.3`** from GitHub Packages (property: `${deltav.horizon.version}`)
- **BOM precedence**: spring-boot-dependencies imported BEFORE horizon BOM in root POM
