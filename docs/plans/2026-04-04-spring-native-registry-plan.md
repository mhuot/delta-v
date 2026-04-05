# Spring Boot-Native Registry Registration — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace ServiceLoader + reflection registry patterns with explicit Spring `@Bean` registration in a dedicated `core/daemon-registry` module.

**Architecture:** Create `core/daemon-registry` with three `@Configuration` classes (monitors, collectors, detectors) that construct registry beans from explicit lists. Refactor `GenericSnmpDetectorFactory` and all 9 subclasses for constructor injection. Boot configs `@Import` only the registries they need. Minion provides a no-op `SnmpAgentConfigFactory`.

**Tech Stack:** Spring Boot 4.x, Spring Framework 7.x, Maven, Java 21

**Spec:** `docs/plans/2026-04-04-spring-native-registry-spec.md`

---

## File Map

### New Files (core/daemon-registry)

| File | Responsibility |
|------|---------------|
| `core/daemon-registry/pom.xml` | Module POM — depends on poller-monitors-core, snmp-collector, detector modules, protocols/radius |
| `core/daemon-registry/src/main/java/org/opennms/core/daemon/registry/MonitorRegistryConfiguration.java` | `@Configuration` creating `ServiceMonitorRegistry` with 17 monitors |
| `core/daemon-registry/src/main/java/org/opennms/core/daemon/registry/CollectorRegistryConfiguration.java` | `@Configuration` creating `ServiceCollectorRegistry` with 1 collector |
| `core/daemon-registry/src/main/java/org/opennms/core/daemon/registry/DetectorRegistryConfiguration.java` | `@Configuration` creating `ServiceDetectorRegistry` with 21 detector factories |
| `core/daemon-registry/src/main/java/org/opennms/core/daemon/registry/LocalServiceMonitorRegistry.java` | Moved from daemon-common — accepts `List<ServiceMonitor>` |
| `core/daemon-registry/src/main/java/org/opennms/core/daemon/registry/LocalServiceCollectorRegistry.java` | Moved from daemon-common — accepts `List<ServiceCollector>` |
| `core/daemon-registry/src/main/java/org/opennms/core/daemon/registry/LocalServiceDetectorRegistry.java` | Moved from daemon-common — accepts `List<ServiceDetectorFactory<?>>` |
| `core/daemon-registry/src/main/java/org/opennms/core/daemon/registry/NoOpSnmpAgentConfigFactory.java` | Throws `UnsupportedOperationException` — used only by Minion |

### Modified Files

