# Next Session: Remaining Config Factory Decoupling — Karaf Removal Phase 4

> Copy everything below the line into the next Claude Code conversation.

---

## Context

Design spec: `docs/superpowers/specs/2026-03-30-karaf-removal-design.md`
Phase 4 progress:
- Trapd: PR #91, MERGED — established Jackson XmlMapper pattern
- Syslogd: PR #93, MERGED — interface to config-api
- Discovery: PR #96, MERGED — DiscoveryConfigFactory moved to features/discovery
- EventTranslator: PR #95, MERGED — interface to config-api, Jackson XmlMapper
- **SnmpPeerFactory: PR #97, MERGED** — new constructor, Jackson XmlMapper, BeanUtils/FileReloadContainer eliminated, 5 daemon-boot modules updated

## Current State: Daemons vs opennms-config

| Daemon | opennms-config dep? | Remaining factories in opennms-config | Difficulty |
|--------|---------------------|---------------------------------------|------------|
| alarmd | None | — | **FREE** |
| bsmd | None | — | **FREE** |
| telemetryd | None | — | **FREE** |
| trapd | None | — | **FREE** (PR #91) |
| syslogd | None | — | **FREE** (PR #93) |
| minion | None | — | **FREE** |
| discovery | None | — | **FREE** (PR #96) |
| eventtranslator | Exclusion only | — | **FREE** (PR #95) |
| **enlinkd** | POM dep exists | SnmpPeerFactory (setInstance bridge only) | **EASY** — verify removable |
| **provisiond** | No direct POM dep | SnmpPeerFactory (setInstance bridge), SnmpAssetAdapterConfigFactory (in integrations/, not opennms-config) | **EASY** — verify already free |
| **pollerd** | Direct dep | `PollerConfigFactory`, `DatabaseSchemaConfigFactory` | **MODERATE** |
| **perspectivepollerd** | Direct dep | `PollerConfigFactory`, `DatabaseSchemaConfigFactory` | **MODERATE** |
| **collectd** | Direct dep | `CollectdConfigFactory`, `DataCollectionConfigFactory`, `DefaultDataCollectionConfigDao`, `DefaultResourceTypesDao`, `DatabaseSchemaConfigFactory` | **COMPLEX** |

## Tier 1 — Verify Already Free (Enlinkd + Provisiond)

### Enlinkd

Enlinkd's `EnhancedLinkdConfig` interface and `EnhancedLinkdConfigFactory` live in `features/enlinkd/config` — NOT opennms-config — despite sharing the `org.opennms.netmgt.config` package name. The only opennms-config class imported is `SnmpPeerFactory` (for `new SnmpPeerFactory(...)` and `setInstance()`).

**Action:** Check if opennms-config can be removed from `daemon-boot-enlinkd/pom.xml`. SnmpPeerFactory comes from opennms-config, so the dep may still be needed for the class reference. If so, verify the exclusions are sufficient to keep it lightweight.

### Provisiond

The audit found NO direct `opennms-config` Maven dependency in `daemon-boot-provisiond/pom.xml`. `SnmpAssetAdapterConfigFactory` is in `integrations/opennms-snmp-asset-provisioning-adapter`, not opennms-config. `ProvisiondConfiguration` and `RequisitionDef` are in `opennms-config-jaxb`.

**Action:** Verify provisiond compiles without opennms-config. It likely gets SnmpPeerFactory transitively. Document that provisiond is free.

## Tier 2 — PollerConfigFactory + DatabaseSchemaConfigFactory (unlocks Pollerd + PerspectivePollerd)

### PollerConfigFactory

**Location:** `opennms-config/src/main/java/org/opennms/netmgt/config/PollerConfigFactory.java`
- Extends `PollerConfigManager` (also in opennms-config)
- `PollerConfig` interface is in opennms-config (NOT config-api) — at `opennms-config/src/main/java/org/opennms/netmgt/config/PollerConfig.java`
- Used in daemon-boot via: `PollerConfigFactory.init(); return PollerConfigFactory.getInstance();`
- `init()` calls `FilterDaoFactory.getInstance()` internally — depends on filter DAO being initialized first

**Approach options:**
1. **Move `PollerConfig` interface to config-api** (like we did for EventTranslator). Then daemon-boot depends on config-api interface only, and creates the factory bean using Jackson XmlMapper.
2. **Replace with Jackson XmlMapper** loading in daemon-boot, similar to SnmpPeerFactory pattern. Create a lightweight adapter that implements `PollerConfig` from the parsed XML model.

**Complication:** PollerConfig is a large interface (examine it first). PollerConfigFactory/PollerConfigManager contain complex filter rule validation logic. The Jackson XmlMapper approach may require keeping the PollerConfigManager logic.

### DatabaseSchemaConfigFactory

**Location:** `opennms-config/src/main/java/org/opennms/netmgt/config/DatabaseSchemaConfigFactory.java`
- Implements `DatabaseSchemaConfig` interface (in config-api at `opennms-config-api/src/main/java/org/opennms/netmgt/config/api/DatabaseSchemaConfig.java`)
- Used in daemon-boot-pollerd, daemon-boot-perspectivepollerd, and daemon-boot-collectd (all in their JPA configuration classes)
- Static `init()/getInstance()` pattern — reads `database-schema.xml`
- Used by `JdbcFilterDao` for database table metadata

**Approach:** Interface already in config-api. Replace `DatabaseSchemaConfigFactory.init()/getInstance()` with Jackson XmlMapper loading + `setInstance()` bridge (same pattern as SnmpPeerFactory).

### Impact

Decoupling PollerConfigFactory + DatabaseSchemaConfigFactory frees **both pollerd and perspectivepollerd**.

## Tier 3 — Collectd (5 remaining factories)

### CollectdConfigFactory

**Location:** `opennms-config/src/main/java/org/opennms/netmgt/config/CollectdConfigFactory.java`
- Implements `org.opennms.netmgt.config.api.CollectdConfigFactory` (interface in config-api)
- Instantiated with no-arg constructor in daemon-boot
- Reads `collectd-configuration.xml`

**Approach:** Interface already in config-api. Replace factory with Jackson XmlMapper loading.

### DataCollectionConfigFactory + DefaultDataCollectionConfigDao

- `DataCollectionConfigFactory` — abstract class, static singleton pattern
- `DefaultDataCollectionConfigDao` — reads `datacollection-config.xml` + custom resource types
- `DataCollectionConfigFactory.setInstance(dao)` bridge used

**Approach:** These are complex — examine dependencies before committing to an approach.

### DefaultResourceTypesDao

- Reads resource type definitions from filesystem
- Used by datacollection config loading

### DatabaseSchemaConfigFactory

Same as Tier 2 — shared dependency across pollerd, perspectivepollerd, and collectd.

## Recommended Session Order

1. **Start with Tier 1** — quick wins, verify enlinkd and provisiond are free
2. **Then Tier 2** — PollerConfigFactory + DatabaseSchemaConfigFactory, unlocks 2 daemons
3. **Defer Tier 3** — Collectd has 5 factories, likely needs its own dedicated session

## Established Pattern (from PR #91, #93, #95, #96, #97)

All config factory decoupling follows this pattern:
1. If no interface exists in config-api, move/create one
2. In daemon-boot `@Configuration`:
   ```java
   private static final XmlMapper XML_MAPPER;
   static {
       XML_MAPPER = XmlMapper.builder().defaultUseWrapper(false).build();
       XML_MAPPER.registerModule(new JaxbAnnotationModule());
       XML_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
   }

   @Bean
   public SomeConfig someConfig() throws IOException {
       var configFile = new File(opennmsHome, "etc/some-config.xml");
       var config = XML_MAPPER.readValue(configFile, SomeConfigModel.class);
       // Create factory/adapter, call setInstance() bridge if needed
       return factory;
   }
   ```
3. Keep `setInstance()` bridge for static callers in feature modules
4. Do NOT rewrite complex config resolution logic — focus on eliminating BeanUtils/JaxbUtils/FileReloadContainer

## Pre-existing Issues

- `integration-tests/config` has a pre-existing test compilation failure (missing `opennms-provision-persistence`) — ignore
- The `opennms` assembly module has a pre-existing maven-assembly-plugin failure — does not affect daemon-boot builds
- `daemon-boot-eventtranslator/pom.xml` has a duplicate dependency warning (`org.opennms.core.model-api`) — cosmetic, ignore
