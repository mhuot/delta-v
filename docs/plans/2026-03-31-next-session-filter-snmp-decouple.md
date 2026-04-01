# Next Session: FilterDao + SnmpPeerFactory Decoupling — Karaf Removal Phase 4 Final

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
- DatabaseSchemaConfigFactory + CollectdConfigFactory: PR #98, MERGED — DefaultDatabaseSchemaConfig and DefaultCollectdConfigFactory in config-api
- **PollerConfigFactory: PR #99, MERGED** — moved to features/poller/config, FilterDao injected, Jackson XmlMapper

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
| **enlinkd** | SnmpPeerFactory | **THIS SESSION** |
| **provisiond** | SnmpPeerFactory (transitive) | **THIS SESSION — verify** |
| **pollerd** | FilterDaoFactory, JdbcFilterDao, SnmpPeerFactory | **THIS SESSION** |
| **perspectivepollerd** | FilterDaoFactory, JdbcFilterDao, SnmpPeerFactory | **THIS SESSION** |
| **collectd** | DataCollectionConfigFactory, DefaultDataCollectionConfigDao, DefaultResourceTypesDao, FilterDaoFactory, JdbcFilterDao, SnmpPeerFactory | **NEXT SESSION** (DataCollection factories are complex) |

## Goal: Free pollerd, perspectivepollerd, enlinkd, provisiond from opennms-config

Two classes need to move: **JdbcFilterDao** (the implementation) and **SnmpPeerFactory**. FilterDaoFactory is just a static holder that goes with JdbcFilterDao.

### Tier 1: Move FilterDaoFactory + JdbcFilterDao to core/filter

**Current location:** `opennms-config/src/main/java/org/opennms/netmgt/filter/`

**Files to move:**
- `FilterDaoFactory.java` (~93 lines) — static singleton holder
- `JdbcFilterDao.java` (~500 lines) — JDBC-backed FilterDao implementation

**Existing interface:** `FilterDao` is already in `opennms-config-api` at `org.opennms.netmgt.filter.api.FilterDao`

**JdbcFilterDao dependencies:**
- `org.opennms.netmgt.config.api.DatabaseSchemaConfig` — interface in config-api ✓
- `org.opennms.netmgt.config.filter.Table` — model class in config-jaxb ✓
- `javax.sql.DataSource` — standard Java ✓
- `org.springframework.cache.annotation.Cacheable` — Spring framework ✓
- SQL parsing logic is self-contained ✓

**FilterDaoFactory dependencies:**
- `org.opennms.core.db.DataSourceFactory` — in core/db ✓
- `org.opennms.netmgt.config.DatabaseSchemaConfigFactory` — in opennms-config ✗ (BUT only used in legacy init() path)

**Key insight:** The daemon-boot modules already create JdbcFilterDao as a Spring bean with injected DataSource and DefaultDatabaseSchemaConfig (from config-api). They call `FilterDaoFactory.setInstance()` as a bridge. The legacy `FilterDaoFactory.init()` method (which uses DatabaseSchemaConfigFactory from opennms-config) is NOT called by any daemon-boot module.

**Approach:**
1. Create `core/filter` module
2. Move JdbcFilterDao and FilterDaoFactory there
3. `core/filter` depends on: config-api (FilterDao, DatabaseSchemaConfig), config-jaxb (filter.Table), core/db (DataSourceFactory), spring-context (caching)
4. Remove `FilterDaoFactory.init()` legacy method (uses DatabaseSchemaConfigFactory) — Karaf is dead
5. Keep `FilterDaoFactory.setInstance()/getInstance()` for the bridge pattern
6. Update daemon-boot POMs: replace opennms-config filter imports with core/filter

**Consumers of FilterDaoFactory/JdbcFilterDao (daemon-boots):**
```
core/daemon-boot-pollerd/.../PollerdJpaConfiguration.java
core/daemon-boot-perspectivepollerd/.../PerspectivePollerdJpaConfiguration.java
core/daemon-boot-collectd/.../CollectdJpaConfiguration.java
```

### Tier 2: Move SnmpPeerFactory out of opennms-config

SnmpPeerFactory is 1379 lines in `opennms-config/src/main/java/org/opennms/netmgt/config/SnmpPeerFactory.java`. PR #97 added a new constructor taking `(SnmpConfig, EntityScopeProvider, ServerSnmpConfigProfileMapper)` and replaced internal JaxbUtils/BeanUtils with Jackson XmlMapper.

**All 5 daemon-boot modules** that use SnmpPeerFactory already use the new constructor:
```java
var config = XML_MAPPER.readValue(configFile, SnmpConfig.class);
var factory = new SnmpPeerFactory(config, entityScopeProvider, null);
SnmpPeerFactory.setInstance(factory);
```