| File | Change |
|------|--------|
| `core/pom.xml` | Add `<module>daemon-registry</module>` |
| `opennms-provision/opennms-detector-simple/.../GenericSnmpDetectorFactory.java` | Constructor injection for `SnmpAgentConfigFactory` |
| `opennms-provision/opennms-detector-simple/.../SnmpDetectorFactory.java` | Pass `SnmpAgentConfigFactory` through constructor |
| `opennms-provision/opennms-detector-simple/.../BgpSessionDetectorFactory.java` | Update constructor (compilation fix) |
| `opennms-provision/opennms-detector-simple/.../CiscoIpSlaDetectorFactory.java` | Update constructor (compilation fix) |
| `opennms-provision/opennms-detector-simple/.../DiskUsageDetectorFactory.java` | Update constructor (compilation fix) |
| `opennms-provision/opennms-detector-simple/.../HostResourceSWRunDetectorFactory.java` | Update constructor (compilation fix) |
| `opennms-provision/opennms-detector-simple/.../OmsaStorageDetectorFactory.java` | Update constructor (compilation fix) |
| `opennms-provision/opennms-detector-simple/.../OpenManageChassisDetectorFactory.java` | Update constructor (compilation fix) |
| `opennms-provision/opennms-detector-simple/.../PercDetectorFactory.java` | Update constructor (compilation fix) |
| `opennms-provision/opennms-detector-simple/.../Win32ServiceDetectorFactory.java` | Update constructor (compilation fix) |
| `core/daemon-boot-pollerd/.../PollerdRpcConfiguration.java` | Remove `serviceMonitorRegistry()` bean, `@Import MonitorRegistryConfiguration` |
| `core/daemon-boot-perspectivepollerd/.../PerspectivePollerdRpcConfiguration.java` | Remove `serviceMonitorRegistry()` bean, `@Import MonitorRegistryConfiguration` |
| `core/daemon-boot-collectd/.../CollectdRpcConfiguration.java` | Remove `serviceCollectorRegistry()` bean + reflection hack, `@Import CollectorRegistryConfiguration` |
| `core/daemon-boot-provisiond/.../ProvisiondBootConfiguration.java` | Remove `SmartLifecycle` bean, `@Import DetectorRegistryConfiguration` |
| `core/daemon-boot-minion/.../PollerConfiguration.java` | Replace `DefaultServiceMonitorRegistry` with `@Import MonitorRegistryConfiguration` |
| `core/daemon-boot-minion/.../CollectorConfiguration.java` | Replace `DefaultServiceCollectorRegistry` with `@Import CollectorRegistryConfiguration` |
| `core/daemon-boot-minion/.../DetectorConfiguration.java` | Replace `LocalServiceDetectorRegistry()` with `@Import DetectorRegistryConfiguration`, add no-op `SnmpAgentConfigFactory` bean |
| `core/daemon-boot-pollerd/pom.xml` | Add `daemon-registry` dependency |
| `core/daemon-boot-perspectivepollerd/pom.xml` | Add `daemon-registry` dependency |
| `core/daemon-boot-collectd/pom.xml` | Add `daemon-registry` dependency |
| `core/daemon-boot-provisiond/pom.xml` | Add `daemon-registry` dependency, remove detector module deps (moved to daemon-registry) |
| `core/daemon-boot-minion/pom.xml` | Add `daemon-registry` dependency, remove detector module deps (moved to daemon-registry) |

### Deleted Files

| File | Why |
|------|-----|
| `core/daemon-common/.../LocalServiceMonitorRegistry.java` | Moved to daemon-registry |
| `core/daemon-common/.../LocalServiceCollectorRegistry.java` | Moved to daemon-registry |
| `core/daemon-common/.../LocalServiceDetectorRegistry.java` | Moved to daemon-registry |
| 6 `META-INF/services/org.opennms.netmgt.provision.ServiceDetectorFactory` files | ServiceLoader no longer used for detectors |

---

## Task 1: Create `core/daemon-registry` Module

**Files:**
- Create: `core/daemon-registry/pom.xml`
- Modify: `core/pom.xml` (add module)

- [ ] **Step 1: Create the module POM**

Create `core/daemon-registry/pom.xml` with dependencies on poller-monitors-core, snmp-collector, all 6 detector modules, and protocols/radius. Include Spring OSGi exclusions (copied from daemon-boot-provisiond for detector modules). Use `jar` packaging.

- [ ] **Step 2: Add module to core/pom.xml**

Add `<module>daemon-registry</module>` to the `<modules>` section in `core/pom.xml`.

- [ ] **Step 3: Verify module compiles**

