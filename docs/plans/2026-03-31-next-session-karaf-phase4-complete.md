# Next Session: Karaf Removal Phase 4 — Complete opennms-config Elimination

> Copy everything below the line into the next Claude Code conversation.

---

## Context

Design spec: `docs/superpowers/specs/2026-03-30-karaf-removal-design.md`
Phase 1 (delete dead Karaf infrastructure) — PR #84, MERGED.
Phase 2 (convert bundle to jar packaging) — PR #89, MERGED.
Phase 3 (move EventBuilder et al out of opennms-model) — PR #90, MERGED.
Phase 4 (trapd: first opennms-config elimination) — PR #91, MERGED.

## Goal

Replace all remaining `opennms-config` factory singletons with Jackson XmlMapper `@Bean` methods in daemon-boot modules, then delete `opennms-config/` entirely.

## Established Pattern (from PR #91, trapd)

For each config factory:
1. **Move the config interface** from `opennms-config` to `opennms-config-api` (same Java package — no import changes)
2. **Load XML** with `Jackson XmlMapper` + `JaxbAnnotationModule` in the `@Bean` method
3. **Pass deserialized model** to existing bean implementation (or create one if needed)
4. **Remove opennms-config dependency** from daemon-boot POM
5. **Exclude opennms-config** from transitive feature module dependencies

Jackson XmlMapper setup (reuse from DaemonEventConfDao):
```java
private static final XmlMapper XML_MAPPER;
static {
    XML_MAPPER = new XmlMapper();
    XML_MAPPER.registerModule(new JaxbAnnotationModule());
    XML_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
}
```

## Priority Order

### Priority 1: Syslogd (EASY — bean implementation exists)

**Branch:** `refactor/syslogd-config-spring-boot`
**File:** `core/daemon-boot-syslogd/src/main/java/org/opennms/netmgt/syslogd/boot/SyslogdConfiguration.java`

**Current pattern:**
```java
import org.opennms.netmgt.config.SyslogdConfigFactory;
import org.opennms.netmgt.config.SyslogdConfig;

@Bean
public SyslogdConfig syslogdConfig() throws Exception {
    return new SyslogdConfigFactory();
}
```

**What exists:** `SyslogConfigBean` in `features/events/syslog/src/main/java/org/opennms/netmgt/syslogd/SyslogConfigBean.java` — fully implements `SyslogdConfig`

**Steps:**
1. Move `SyslogdConfig` interface from `opennms-config` to `opennms-config-api` (same package `org.opennms.netmgt.config`)
2. Load `syslogd-configuration.xml` with XmlMapper, construct `SyslogConfigBean(SyslogdConfiguration)`
3. Remove `opennms-config` dep from daemon-boot-syslogd POM
4. Config file: `${opennms.home}/etc/syslogd-configuration.xml`
5. JAXB model: `org.opennms.netmgt.config.syslogd.SyslogdConfiguration` (in opennms-config-jaxb)

### Priority 2: Discovery (MEDIUM — no bean impl, but simple factory)

**Branch:** `refactor/discovery-config-spring-boot`
**File:** `core/daemon-boot-discovery/src/main/java/org/opennms/netmgt/discovery/boot/DiscoveryBootConfiguration.java`

**Current pattern:**
```java
import org.opennms.netmgt.config.DiscoveryConfigFactory;

@Bean
public DiscoveryConfigFactory discoveryConfigFactory() throws Exception {
    return new DiscoveryConfigFactory();
}
```

**Interface:** `DiscoveryConfigurationFactory` already in `opennms-config-api`!
**Factory:** `DiscoveryConfigFactory` in opennms-config extends `DiscoveryConfigurationFactory`

**Steps:**
1. Read `DiscoveryConfigFactory` to understand what it does beyond XML loading
2. Create a lightweight Jackson-based replacement in daemon-boot-discovery that implements `DiscoveryConfigurationFactory`
3. Or check if the factory is simple enough to just read the XML model and expose it
4. Config file: `${opennms.home}/etc/discovery-configuration.xml`
5. JAXB model: `org.opennms.netmgt.config.discovery.DiscoveryConfiguration` (in opennms-config-model)

### Priority 3: EventTranslator (MEDIUM — no POM dep but uses factory)

**Branch:** `refactor/eventtranslator-config-spring-boot`
**File:** `core/daemon-boot-eventtranslator/src/main/java/org/opennms/netmgt/translator/boot/EventTranslatorBootConfiguration.java`

**Current pattern:**
```java
import org.opennms.netmgt.config.EventTranslatorConfigFactory;

@Bean
public EventTranslatorConfigFactory eventTranslatorConfig(DataSource dataSource) throws Exception {
    DataSourceFactory.setInstance(dataSource);
    EventTranslatorConfigFactory.init();
    return (EventTranslatorConfigFactory) EventTranslatorConfigFactory.getInstance();
}
```

**Note:** This daemon does NOT have opennms-config in its POM — it gets it transitively. The `EventTranslator` class takes the concrete `EventTranslatorConfigFactory` type (not an interface). Need to check if it can be changed to use an interface.

