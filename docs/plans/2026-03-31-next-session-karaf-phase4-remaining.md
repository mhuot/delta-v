# Next Session: Karaf Removal Phase 4 — Remaining opennms-config Elimination

> Copy everything below the line into the next Claude Code conversation.

---

## Context

Design spec: `docs/superpowers/specs/2026-03-30-karaf-removal-design.md`
Phase 1 (delete dead Karaf infrastructure) — PR #84, MERGED.
Phase 2 (convert bundle to jar packaging) — PR #89, MERGED.
Phase 3 (move EventBuilder et al out of opennms-model) — PR #90, MERGED.
Phase 4 progress so far:
- Trapd: PR #91, MERGED — established Jackson XmlMapper + SyslogdConfigAdapter pattern.
- Syslogd: PR #93, MERGED — SyslogdConfig interface moved to opennms-config-api, opennms-config fully eliminated.
- Discovery: PR #94, MERGED — Jackson loading + interface widening, but opennms-config retained (feature module coupling).

## Current State: Daemons vs opennms-config

| Daemon | opennms-config dep? | Blocking factory | Status |
|--------|---------------------|------------------|--------|
| alarmd | Exclusion only | — | **FREE** |
| bsmd | Exclusion only | — | **FREE** |
| telemetryd | Exclusion only | — | **FREE** |
| trapd | Exclusion only | — | **FREE (PR #91)** |
| syslogd | Exclusion only | — | **FREE (PR #93)** |
| minion | None | — | **FREE** |
| minion-common | None | — | **FREE** |
| discovery | Direct dep | `DiscoveryConfigFactory` | PARTIAL (PR #94) — feature module creates `new DiscoveryConfigFactory(config)` in RangeChunker + DiscoveryTaskExecutorImpl |
| eventtranslator | Transitive | `EventTranslatorConfigFactory` | **NEXT** |
| pollerd | Direct dep | `SnmpPeerFactory`, `PollerConfigFactory` | BLOCKED on SnmpPeerFactory |
| collectd | Direct dep | `SnmpPeerFactory`, `CollectdConfigFactory` + 3 more | BLOCKED on SnmpPeerFactory |
| enlinkd | Direct dep | `SnmpPeerFactory` only | BLOCKED on SnmpPeerFactory |
| perspectivepollerd | Direct dep | `SnmpPeerFactory`, `PollerConfigFactory` | BLOCKED on SnmpPeerFactory |
| provisiond | Exclusion only | `SnmpPeerFactory` (via SnmpPeerFactoryInitializer) | BLOCKED on SnmpPeerFactory |

## Goal

Continue eliminating `opennms-config` from daemon-boot modules. Priorities 3-4 are ready to execute.

## Established Pattern (from PRs #91, #93, #94)

For each config factory:
1. **Move the config interface** from `opennms-config` to `opennms-config-api` (same Java package — no import changes)
2. **Load XML** with `Jackson XmlMapper` + `JaxbAnnotationModule` in the `@Bean` method
3. **Pass deserialized model** to existing bean implementation (or create one if needed)
4. **Remove opennms-config dependency** from daemon-boot POM
5. **Exclude opennms-config** from transitive feature module dependencies

Jackson XmlMapper setup (reuse in every boot @Configuration):
```java
private static final XmlMapper XML_MAPPER;
static {
    XML_MAPPER = new XmlMapper();
    XML_MAPPER.registerModule(new JaxbAnnotationModule());
    XML_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
}
```

## Priority 3: EventTranslator (MEDIUM)

**Branch:** `refactor/eventtranslator-config-spring-boot`
**File:** `core/daemon-boot-eventtranslator/src/main/java/org/opennms/netmgt/translator/boot/EventTranslatorBootConfiguration.java`

**Current pattern:**
```java
@Bean
public EventTranslatorConfigFactory eventTranslatorConfig(DataSource dataSource) throws Exception {
    DataSourceFactory.setInstance(dataSource);
    EventTranslatorConfigFactory.init();
    return (EventTranslatorConfigFactory) EventTranslatorConfigFactory.getInstance();
}

@Bean
public EventTranslator eventTranslator(EventIpcManager eventIpcManager,
        EventTranslatorConfigFactory config, DataSource dataSource) {
    var translator = new EventTranslator();
    translator.setEventManager(eventIpcManager);
    translator.setConfig(config);
    translator.setDataSource(dataSource);
    return translator;
}
```

**Key complications:**
- `EventTranslator.setConfig()` takes the **concrete** `EventTranslatorConfigFactory` type, not an interface
- `EventTranslatorConfigFactory.init()` calls `DataSourceFactory.setInstance(dataSource)` — the factory queries the DB for filter rules during initialization
- `EventTranslatorConfig` interface exists in **opennms-config** (not opennms-config-api) — needs to be moved
- The factory reads `translator-configuration.xml` via JAXB and also does SQL-based filter evaluation

**Recommended approach:**
1. Read `EventTranslatorConfigFactory` to understand its full API and DB interactions
2. Read `EventTranslator` to understand which methods it actually calls
3. Move `EventTranslatorConfig` interface to `opennms-config-api`
4. Widen `EventTranslator.setConfig()` to accept the interface type
5. Create a lightweight Jackson-based replacement or use the model-arg constructor pattern
6. Config file: `${opennms.home}/etc/translator-configuration.xml`
7. JAXB model: check `opennms-config-model` for `EventTranslatorConfiguration`

**Note:** EventTranslator does NOT have opennms-config as a direct POM dependency — it gets it transitively. So the value is in decoupling the concrete type, not removing a POM entry.

## Priority 4: SnmpPeerFactory (HIGH VALUE — unlocks 5 daemons, COMPLEX)

**Used by:** pollerd, collectd, enlinkd, perspectivepollerd, provisiond (5 daemons)

### Audit Findings (from 2026-03-31 session)

SnmpPeerFactory (794 lines in `opennms-config/src/main/java/org/opennms/netmgt/config/SnmpPeerFactory.java`) has 5 hidden dependencies:

1. **`BeanUtils.getBean("daoContext", "entityScopeProvider", EntityScopeProvider.class)`** (line 283)
   - Lazy lookup for MATE metadata interpolation of SNMP credentials
   - Used in `getSecureCredentialsScope()`, called by every `getAgentConfig()` variant
   - **Fix:** Wire as constructor parameter

2. **`BeanUtils.getBean("daoContext", "textEncryptor", TextEncryptor.class)`** (line 776)
   - Lazy lookup for SNMP credential encryption/decryption
   - Only activated when system property `org.opennms.snmp.encryption.enabled=true`
   - Called in `initializeTextEncryptor()` → `encryptSnmpConfig()` → `init()`
   - **Fix:** Wire as constructor parameter (nullable — encryption is optional)

3. **`FileReloadContainer<SnmpConfig>`** (lines 115, 143)
   - Watches snmp-config.xml for filesystem changes, hot-reloads on modification
   - In containerized deployment, config files are immutable overlays — mechanism is wasteful
   - **Fix:** Replace with a no-op or remove entirely; config changes = container restart

4. **`JaxbUtils.unmarshal(SnmpConfig.class, resource)`** (lines 133, 140)
   - JAXB in both main constructor and FileReloadContainer callback
   - **Fix:** Replace with `XmlMapper.readValue()`

5. **Static singleton pattern** — `getInstance()` / `setInstance()` used by 33 callers
   - 5 daemon-boot modules call `SnmpPeerFactory.init()` + `getInstance()`
   - Feature modules call `SnmpPeerFactory.getInstance()` directly: `SnmpMonitorStrategy`, `NodeCollector`, `DefaultSnmpCollectionAgent`, `DefaultSnmpCollectionAgentService`, `Collectd`, `ScanManager`, `DiskUsageDetector`, `SnmpTrapConfig`
   - **Fix:** Keep `setInstance()` bridge — boot module creates instance with Jackson, calls `setInstance()`, feature modules continue using `getInstance()`

### Recommended approach for SnmpPeerFactory:
1. **Do NOT rewrite** SnmpConfigManager or IP range matching logic (500+ lines, battle-tested)
2. Add a new constructor: `SnmpPeerFactory(SnmpConfig config, TextEncryptor textEncryptor, EntityScopeProvider entityScopeProvider)` — eliminates both BeanUtils lookups
3. Replace `JaxbUtils.unmarshal()` with `XmlMapper.readValue()` in the existing `Resource` constructor
4. Replace `FileReloadContainer` with direct `m_config` storage (no hot-reload in containers)
5. In each daemon-boot `@Configuration`:
   ```java
   @Bean
   public SnmpAgentConfigFactory snmpPeerFactory(@Value("${opennms.home}") String home,
           @Autowired(required = false) TextEncryptor textEncryptor,
           EntityScopeProvider entityScopeProvider) throws IOException {
       var configFile = new File(home, "etc/snmp-config.xml");
       var config = XML_MAPPER.readValue(configFile, SnmpConfig.class);
       var factory = new SnmpPeerFactory(config, textEncryptor, entityScopeProvider);
       SnmpPeerFactory.setInstance(factory); // bridge for static callers
       return factory;
   }
   ```
6. Config file: `${opennms.home}/etc/snmp-config.xml`
7. JAXB model: `org.opennms.netmgt.config.snmp.SnmpConfig` (in opennms-config-jaxb)

### SnmpPeerFactory callers by daemon (static getInstance() usage in feature modules):

| Feature module | Caller class | Method |
|---------------|--------------|--------|
| features/poller/monitors/core | SnmpMonitorStrategy | getInstance() |
| features/collection/impl | DefaultSnmpCollectionAgent | getInstance() |
| features/collection/impl | DefaultSnmpCollectionAgentService | getInstance() |
| features/collection/impl | Collectd | getInstance() |
| features/enlinkd/common | NodeCollector | getInstance() |
| opennms-provision/opennms-provisiond | ScanManager | getInstance() |
| opennms-provision/opennms-detector-simple | DiskUsageDetector | getInstance() |
| opennms-alarms/snmptrap-northbounder | SnmpTrapConfig | getInstance() |

These all continue working as long as `setInstance()` is called during boot.

## Priority 5: Discovery full decoupling (FOLLOW-UP from PR #94)

PR #94 widened `Discovery` daemon to `DiscoveryConfigurationFactory` (interface) and added Jackson XmlMapper loading. But `opennms-config` is still on the classpath because `RangeChunker` (line 83) and `DiscoveryTaskExecutorImpl` (line 223) create `new DiscoveryConfigFactory(config)` internally.

**To fully decouple:**
1. Move `DiscoveryConfigFactory` from `opennms-config` to `features/discovery` (same package — no import changes)
2. The feature module already depends on the needed transitive deps (`core/xml` for JaxbUtils, `core/lib` for ConfigFileConstants)
3. Remove `opennms-config` from daemon-boot-discovery POM
4. This is low-risk since the class just moves modules

## Priorities 5-8 (from original plan — lower priority)

### Priority 5 (was 5): PollerConfig
- `PollerConfig` interface in opennms-config (30+ methods) — move to opennms-config-api
- `PollerConfigFactory` extends `PollerConfigManager` (complex package/filter logic)
- `FilterDaoFactory.getInstance()` dependency during init
- Unlocks pollerd + perspectivepollerd (along with SnmpPeerFactory)

### Priority 6 (was 6): Collectd configs (5 factories)
- `CollectdConfigFactory`, `DataCollectionConfigFactory`, `DefaultDataCollectionConfigDao`, `DefaultResourceTypesDao`, `SnmpPeerFactory`
- `CollectdConfigFactory` interface already in opennms-config-api

### Priority 7 (was 7): Enlinkd config
- Enlinkd's config is NOT in opennms-config — it's in `features/enlinkd/config`
- Enlinkd's opennms-config dep is **ONLY for SnmpPeerFactory**
- Once SnmpPeerFactory moves out → enlinkd is automatically free

### Priority 8: Final cleanup
- Delete `EventConfInitializer` in `core/event-forwarder-kafka` — dead code
- Remove `opennms-config` from root POM `<modules>`
- Delete `opennms-config/` directory

## Recommended Session Structure

**Session A (quick wins):**
- Priority 3: EventTranslator — widen to interface, Jackson loading
- Priority 5 (Discovery follow-up): Move DiscoveryConfigFactory to features/discovery

**Session B (dedicated — SnmpPeerFactory):**
- Priority 4: SnmpPeerFactory refactoring — new constructor, eliminate BeanUtils, replace JAXB
- This unlocks enlinkd immediately (Priority 7 becomes free)
- Enables pollerd/collectd/perspectivepollerd work

**Session C (after SnmpPeerFactory lands):**
- Priorities 5-6: PollerConfig + CollectdConfig factories
- Priority 8: Final cleanup — delete opennms-config/

## Pre-existing Issues

- `integration-tests/config` has a pre-existing test compilation failure (missing `opennms-provision-persistence`) — ignore
- EventBuilder is now in `features/events/api` (PR #90) — same package, no import changes needed
- The `opennms` assembly module has a pre-existing maven-assembly-plugin failure (`unclosed entries`) — does not affect daemon-boot builds