Run: `./compile.pl -DskipTests --projects :org.opennms.core.daemon-registry -am install`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add core/daemon-registry/pom.xml core/pom.xml
git commit -m "refactor: create core/daemon-registry module skeleton"
```

---

## Task 2: Refactor GenericSnmpDetectorFactory for Constructor Injection

**Files:**
- Modify: `opennms-provision/opennms-detector-simple/src/main/java/org/opennms/netmgt/provision/detector/snmp/GenericSnmpDetectorFactory.java`
- Modify: `opennms-provision/opennms-detector-simple/src/main/java/org/opennms/netmgt/provision/detector/snmp/SnmpDetectorFactory.java`
- Modify: 8 other `*DetectorFactory.java` files in the same package (BgpSession, CiscoIpSla, DiskUsage, HostResourceSWRun, OmsaStorage, OpenManageChassis, Perc, Win32Service)

- [ ] **Step 1: Refactor GenericSnmpDetectorFactory**

Change the constructor to accept `SnmpAgentConfigFactory`:

```java
public GenericSnmpDetectorFactory(Class<T> clazz, SnmpAgentConfigFactory agentConfigFactory) {
    super((Class<SnmpDetector>) clazz);
    this.m_agentConfigFactory = agentConfigFactory;
}
```

Make `m_agentConfigFactory` final. Remove `@Autowired` annotation. Keep the getter (used by `SnmpDetectorFactory.getRuntimeAttributes`). Remove the setter.

- [ ] **Step 2: Update SnmpDetectorFactory**

Change constructor from no-arg to:

```java
public SnmpDetectorFactory(SnmpAgentConfigFactory agentConfigFactory) {
    super(SnmpDetector.class, agentConfigFactory);
}
```

Remove `@Component` annotation (will be instantiated by `DetectorRegistryConfiguration`).

- [ ] **Step 3: Update all 8 non-kept subclasses**

Each changes from `super(XxxDetector.class)` to `super(XxxDetector.class, agentConfigFactory)`:

```java
// Example: BgpSessionDetectorFactory
public BgpSessionDetectorFactory(SnmpAgentConfigFactory agentConfigFactory) {
    super(BgpSessionDetector.class, agentConfigFactory);
}
```

Apply to: BgpSessionDetectorFactory, CiscoIpSlaDetectorFactory, DiskUsageDetectorFactory, HostResourceSWRunDetectorFactory, OmsaStorageDetectorFactory, OpenManageChassisDetectorFactory, PercDetectorFactory, Win32ServiceDetectorFactory.

- [ ] **Step 4: Verify compilation**

Run: `./compile.pl -DskipTests --projects :opennms-detector-simple -am install`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add opennms-provision/opennms-detector-simple/
git commit -m "refactor: convert GenericSnmpDetectorFactory and 9 subclasses to constructor injection"
```

---

## Task 3: Move and Refactor Registry Classes

**Files:**
- Create: `core/daemon-registry/src/main/java/org/opennms/core/daemon/registry/LocalServiceMonitorRegistry.java`
- Create: `core/daemon-registry/src/main/java/org/opennms/core/daemon/registry/LocalServiceCollectorRegistry.java`
- Create: `core/daemon-registry/src/main/java/org/opennms/core/daemon/registry/LocalServiceDetectorRegistry.java`
- Delete: `core/daemon-common/src/main/java/org/opennms/core/daemon/common/registry/LocalServiceMonitorRegistry.java`
- Delete: `core/daemon-common/src/main/java/org/opennms/core/daemon/common/registry/LocalServiceCollectorRegistry.java`
- Delete: `core/daemon-common/src/main/java/org/opennms/core/daemon/common/registry/LocalServiceDetectorRegistry.java`

- [ ] **Step 1: Create LocalServiceMonitorRegistry in daemon-registry**

New package: `org.opennms.core.daemon.registry`. Constructor accepts `List<ServiceMonitor>`. Remove ServiceLoader, remove `EXPLICIT_MONITORS` array, remove all reflection code.

```java
package org.opennms.core.daemon.registry;

public class LocalServiceMonitorRegistry implements ServiceMonitorRegistry {
    private final Map<String, ServiceMonitor> monitorsByClassName = new HashMap<>();

    public LocalServiceMonitorRegistry(List<ServiceMonitor> monitors) {
        for (ServiceMonitor monitor : monitors) {
            monitorsByClassName.put(monitor.getClass().getCanonicalName(), monitor);
            LOG.info("Registered monitor: {}", monitor.getClass().getCanonicalName());
        }
        LOG.info("Loaded {} monitors", monitorsByClassName.size());
    }

    @Override
    public ServiceMonitor getMonitorByClassName(String className) {
        return monitorsByClassName.get(className);
    }

    @Override
    public Set<String> getMonitorClassNames() {
        return Collections.unmodifiableSet(monitorsByClassName.keySet());
    }
}
```

