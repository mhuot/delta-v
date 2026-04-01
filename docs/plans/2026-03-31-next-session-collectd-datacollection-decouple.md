# Next Session: Collectd DataCollection Factories — Karaf Removal Phase 4 Final

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
- PollerConfigFactory: PR #99, MERGED — moved to features/poller/config, FilterDao injected, Jackson XmlMapper
- **FilterDaoFactory + JdbcFilterDao + SnmpPeerFactory: PR #100, MERGED** — core/filter + core/snmp/config modules, 12/13 daemons free

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
| enlinkd | — | **FREE** (PR #100) |
| provisiond | — | **FREE** (PR #100) |
| pollerd | — | **FREE** (PR #100) |
| perspectivepollerd | — | **FREE** (PR #100) |
| **collectd** | DataCollectionConfigFactory, DefaultDataCollectionConfigDao, DefaultResourceTypesDao | **THIS SESSION** |

## Goal: Free collectd from opennms-config — 13/13 daemons free

Three classes need to move from `opennms-config` to a new `features/collection/config` module:

### Classes to Move

1. **DataCollectionConfigFactory** (~112 lines) — static singleton holder
   - Location: `opennms-config/src/main/java/org/opennms/netmgt/config/DataCollectionConfigFactory.java`
   - Pattern: Same as other config factories — `init()`, `getInstance()`, `setInstance()`
   - Daemon-boot already calls `setInstance()` with a Spring-constructed DAO

2. **DefaultDataCollectionConfigDao** (~724 lines) — complex XML loading
   - Location: `opennms-config/src/main/java/org/opennms/netmgt/config/DefaultDataCollectionConfigDao.java`
   - Dependencies: JAXB (JaxbUtils), BeanUtils, FileReloadContainer — all legacy patterns
   - Needs Jackson XmlMapper replacement following the established pattern
   - Loads `etc/datacollection-config.xml` plus external group files from `etc/datacollection/*.xml`

3. **DataCollectionConfigParser** (~326 lines) — parses `etc/datacollection/*.xml`
   - Location: `opennms-config/src/main/java/org/opennms/netmgt/config/DataCollectionConfigParser.java`
   - Called by DefaultDataCollectionConfigDao to load external group definitions
   - Uses Spring Resource loading

4. **DefaultResourceTypesDao** (~100 lines) — depends on DataCollectionConfigFactory.getInstance()
   - Location: `opennms-config/src/main/java/org/opennms/netmgt/config/DefaultResourceTypesDao.java`

### Dependencies

**DefaultDataCollectionConfigDao imports (key ones):**
- `org.opennms.core.utils.ConfigFileConstants` — core/lib ✓
- `org.opennms.core.xml.JaxbUtils` — needs Jackson XmlMapper replacement
- `org.opennms.netmgt.config.api.DataCollectionConfigDao` — interface in config-api ✓
- `org.opennms.netmgt.config.datacollection.*` — model in config-model ✓
- `org.opennms.core.spring.BeanUtils` — legacy, remove
- `org.opennms.core.spring.FileReloadContainer` — legacy, remove

### Approach

Follow the **PollerConfigFactory pattern** (PR #99):

1. Create `features/collection/config` module
2. Move DataCollectionConfigFactory, DefaultDataCollectionConfigDao, DataCollectionConfigParser, DefaultResourceTypesDao there
3. Replace JAXB (JaxbUtils) with Jackson XmlMapper for XML loading
4. Remove BeanUtils/FileReloadContainer — use constructor injection instead
5. Remove legacy `init()` methods (Karaf is dead)
6. Add `features/collection/config` as a dependency of `opennms-config` (transitive re-export for backward compat)
7. Update daemon-boot-collectd POM: replace `opennms-config` with `features/collection/config`
8. Verify opennms-config can be fully removed from daemon-boot-collectd

### Daemon-boot-collectd Current opennms-config Usage

From `CollectdDaemonConfiguration.java`:
```java
import org.opennms.netmgt.config.DataCollectionConfigFactory;
import org.opennms.netmgt.config.DefaultDataCollectionConfigDao;
import org.opennms.netmgt.config.DefaultResourceTypesDao;
```

From `CollectdJpaConfiguration.java`:
```java
import org.opennms.netmgt.filter.FilterDaoFactory;  // ← already in core/filter
```

### Existing Interface

`DataCollectionConfigDao` is already in `opennms-config-api`:
```java
org.opennms.netmgt.config.api.DataCollectionConfigDao
```

### Existing Model Classes

Data collection model is in `opennms-config-model`:
```
org.opennms.netmgt.config.datacollection.DatacollectionConfig
org.opennms.netmgt.config.datacollection.SnmpCollection
org.opennms.netmgt.config.datacollection.DatacollectionGroup
org.opennms.netmgt.config.datacollection.ResourceType
```

## Recommended Session Order

1. **Create `features/collection/config` module** — POM with dependencies
2. **Move DataCollectionConfigFactory** — strip `init()`, keep `setInstance()/getInstance()`
3. **Move + refactor DefaultDataCollectionConfigDao** — Jackson XmlMapper, constructor injection
4. **Move DataCollectionConfigParser** — Jackson XmlMapper for external group files
5. **Move DefaultResourceTypesDao** — simple, depends on DataCollectionConfigFactory
6. **Add `features/collection/config` as dependency of `opennms-config`** — backward compat
7. **Update daemon-boot-collectd POM** — replace opennms-config with features/collection/config
8. **Verify: Remove opennms-config from daemon-boot-collectd** — should now be fully free
9. **Build all 13 daemon-boot modules** — verify compilation

## After This Session

**All 13 daemons free of opennms-config.** The `opennms-config` module becomes a pure re-export layer for backward compatibility with legacy modules (opennms-dao, opennms-webapp-rest, etc.).

## CI Workflows

PR #100 fixed the CI workflows — both `ci.yml` and `codeql-analysis.yml` now build only the 13 daemon-boot modules with `-am`. Dead Karaf modules (`core/test-api/karaf`, `features/minion/repository`, `features/minion/core/repository`) were removed from the Maven reactor. CI should pass cleanly.

## Established Patterns

All config factory decoupling follows these proven patterns:
- **Simple factories** (Trapd, Syslogd, DatabaseSchema): Jackson XmlMapper in daemon-boot, new Default* impl in config-api
- **Complex factories** (Discovery, Poller): Move entire factory to feature module, keep static init()/getInstance() pattern
- **Constructor-injected factories** (Collectd, Poller): FilterDao/EntityScopeProvider via DI, no static singletons
- **Infrastructure classes** (JdbcFilterDao, SnmpPeerFactory): Move to core/* modules, keep setInstance() bridge
- **Jackson XmlMapper setup** (all):
  ```java
  private static final XmlMapper XML_MAPPER;
  static {
      XML_MAPPER = XmlMapper.builder()
              .defaultUseWrapper(false)
              .build();
      XML_MAPPER.registerModule(new JaxbAnnotationModule());
      XML_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }
  ```

## Pre-existing Issues (ignore)

- `core/test-api/karaf` removed from reactor (PR #100) — directory still exists, just not built
- The `opennms` assembly module has a pre-existing maven-assembly-plugin failure — does not affect daemon-boot builds
- `daemon-boot-eventtranslator/pom.xml` has a duplicate dependency warning (`org.opennms.core.model-api`) — cosmetic