**SnmpPeerFactory dependencies (after PR #97):**
- `org.opennms.netmgt.config.api.SnmpAgentConfigFactory` — interface in config-api ✓
- `org.opennms.netmgt.config.snmp.*` — model in config-jaxb ✓
- `org.opennms.core.mate.api.EntityScopeProvider` — in core/mate/api ✓
- `org.opennms.core.network.*` — in core/lib ✓
- Jackson XmlMapper — ✓
- `org.opennms.netmgt.config.server.LookupService` — in `opennms-config-api` ✓
- `org.opennms.netmgt.config.server.ServerSnmpConfigProfileMapper` — check location

**Destination:** Move to `core/snmp/config` (new module) or into existing `core/snmp/api`. The snmp module tree is:
```
core/snmp/
├── api/          — SnmpAgentConfig, SnmpStrategy
├── commands/     — Karaf shell (legacy)
├── impl-joesnmp/ — JoeSnmp implementation
├── impl-snmp4j/  — SNMP4J implementation
├── integration-tests/
├── joesnmp/      — JoeSnmp library
├── proxy-rpc-impl/
└── proxy-rpc-tests/
```

`core/snmp/config` is the natural home — SnmpPeerFactory is config loading, not SNMP protocol.

**Approach:**
1. Create `core/snmp/config` module (or if simpler, add to existing `core/snmp/api`)
2. Move SnmpPeerFactory, SnmpConfigManager (if it exists), and related classes
3. Update all 5 daemon-boot POMs that import SnmpPeerFactory
4. Update features/poller/config if it references SnmpPeerFactory (unlikely)

**Daemon-boots using SnmpPeerFactory:**
- daemon-boot-pollerd
- daemon-boot-perspectivepollerd
- daemon-boot-collectd
- daemon-boot-enlinkd
- daemon-boot-provisiond (verify — may be transitive only)

### Tier 3: Verify enlinkd and provisiond

After Tier 1 + Tier 2, check:
- Can opennms-config be removed from daemon-boot-enlinkd? (EnhancedLinkdConfigFactory is already in features/enlinkd/config)
- Can opennms-config be removed from daemon-boot-provisiond? (No direct opennms-config imports expected)

### Tier 4 (Defer): Collectd DataCollection factories

After Tier 1-3, collectd is the only daemon left needing opennms-config, and only for:
- `DataCollectionConfigFactory` (112 lines) — static singleton
- `DefaultDataCollectionConfigDao` (724 lines) — complex XML loading with external group files
- `DataCollectionConfigParser` (326 lines) — parses etc/datacollection/*.xml
- `DefaultResourceTypesDao` (100 lines) — depends on DataCollectionConfigFactory.getInstance()

These use JAXB (JaxbUtils), BeanUtils, FileReloadContainer — all legacy patterns. They should move to `features/collection/config` following the poller/config pattern, with Jackson XmlMapper replacing JAXB.

**Defer to a dedicated session** — these are ~1262 lines of complex config parsing logic.

## Recommended Session Order

1. **Create `core/filter` module** — move FilterDaoFactory + JdbcFilterDao
2. **Update 3 daemon-boot POMs** — pollerd, perspectivepollerd, collectd
3. **Create `core/snmp/config` module** — move SnmpPeerFactory + related classes
4. **Update 5 daemon-boot POMs** — all daemons using SnmpPeerFactory
5. **Verify: Remove opennms-config from daemon-boot-pollerd** — should now be fully free
6. **Verify: Remove opennms-config from daemon-boot-perspectivepollerd** — should now be fully free
7. **Verify: Remove opennms-config from daemon-boot-enlinkd** — should now be fully free
8. **Verify: Check daemon-boot-provisiond** — likely already free
9. **Build all 13 daemon-boot modules** — verify compilation

## Daemon Scoreboard After This Session

| Daemon | Expected Status |
|--------|----------------|
| alarmd | **FREE** |
| bsmd | **FREE** |
| telemetryd | **FREE** |
| trapd | **FREE** |
| syslogd | **FREE** |
| minion | **FREE** |
| discovery | **FREE** |
| eventtranslator | **FREE** |
| enlinkd | **FREE** ← NEW |
| provisiond | **FREE** ← NEW |
| pollerd | **FREE** ← NEW |
| perspectivepollerd | **FREE** ← NEW |
| collectd | DataCollection factories only (opennms-config dep remains) |

**12 of 13 daemons free of opennms-config.** Only collectd remains, needing one more session for DataCollection factories.

## Established Patterns

All config factory decoupling follows these proven patterns:
- **Simple factories** (Trapd, Syslogd, DatabaseSchema): Jackson XmlMapper in daemon-boot, new Default* impl in config-api
- **Complex factories** (Discovery, Poller): Move entire factory to feature module, keep static init()/getInstance() pattern
- **Constructor-injected factories** (Collectd, Poller): FilterDao/EntityScopeProvider via DI, no static singletons
- **Infrastructure classes** (JdbcFilterDao, SnmpPeerFactory): Move to core/* modules, keep setInstance() bridge

## Pre-existing Issues (ignore)

- `integration-tests/config` has a pre-existing test compilation failure (missing `opennms-provision-persistence`)
- The `opennms` assembly module has a pre-existing maven-assembly-plugin failure — does not affect daemon-boot builds
- `daemon-boot-eventtranslator/pom.xml` has a duplicate dependency warning (`org.opennms.core.model-api`) — cosmetic