- [ ] **Step 2: Create LocalServiceCollectorRegistry in daemon-registry**

Constructor accepts `List<ServiceCollector>`. Same simplification — no ServiceLoader, no OSGi onBind/onUnbind.

```java
public class LocalServiceCollectorRegistry implements ServiceCollectorRegistry {
    private final Map<String, ServiceCollector> collectorsByClassName = new HashMap<>();

    public LocalServiceCollectorRegistry(List<ServiceCollector> collectors) {
        for (ServiceCollector collector : collectors) {
            collectorsByClassName.put(collector.getClass().getCanonicalName(), collector);
            LOG.info("Registered collector: {}", collector.getClass().getCanonicalName());
        }
        LOG.info("Loaded {} collectors", collectorsByClassName.size());
    }

    @Override
    public CompletableFuture<ServiceCollector> getCollectorFutureByClassName(String className) {
        ServiceCollector collector = collectorsByClassName.get(className);
        if (collector != null) {
            return CompletableFuture.completedFuture(collector);
        }
        return CompletableFuture.failedFuture(
                new IllegalArgumentException("Collector not found: " + className));
    }

    @Override
    public Set<String> getCollectorClassNames() {
        return Collections.unmodifiableSet(collectorsByClassName.keySet());
    }
}
```

- [ ] **Step 3: Create LocalServiceDetectorRegistry in daemon-registry**

Constructor accepts `List<ServiceDetectorFactory<?>>`. Keys by `factory.getDetectorClass().getCanonicalName()`. No ServiceLoader, no `EXPLICIT_DETECTOR_FACTORIES`, no reflection.

- [ ] **Step 4: Delete old registry classes from daemon-common**

Delete all three files from `core/daemon-common/src/main/java/org/opennms/core/daemon/common/registry/`.

- [ ] **Step 5: Update imports in all consumers**

Update import statements in these files from `org.opennms.core.daemon.common.registry.*` to `org.opennms.core.daemon.registry.*`:
- `core/daemon-boot-pollerd/.../PollerdRpcConfiguration.java`
- `core/daemon-boot-perspectivepollerd/.../PerspectivePollerdRpcConfiguration.java`
- `core/daemon-boot-collectd/.../CollectdRpcConfiguration.java`
- `core/daemon-boot-provisiond/.../ProvisiondBootConfiguration.java`
- `core/daemon-boot-minion/.../DetectorConfiguration.java`

- [ ] **Step 6: Verify compilation**

