# Next Session: PollerConfigFactory Decoupling — Karaf Removal Phase 4

> Copy everything below the line into the next Claude Code conversation.

---

## Context

Design spec: `docs/superpowers/specs/2026-03-30-karaf-removal-design.md`
Phase 4 progress:
- Trapd: PR #91, MERGED — established Jackson XmlMapper pattern
- Syslogd: PR #93, MERGED — interface to config-api
- EventTranslator: PR #95, MERGED — interface to config-api, Jackson XmlMapper
- Discovery: PR #96, MERGED — DiscoveryConfigFactory moved to features/discovery
- SnmpPeerFactory: PR #97, MERGED — new constructor, Jackson XmlMapper, 5 daemon-boot modules updated
- **DatabaseSchemaConfigFactory + CollectdConfigFactory: PR #98, MERGED** — DefaultDatabaseSchemaConfig and DefaultCollectdConfigFactory in config-api

## Current State: Daemons vs opennms-config

| Daemon | Remaining opennms-config classes used | Status |
|--------|---------------------------------------|--------|
| alarmd | — | **FREE** |
| bsmd | — | **FREE** |
| telemetryd | — | **FREE** |
| trapd | — | **FREE** (PR #91) |
| syslogd | — | **FREE** (PR #93) |
| minion | — | **FREE** |
| discovery | — | **FREE** (PR #96) |
| eventtranslator | — | **FREE** (PR #95) |
| enlinkd | SnmpPeerFactory | SnmpPeerFactory class in opennms-config (48 refs) |
| provisiond | SnmpPeerFactory (transitive via opennms-provision-persistence) | Same blocker |
| **pollerd** | PollerConfigFactory, PollerConfig, SnmpPeerFactory, FilterDaoFactory, JdbcFilterDao | **THIS SESSION** |
| **perspectivepollerd** | PollerConfigFactory, PollerConfig, SnmpPeerFactory, FilterDaoFactory, JdbcFilterDao | **THIS SESSION** |
| **collectd** | DataCollectionConfigFactory, DefaultDataCollectionConfigDao, DefaultResourceTypesDao, SnmpPeerFactory, FilterDaoFactory, JdbcFilterDao | **NEXT SESSION** |

## Goal: Decouple PollerConfigFactory

PollerConfigFactory is the biggest remaining blocker for pollerd and perspectivepollerd. It's fundamentally different from the simple config factories already decoupled — it contains complex runtime business logic.

### Architecture

```
PollerConfig (interface, 45 methods)
  ├── extends PathOutageConfig (in config-api)
  └── implemented by PollerConfigManager (abstract, 1231 lines)
        └── extended by PollerConfigFactory (singleton, ~244 lines)
```

**Key files:**
- Interface: `opennms-config/src/main/java/org/opennms/netmgt/config/PollerConfig.java` (429 lines)
- Abstract base: `opennms-config/src/main/java/org/opennms/netmgt/config/PollerConfigManager.java` (1231 lines)
- Factory: `opennms-config/src/main/java/org/opennms/netmgt/config/PollerConfigFactory.java` (244 lines)

### Why It's Hard

1. **PollerConfig interface depends on `features/poller/api` types** — `ServiceMonitor`, `ServiceMonitorLocator`, `ServiceMonitorRegistry`. Can't move interface to `opennms-config-api` without adding features/poller/api as a dep (inverts dependency direction).

2. **PollerConfigManager has runtime dependencies** — uses `FilterDaoFactory.getInstance()` for filter rule validation, `IpListFromUrl` for URL-based IP lists, `JaxbUtils` for XML marshaling.

3. **Model classes are in opennms-config-jaxb** — `PollerConfiguration`, `Package`, `Service`, `Monitor`, `Parameter` — these are already in the right place.

### Recommended Approach: Move to `features/poller/config`

Follow the Discovery pattern (PR #96) — move the entire factory to a feature module:

1. **Create `features/poller/config` module** (or use existing enlinkd-style `features/poller/config` if it exists)
2. **Move PollerConfig interface** from opennms-config to `features/poller/config`
3. **Move PollerConfigManager + PollerConfigFactory** from opennms-config to `features/poller/config`
4. **Update all 15 files** that import `org.opennms.netmgt.config.PollerConfig` to use the new module location
5. **Update daemon-boot-pollerd and daemon-boot-perspectivepollerd** POMs to depend on `features/poller/config` instead of opennms-config (for Poller config)

**Why this approach works:**
- `features/poller/config` can depend on `features/poller/api` (natural direction)
- `features/poller/config` can depend on `opennms-config-jaxb` (model classes)
- `features/poller/config` can depend on `opennms-config` for FilterDaoFactory/JdbcFilterDao (temporary — these move later)
- No need to rewrite 1231 lines of business logic
- Package name stays `org.opennms.netmgt.config` (split package is fine, Karaf is dead)

### Consumers of PollerConfig (15 files to update)

```
core/daemon-boot-pollerd/.../PollerdDaemonConfiguration.java
core/daemon-boot-pollerd/.../StandalonePollContext.java
core/daemon-boot-perspectivepollerd/.../PerspectivePollerdDaemonConfiguration.java
features/poller/impl/.../Poller.java
features/poller/impl/.../PollerEventProcessor.java
features/poller/impl/.../DefaultPollContext.java
features/poller/impl/.../PollableServiceConfig.java
features/poller/impl/.../StatusStoringServiceMonitorAdaptor.java
features/poller/impl/.../LatencyStoringServiceMonitorAdaptor.java
features/poller/shell/.../Poll.java
features/perspectivepoller/.../PerspectivePollerd.java
features/api-layer/core/.../PollerConfExtensionManager.java
opennms-webapp/.../PollerConfigServlet.java
opennms-webapp-rest/.../ForeignSourceConfigRestService.java
```

### Initialization Pattern in Daemon-Boot

Both pollerd and perspectivepollerd use identical pattern:
```java
@Bean
@DependsOn("filterDaoInitializer")
public PollerConfig pollerConfig() throws IOException {
    LOG.info("Initializing PollerConfigFactory");
    PollerConfigFactory.init();
    return PollerConfigFactory.getInstance();
}
```

After the move, this stays the same — only the Maven dependency changes from opennms-config to features/poller/config.

### Bonus: Replace JaxbUtils in PollerConfigManager

While moving the files, replace `JaxbUtils.unmarshal()` and `JaxbUtils.marshal()` with Jackson XmlMapper. This eliminates the dependency on `core/xml` (which provides JaxbUtils). Follow the established pattern:
```java
private static final XmlMapper XML_MAPPER;
static {
    XML_MAPPER = XmlMapper.builder().defaultUseWrapper(false).build();
    XML_MAPPER.registerModule(new JaxbAnnotationModule());
    XML_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
}
```

## Also Consider: FilterDaoFactory + JdbcFilterDao

FilterDaoFactory and JdbcFilterDao are cross-cutting infrastructure used by all 3 remaining daemons (pollerd, perspectivepollerd, collectd). They live in opennms-config but logically belong in a filter module.

**Current location:** `opennms-config/src/main/java/org/opennms/netmgt/filter/`
**Interface location:** `opennms-config-api/src/main/java/org/opennms/netmgt/filter/api/FilterDao.java`

Moving these to a new `core/filter` module (or into config-api) would help all 3 daemons. JdbcFilterDao depends on:
- `DatabaseSchemaConfig` (now in config-api via DefaultDatabaseSchemaConfig)
- `DataSource` (standard Java)
- SQL parsing logic (self-contained)

This is a moderate effort that unlocks further decoupling.

## Recommended Session Order

1. **Move PollerConfig + PollerConfigFactory + PollerConfigManager** to `features/poller/config`
2. **Replace JaxbUtils** with Jackson XmlMapper in the moved classes
3. **Update all 15 consumer imports**
4. **Verify** pollerd + perspectivepollerd compile without opennms-config for Poller config
5. **If time permits:** Move FilterDaoFactory + JdbcFilterDao to a filter module

## Established Patterns

All config factory decoupling follows these proven patterns:
- **Simple factories** (Trapd, Syslogd, DatabaseSchema): Jackson XmlMapper in daemon-boot, new Default* impl in config-api
- **Complex factories** (Discovery): Move entire factory to feature module, keep static init()/getInstance() pattern
- **CollectdConfigFactory**: Jackson XmlMapper loading + constructor-injected FilterDao (proper DI)
- **PollerConfigFactory**: Use the Discovery pattern — move to feature module (too much business logic to rewrite)

## Pre-existing Issues (ignore)

- `integration-tests/config` has a pre-existing test compilation failure (missing `opennms-provision-persistence`)
- The `opennms` assembly module has a pre-existing maven-assembly-plugin failure — does not affect daemon-boot builds
- `daemon-boot-eventtranslator/pom.xml` has a duplicate dependency warning (`org.opennms.core.model-api`) — cosmetic