**Interface:** `EventTranslatorConfig` in opennms-config
**Config file:** `${opennms.home}/etc/translator-configuration.xml`
**Complication:** Uses `DataSourceFactory.setInstance(dataSource)` before init — the factory queries the DB for filter rules

### Priority 4: SnmpPeerFactory (HIGH VALUE — unlocks 5 daemons, but COMPLEX)

**Used by:** pollerd, collectd, enlinkd, perspectivepollerd, provisiond

**Current pattern (in all 5 daemons):**
```java
import org.opennms.netmgt.config.SnmpPeerFactory;

@Bean
public SnmpPeerFactory snmpPeerFactory() throws IOException {
    SnmpPeerFactory.init();
    return SnmpPeerFactory.getInstance();
}
```

**Interface:** `SnmpAgentConfigFactory` already in `opennms-config-api`
**Factory:** `SnmpPeerFactory` in opennms-config (800+ lines, complex)

**Key complexities:**
- `FileReloadContainer` for hot-reload of snmp-config.xml
- Lazy `TextEncryptor` retrieval via `BeanUtils.getBean()`
- Lazy `EntityScopeProvider` retrieval via `BeanUtils.getBean()`
- `SnmpConfigManager` for IP range matching (500+ lines of networking logic)
- Feature modules like `SnmpMonitorStrategy` call `SnmpPeerFactory.getInstance()` directly (static singleton pattern)

**Recommended approach:**
1. Do NOT rewrite the 800-line config resolution logic
2. Move `SnmpPeerFactory` to a new lightweight module (e.g., `core/snmp/snmp-config-boot`) that depends only on `opennms-config-api`, `opennms-config-model`, `core/snmp/api`
3. Replace `JaxbUtils.unmarshal()` calls with `XmlMapper.readValue()`
4. Wire `TextEncryptor` and `EntityScopeProvider` as constructor params instead of BeanUtils
5. Keep `SnmpPeerFactory.setInstance()` bridge so feature modules' static calls still work
6. Config file: `${opennms.home}/etc/snmp-config.xml`

### Priority 5: PollerConfig (COMPLEX — unlocks pollerd + perspectivepollerd)

**Interface:** `PollerConfig` in opennms-config (LARGE interface — 30+ methods)
**Factory:** `PollerConfigFactory` extends `PollerConfigManager` (abstract class with complex package/filter logic)

**Steps:**
1. Move `PollerConfig` interface to `opennms-config-api`
2. Evaluate whether to move `PollerConfigManager`/`PollerConfigFactory` to a new module or rewrite
3. `PollerConfigFactory.init()` calls `FilterDaoFactory.getInstance()` — dependency on filter DAO initialization order
4. Config file: `${opennms.home}/etc/poller-configuration.xml`

### Priority 6: Collectd configs (COMPLEX — 5 factories)

Requires: `CollectdConfigFactory`, `DataCollectionConfigFactory`, `DefaultDataCollectionConfigDao`, `DefaultResourceTypesDao`, `SnmpPeerFactory`

**Note:** `CollectdConfigFactory` interface already exists in `opennms-config-api`! The concrete class in opennms-config extends it.

### Priority 7: Enlinkd config

**Interface:** `EnhancedLinkdConfig` in `features/enlinkd/config` (NOT in opennms-config!)
**Factory:** `EnhancedLinkdConfigFactory` in `features/enlinkd/config` (NOT in opennms-config!)
**Note:** Enlinkd's opennms-config dependency is ONLY for `SnmpPeerFactory`. Once SnmpPeerFactory moves out, enlinkd is free of opennms-config.

### Priority 8: Cleanup

- Delete `EventConfInitializer` in `core/event-forwarder-kafka` — dead code (replaced by `KafkaEventTransportConfiguration`)
- Remove `opennms-config` from root POM `<modules>` (only after ALL consumers migrated)
- Delete `opennms-config/` directory

## Daemons Already Free of opennms-config (7 of 14)

| Daemon | Status |
|--------|--------|
| alarmd | FREE — no direct config factory usage |
| bsmd | FREE — no direct config factory usage |
| telemetryd | FREE — no direct config factory usage |
| **trapd** | **FREE — PR #91 MERGED** |
| minion | FREE — different architecture |
| minion-common | FREE — different architecture |

*Remaining: syslogd, discovery, eventtranslator, pollerd, collectd, enlinkd, perspectivepollerd (provisiond needs verification)*

## Recommended Session Structure

For a single session, tackle Priorities 1-2 (syslogd + discovery) as separate PRs. These are the lowest-risk, highest-confidence wins. Save SnmpPeerFactory (Priority 4) for a dedicated session due to its complexity.

Each config replacement is an independent PR:
```bash
# PR 1: Syslogd
gh pr create --repo pbrane/delta-v --base develop \
  --title "refactor: replace SyslogdConfigFactory with Jackson XmlMapper — Phase 4"

# PR 2: Discovery
gh pr create --repo pbrane/delta-v --base develop \
  --title "refactor: replace DiscoveryConfigFactory with Jackson XmlMapper — Phase 4"
```

## Pre-existing Issues

- `integration-tests/config` has a pre-existing test compilation failure (missing `opennms-provision-persistence`) — ignore
- EventBuilder is now in `features/events/api` (PR #90) — same package, no import changes needed