Run: `./compile.pl -DskipTests --projects :org.opennms.core.daemon-registry,:org.opennms.core.daemon-boot-pollerd,:org.opennms.core.daemon-boot-collectd,:org.opennms.core.daemon-boot-provisiond,:org.opennms.core.daemon-boot-minion -am install`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add core/daemon-registry/src/ core/daemon-common/src/ core/daemon-boot-*/
git commit -m "refactor: move registry classes to daemon-registry, accept List via constructor"
```

---

## Task 4: Create Configuration Classes and NoOpSnmpAgentConfigFactory

**Files:**
- Create: `core/daemon-registry/src/main/java/org/opennms/core/daemon/registry/MonitorRegistryConfiguration.java`
- Create: `core/daemon-registry/src/main/java/org/opennms/core/daemon/registry/CollectorRegistryConfiguration.java`
- Create: `core/daemon-registry/src/main/java/org/opennms/core/daemon/registry/DetectorRegistryConfiguration.java`
- Create: `core/daemon-registry/src/main/java/org/opennms/core/daemon/registry/NoOpSnmpAgentConfigFactory.java`

- [ ] **Step 1: Create MonitorRegistryConfiguration**

```java
@Configuration
public class MonitorRegistryConfiguration {
    @Bean
    public ServiceMonitorRegistry serviceMonitorRegistry() {
        return new LocalServiceMonitorRegistry(List.of(
            new IcmpMonitor(),
            new SnmpMonitor(),
            new TcpMonitor(),
            new HttpMonitor(),
            new HttpsMonitor(),
            new DnsMonitor(),
            new SshMonitor(),
            new SSLCertMonitor(),
            new PageSequenceMonitor(),
            new BgpSessionMonitor(),
            new DNSResolutionMonitor(),
            new MinaSshMonitor(),
            new NtpMonitor(),
            new StrafePingMonitor(),
            new WebMonitor(),
            new PassiveServiceMonitor(),
            new RadiusAuthMonitor()
        ));
    }
}
```

- [ ] **Step 2: Create CollectorRegistryConfiguration**

Takes `LocationAwareSnmpClient` as a parameter and injects it into SnmpCollector via setter:

```java
@Configuration
public class CollectorRegistryConfiguration {
    @Bean
    public ServiceCollectorRegistry serviceCollectorRegistry(LocationAwareSnmpClient snmpClient) {
        var snmpCollector = new SnmpCollector();
        snmpCollector.setLocationAwareSnmpClient(snmpClient);
        return new LocalServiceCollectorRegistry(List.of(snmpCollector));
    }
}
```

- [ ] **Step 3: Create NoOpSnmpAgentConfigFactory**

Implements `SnmpAgentConfigFactory`, throws `UnsupportedOperationException` on all methods. Add a log warning in the constructor so any accidental usage is visible.

- [ ] **Step 4: Create DetectorRegistryConfiguration**

Takes `SnmpAgentConfigFactory` as a parameter:

```java
@Configuration
public class DetectorRegistryConfiguration {
    @Bean
    public ServiceDetectorRegistry serviceDetectorRegistry(SnmpAgentConfigFactory snmpAgentConfigFactory) {
        return new LocalServiceDetectorRegistry(List.of(
            new IcmpDetectorFactory(),
            new SnmpDetectorFactory(snmpAgentConfigFactory),
            new SmbDetectorFactory(),
            new LoopDetectorFactory(),
            new TcpDetectorFactory(),
            new HttpDetectorFactory(),
            new HttpsDetectorFactory(),
            new FtpDetectorFactory(),
            new Pop3DetectorFactory(),
            new SmtpDetectorFactory(),
            new ImapDetectorFactory(),
            new ImapsDetectorFactory(),
            new LdapDetectorFactory(),
            new LdapsDetectorFactory(),
            new NrpeDetectorFactory(),
            new MemcachedDetectorFactory(),
            new DnsDetectorFactory(),
            new NtpDetectorFactory(),
            new SshDetectorFactory(),
            new WebDetectorFactory(),
            new Jsr160DetectorFactory()
        ));
    }
}
```

Note: Only `SnmpDetectorFactory` extends `GenericSnmpDetectorFactory` and needs `snmpAgentConfigFactory`. `SmbDetectorFactory` and `LoopDetectorFactory` extend `GenericServiceDetectorFactory` (not SNMP) and use their existing no-arg constructors.

- [ ] **Step 5: Verify compilation**

Run: `./compile.pl -DskipTests --projects :org.opennms.core.daemon-registry -am install`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add core/daemon-registry/src/
git commit -m "refactor: add registry @Configuration classes and NoOpSnmpAgentConfigFactory"
```

---

## Task 5: Update Daemon Boot Configs to @Import Registries

**Files:**
- Modify: `core/daemon-boot-pollerd/.../PollerdRpcConfiguration.java`
- Modify: `core/daemon-boot-perspectivepollerd/.../PerspectivePollerdRpcConfiguration.java`
- Modify: `core/daemon-boot-collectd/.../CollectdRpcConfiguration.java`
- Modify: `core/daemon-boot-provisiond/.../ProvisiondBootConfiguration.java`
- Modify: `core/daemon-boot-minion/.../PollerConfiguration.java`
- Modify: `core/daemon-boot-minion/.../CollectorConfiguration.java`
- Modify: `core/daemon-boot-minion/.../DetectorConfiguration.java`
- Modify: 5 `daemon-boot-*/pom.xml` files

