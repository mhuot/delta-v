# Spec: Spring Boot-Native Monitor, Collector & Detector Registration

> **Date:** 2026-04-04
> **Status:** Draft
> **Replaces:** ServiceLoader + reflection pattern from PR #112
> **Blocked by:** None — inventory decisions finalized (pbrane/delta-v#115)

---

## Problem

PR #112 added detector factory registration using Karaf-era patterns: `ServiceLoader` discovery, `EXPLICIT_DETECTOR_FACTORIES` reflection fallback, and a `SmartLifecycle` workaround to inject `SnmpAgentConfigFactory` into ServiceLoader-created instances. The same patterns exist for monitors (`EXPLICIT_MONITORS` in `LocalServiceMonitorRegistry`). These patterns create instances outside Spring's DI container, requiring manual post-construction wiring.

This conflicts with Delta-V's direction: constructor injection (PR #111), Karaf elimination, and explicit Spring Boot configuration.

## Solution

Replace ServiceLoader + reflection with explicit Spring `@Bean` factory methods. Extract shared `@Configuration` classes in a **new `core/daemon-registry` module** (not `daemon-common`) to avoid classpath bloat. Refactor `GenericSnmpDetectorFactory` and all its subclasses to accept `SnmpAgentConfigFactory` via constructor injection. Provide a no-op `SnmpAgentConfigFactory` bean on Minion since it receives SNMP config via RPC request attributes.

## Inventory (from pbrane/delta-v#115)

### Monitors (17)

| Monitor | Source Module |
|---------|-------------|
| IcmpMonitor | poller-monitors-core |
| SnmpMonitor | poller-monitors-core |
| TcpMonitor | poller-monitors-core |
| HttpMonitor | poller-monitors-core |
| HttpsMonitor | poller-monitors-core |
| DnsMonitor | poller-monitors-core |
| SshMonitor | poller-monitors-core |
| SSLCertMonitor | poller-monitors-core |
| PageSequenceMonitor | poller-monitors-core |
| BgpSessionMonitor | poller-monitors-core |
| DNSResolutionMonitor | poller-monitors-core |
| MinaSshMonitor | poller-monitors-core |
| NtpMonitor | poller-monitors-core |
| StrafePingMonitor | poller-monitors-core |
| WebMonitor | poller-monitors-core |
| PassiveServiceMonitor | poller-api |
| RadiusAuthMonitor | protocols/radius |

### Collectors (1)

| Collector | Source Module |
|-----------|-------------|
| SnmpCollector | features/collection/snmp-collector |

### Detectors (21)

| Detector | Factory | Source Module | DI Needs |
|----------|---------|-------------|----------|
| IcmpDetector | IcmpDetectorFactory | opennms-detector-simple | None |
| SnmpDetector | SnmpDetectorFactory | opennms-detector-simple | **SnmpAgentConfigFactory** |
| SmbDetector | SmbDetectorFactory | opennms-detector-simple | None |
| LoopDetector | LoopDetectorFactory | opennms-detector-simple | None |
| TcpDetector | TcpDetectorFactory | opennms-detector-lineoriented | None |
| HttpDetector | HttpDetectorFactory | opennms-detector-lineoriented | None |
| HttpsDetector | HttpsDetectorFactory | opennms-detector-lineoriented | None |
| FtpDetector | FtpDetectorFactory | opennms-detector-lineoriented | None |
| Pop3Detector | Pop3DetectorFactory | opennms-detector-lineoriented | None |
| SmtpDetector | SmtpDetectorFactory | opennms-detector-lineoriented | None |
| ImapDetector | ImapDetectorFactory | opennms-detector-lineoriented | None |
| ImapsDetector | ImapsDetectorFactory | opennms-detector-lineoriented | None |
| LdapDetector | LdapDetectorFactory | opennms-detector-lineoriented | None |
| LdapsDetector | LdapsDetectorFactory | opennms-detector-lineoriented | None |
| NrpeDetector | NrpeDetectorFactory | opennms-detector-lineoriented | None |
| MemcachedDetector | MemcachedDetectorFactory | opennms-detector-lineoriented | None |
| DnsDetector | DnsDetectorFactory | opennms-detector-datagram | None |
| NtpDetector | NtpDetectorFactory | opennms-detector-datagram | None |
| SshDetector | SshDetectorFactory | opennms-detector-ssh | None |
| WebDetector | WebDetectorFactory | opennms-detector-web | None |
| Jsr160Detector | Jsr160DetectorFactory | opennms-detector-jmx | None |

## Architecture

### New Module: `core/daemon-registry`

A dedicated module containing the three `@Configuration` classes and the registry implementations. This avoids classpath bloat — only daemons that need registries (Pollerd, Collectd, Provisiond, Minion, PerspectivePollerd) depend on this module. Daemons that don't (Alarmd, Syslogd, Trapd, EventTranslator, Discovery, Enlinkd, Bsmd, db-init) remain unaffected.

```
core/daemon-registry/
  pom.xml                    — depends on poller-monitors-core, snmp-collector,
                               detector-simple, detector-lineoriented, detector-datagram,
                               detector-ssh, detector-web, detector-jmx, protocols/radius
  src/main/java/org/opennms/core/daemon/registry/
    MonitorRegistryConfiguration.java
    CollectorRegistryConfiguration.java
    DetectorRegistryConfiguration.java
    LocalServiceMonitorRegistry.java       — moved from daemon-common
    LocalServiceCollectorRegistry.java     — moved from daemon-common
    LocalServiceDetectorRegistry.java      — moved from daemon-common
```

#### MonitorRegistryConfiguration

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

#### CollectorRegistryConfiguration

```java
@Configuration
public class CollectorRegistryConfiguration {
    @Bean
    public ServiceCollectorRegistry serviceCollectorRegistry() {
        return new LocalServiceCollectorRegistry(List.of(
            new SnmpCollector()
        ));
    }
}
```

#### DetectorRegistryConfiguration

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

### Registry Class Changes

The three registry classes move from `daemon-common` to `daemon-registry` and change from self-discovering (ServiceLoader + reflection in constructor) to accepting a pre-built list via constructor injection:

```java
public class LocalServiceMonitorRegistry implements ServiceMonitorRegistry {
    private final Map<String, ServiceMonitor> monitorsByClassName;

    public LocalServiceMonitorRegistry(List<ServiceMonitor> monitors) {
        monitorsByClassName = new HashMap<>();
        for (ServiceMonitor monitor : monitors) {
            monitorsByClassName.put(monitor.getClass().getCanonicalName(), monitor);
            LOG.info("Registered monitor: {}", monitor.getClass().getCanonicalName());
        }
        LOG.info("Loaded {} monitors", monitorsByClassName.size());
    }
    // ... existing getter methods unchanged
}
```

Same pattern for `LocalServiceCollectorRegistry` (accepts `List<ServiceCollector>`) and `LocalServiceDetectorRegistry` (accepts `List<ServiceDetectorFactory<?>>`).

### GenericSnmpDetectorFactory Changes

Refactor to accept `SnmpAgentConfigFactory` via constructor instead of `@Autowired` field + setter:

```java
public class GenericSnmpDetectorFactory<T extends SnmpDetector> extends GenericServiceDetectorFactory<SnmpDetector> {
    private final SnmpAgentConfigFactory agentConfigFactory;

    public GenericSnmpDetectorFactory(Class<T> clazz, SnmpAgentConfigFactory agentConfigFactory) {
        super((Class<SnmpDetector>) clazz);
        this.agentConfigFactory = agentConfigFactory;
    }
    // ... remove @Autowired field, remove setter, remove getter
}
```

**All 9 subclasses** must be updated to pass through the factory:

| Subclass | Status |
|----------|--------|
| SnmpDetectorFactory | **Keep** — used in Delta-V |
| BgpSessionDetectorFactory | Not in Delta-V — update constructor for compilation |
| CiscoIpSlaDetectorFactory | Not in Delta-V — update constructor for compilation |
| DiskUsageDetectorFactory | Not in Delta-V — update constructor for compilation |
| HostResourceSWRunDetectorFactory | Not in Delta-V — update constructor for compilation |
| OmsaStorageDetectorFactory | Not in Delta-V — update constructor for compilation |
| OpenManageChassisDetectorFactory | Not in Delta-V — update constructor for compilation |
| PercDetectorFactory | Not in Delta-V — update constructor for compilation |
| Win32ServiceDetectorFactory | Not in Delta-V — update constructor for compilation |

Each non-kept subclass changes from:
```java
public BgpSessionDetectorFactory() {
    super(BgpSessionDetector.class);
}
```
to:
```java
public BgpSessionDetectorFactory(SnmpAgentConfigFactory agentConfigFactory) {
    super(BgpSessionDetector.class, agentConfigFactory);
}
```

### Minion SnmpAgentConfigFactory

Verified: Minion does **not** need a real `SnmpAgentConfigFactory` for detector execution. On the core side (Provisiond), the factory populates `runtimeAttributes` in the RPC request. On the Minion side, `SnmpDetector.getAgentConfig()` reads these attributes from the request — it never calls the factory directly.

`MinionBootConfiguration` provides a no-op bean to satisfy the `DetectorRegistryConfiguration` dependency:

```java
@Bean
public SnmpAgentConfigFactory snmpAgentConfigFactory() {
    // Minion receives SNMP config via RPC request attributes.
    // This no-op satisfies DetectorRegistryConfiguration's dependency.
    return new NoOpSnmpAgentConfigFactory();
}
```

Create `NoOpSnmpAgentConfigFactory` in `core/daemon-registry` (its only consumer). It throws `UnsupportedOperationException` on all methods — any call to it on Minion indicates a bug. If another service needs it in the future, it can be moved to `daemon-common` at that point.

### Daemon Boot Config Changes

Each boot config adds `@Import` for the registries it needs and a dependency on `core/daemon-registry`:

| Boot Config | @Import | POM dependency |
|-------------|---------|---------------|
| PollerdBootConfiguration | `MonitorRegistryConfiguration` | `core/daemon-registry` |
| PerspectivePollerdBootConfiguration | `MonitorRegistryConfiguration` | `core/daemon-registry` |
| CollectdBootConfiguration | `CollectorRegistryConfiguration` | `core/daemon-registry` |
| ProvisiondBootConfiguration | `DetectorRegistryConfiguration` | `core/daemon-registry` |
| MinionBootConfiguration | All three `*RegistryConfiguration` | `core/daemon-registry` |

**Not affected** (no dependency on `daemon-registry`): Alarmd, Bsmd, Discovery, Enlinkd, EventTranslator, Syslogd, Trapd, Telemetryd, db-init.

## What Gets Deleted

| File/Code | Why |
|-----------|-----|
| `EXPLICIT_MONITORS` array + reflection loop in `LocalServiceMonitorRegistry` | Replaced by constructor `List<ServiceMonitor>` |
| `EXPLICIT_DETECTOR_FACTORIES` array + reflection loop in `LocalServiceDetectorRegistry` | Replaced by constructor `List<ServiceDetectorFactory<?>>` |
| ServiceLoader calls in all three registry constructors | Replaced by constructor injection |
| `SmartLifecycle detectorRegistrySnmpConfigInjector` bean in `ProvisiondBootConfiguration` | SnmpAgentConfigFactory now injected via constructor |
| `@Autowired SnmpAgentConfigFactory` field + setter/getter in `GenericSnmpDetectorFactory` | Replaced by constructor parameter + final field |
| 6 `META-INF/services/org.opennms.netmgt.provision.ServiceDetectorFactory` files | ServiceLoader no longer used |
| `META-INF/services/org.opennms.netmgt.poller.ServiceMonitor` in poller-monitors-core | ServiceLoader no longer used |
| `META-INF/services/org.opennms.netmgt.collection.api.ServiceCollector` in snmp-collector | ServiceLoader no longer used |
| Karaf/OSGi comments referencing bundle boundaries and DynamicImport-Package | Dead context |
| Registry classes from `daemon-common` (moved to `daemon-registry`) | Relocated |

## What Gets Removed from POMs

Collector modules no longer needed:
- `features/collection/collectors` (HttpCollector, Jsr160Collector)
- `features/prometheus-collector`
- `features/jdbc-collector`
- `features/wsman`
- `opennms-wmi`
- `protocols/xml`
- `features/juniper-tca-collector`

Monitor modules no longer needed:
- `protocols/cifs`
- `protocols/selenium`
- `opennms-wmi`
- `features/wsman`

Spring OSGi exclusion blocks in `daemon-boot-provisiond/pom.xml` and `daemon-boot-minion/pom.xml` can be consolidated since we're trimming the dependency list. The exclusions move to `daemon-registry/pom.xml` — one copy instead of two.

## Testing

1. **Unit tests**: Verify each registry accepts its list and returns correct lookups
2. **Boot smoke test**: Each daemon starts and logs the expected number of monitors/collectors/detectors
3. **Slim daemon verification**: Confirm Alarmd, Syslogd, Trapd, etc. do NOT contain monitor/collector/detector JARs in their fat JARs
4. **E2E suites**: All 6 suites must pass (test-e2e, test-minion-e2e, test-syslog-e2e, test-passive-e2e, test-collectd-e2e, test-enlinkd-e2e)
5. **Detector verification**: Provisiond logs "21 detector factories", all SNMP detection works
6. **Minion verification**: Minion logs same counts, RPC detection works end-to-end, no-op SnmpAgentConfigFactory is never called

## Adding a Monitor/Collector/Detector in the Future

To add a new capability (e.g., if voting adds something back):

1. Add the Maven dependency to `core/daemon-registry/pom.xml` (with Spring OSGi exclusions if needed)
2. Add one line to the appropriate `*RegistryConfiguration.java` — e.g., `new FooMonitor()`
3. If it needs DI, add it as a parameter to the `@Bean` method
4. Build and deploy

No ServiceLoader files, no reflection arrays, no SmartLifecycle workarounds.

## Scope Note

This spec covers the registration mechanism only. The underlying monitor/collector/detector implementation classes are not modified (except `GenericSnmpDetectorFactory` and its 9 subclasses for constructor injection). Dropping unused modules from the Maven reactor is a separate future task.