- [ ] **Step 1: Update PollerdRpcConfiguration**

Add `@Import(MonitorRegistryConfiguration.class)` to the class. Remove the `serviceMonitorRegistry()` `@Bean` method (it's now provided by the imported configuration). Keep the `pollerClientRpcModule` bean that consumes the registry.

- [ ] **Step 2: Update PerspectivePollerdRpcConfiguration**

Same pattern as Pollerd — `@Import(MonitorRegistryConfiguration.class)`, remove `serviceMonitorRegistry()`.

- [ ] **Step 3: Update CollectdRpcConfiguration**

`@Import(CollectorRegistryConfiguration.class)`. Remove the `serviceCollectorRegistry()` bean and the reflection-based `setLocationAwareSnmpClient` hack (the `CollectorRegistryConfiguration` handles this cleanly).

- [ ] **Step 4: Update ProvisiondBootConfiguration**

`@Import(DetectorRegistryConfiguration.class)`. Remove the `SmartLifecycle detectorRegistrySnmpConfigInjector` bean entirely. The detector registry already has the SnmpAgentConfigFactory injected via constructor.

- [ ] **Step 5: Update Minion PollerConfiguration**

`@Import(MonitorRegistryConfiguration.class)`. Replace `new DefaultServiceMonitorRegistry()` with removal of the `serviceMonitorRegistry()` bean (now provided by import).

- [ ] **Step 6: Update Minion CollectorConfiguration**

`@Import(CollectorRegistryConfiguration.class)`. Replace `new DefaultServiceCollectorRegistry()` with removal of the `serviceCollectorRegistry()` bean.

- [ ] **Step 7: Update Minion DetectorConfiguration**

`@Import(DetectorRegistryConfiguration.class)`. Replace `new LocalServiceDetectorRegistry()` with removal of the `serviceDetectorRegistry()` bean. Add a `@Bean` for `NoOpSnmpAgentConfigFactory`:

```java
@Bean
public SnmpAgentConfigFactory snmpAgentConfigFactory() {
    return new NoOpSnmpAgentConfigFactory();
}
```

- [ ] **Step 8: Update POM dependencies**

Add `<dependency>` on `org.opennms.core.daemon-registry` to:
- `core/daemon-boot-pollerd/pom.xml`
- `core/daemon-boot-perspectivepollerd/pom.xml`
- `core/daemon-boot-collectd/pom.xml`
- `core/daemon-boot-provisiond/pom.xml`
- `core/daemon-boot-minion/pom.xml`

Remove detector module dependencies from `daemon-boot-provisiond/pom.xml` and `daemon-boot-minion/pom.xml` (they're now transitive via `daemon-registry`).

- [ ] **Step 9: Verify compilation**

Run: `make build`
Expected: BUILD SUCCESS (may have pre-existing test failures in graph/integration-tests — ignore those)

- [ ] **Step 10: Commit**

```bash
git add core/daemon-boot-*/
git commit -m "refactor: replace ServiceLoader with @Import registry configurations in all boot configs"
```

---

## Task 6: Delete ServiceLoader Artifacts

**Files:**
- Delete: 6 `META-INF/services/org.opennms.netmgt.provision.ServiceDetectorFactory` files
- Delete: `META-INF/services/org.opennms.netmgt.poller.ServiceMonitor` from poller-monitors-core (if only used by ServiceLoader)
- Delete: `META-INF/services/org.opennms.netmgt.collection.api.ServiceCollector` from snmp-collector (if only used by ServiceLoader)

- [ ] **Step 1: Delete detector ServiceLoader files**

```bash
rm opennms-provision/opennms-detector-simple/src/main/resources/META-INF/services/org.opennms.netmgt.provision.ServiceDetectorFactory
rm opennms-provision/opennms-detector-lineoriented/src/main/resources/META-INF/services/org.opennms.netmgt.provision.ServiceDetectorFactory
rm opennms-provision/opennms-detector-datagram/src/main/resources/META-INF/services/org.opennms.netmgt.provision.ServiceDetectorFactory
rm opennms-provision/opennms-detector-ssh/src/main/resources/META-INF/services/org.opennms.netmgt.provision.ServiceDetectorFactory
rm opennms-provision/opennms-detector-web/src/main/resources/META-INF/services/org.opennms.netmgt.provision.ServiceDetectorFactory
rm opennms-provision/opennms-detector-jmx/src/main/resources/META-INF/services/org.opennms.netmgt.provision.ServiceDetectorFactory
```

- [ ] **Step 2: Verify build still works**

Run: `make build`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add -u opennms-provision/
git commit -m "refactor: remove ServiceLoader META-INF/services files for detectors"
```

---

## Task 7: Build, Deploy, and Run E2E Tests

- [ ] **Step 1: Build Docker images**

```bash
cd opennms-container/delta-v && bash build.sh deltav
```

Expected: All 16 images built successfully.

- [ ] **Step 2: Deploy with clean restart**

```bash
docker compose --profile full down && docker compose --profile full up -d
```

Wait 45 seconds, verify all 16 services healthy.

- [ ] **Step 3: Verify registry counts in logs**

```bash
docker logs delta-v-provisiond 2>&1 | grep "Loaded.*detector"
# Expected: "Loaded 21 detectors"

docker logs delta-v-pollerd 2>&1 | grep "Loaded.*monitor"
# Expected: "Loaded 17 monitors"

docker logs delta-v-collectd 2>&1 | grep "Loaded.*collector"
# Expected: "Loaded 1 collectors"

docker logs delta-v-minion 2>&1 | grep "Loaded"
# Expected: 17 monitors, 1 collector, 21 detectors
```

- [ ] **Step 4: Verify no ServiceLoader or reflection log lines**

```bash
docker logs delta-v-provisiond 2>&1 | grep -i "ServiceLoader\|via reflection"
# Expected: no output

docker logs delta-v-minion 2>&1 | grep -i "ServiceLoader\|via reflection"
# Expected: no output
```

- [ ] **Step 5: Verify slim daemons don't contain detector JARs**

```bash
# Alarmd should NOT have detector modules
jar tf core/daemon-boot-alarmd/target/*-boot.jar | grep "detector-simple\|detector-lineoriented"
# Expected: no output
```

- [ ] **Step 6: Run all 6 E2E test suites**

Run each sequentially from `opennms-container/delta-v/`:

```bash
./test-e2e.sh --verbose
./test-minion-e2e.sh --verbose
./test-syslog-e2e.sh --verbose
./test-passive-e2e.sh --verbose
./test-collectd-e2e.sh --verbose
./test-enlinkd-e2e.sh --verbose
```

Expected: All suites pass.

- [ ] **Step 7: Push Minion image to labbox and verify**

```bash
docker tag opennms/minion-boot:36.0.0-SNAPSHOT pbrane/minion-boot:latest
docker push pbrane/minion-boot:latest
ssh pbrane@labbox "cd ~/delta-v && docker compose pull && docker compose up -d"
```

Verify 21 detector factories, 17 monitors, 1 collector on labbox Minion.

- [ ] **Step 8: Commit (if any fixes were needed)**

```bash
git commit -m "fix: address E2E test failures from registry migration"
```

---

## Task 8: Create PR

- [ ] **Step 1: Create feature branch and push**

```bash
git checkout -b refactor/spring-native-registries
git push -u origin refactor/spring-native-registries
```

- [ ] **Step 2: Create PR**

```bash
gh pr create --repo pbrane/delta-v --base develop --title "refactor: Spring Boot-native monitor/collector/detector registration" --body "..."
```

Include E2E results table, counts (17 monitors, 1 collector, 21 detectors), and list of deleted patterns (ServiceLoader, EXPLICIT_* arrays, SmartLifecycle, reflection).
