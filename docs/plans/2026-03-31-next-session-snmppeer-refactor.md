# Next Session: SnmpPeerFactory Refactoring — Karaf Removal Phase 4

> Copy everything below the line into the next Claude Code conversation.

---

## Context

Design spec: `docs/superpowers/specs/2026-03-30-karaf-removal-design.md`
Phase 4 progress:
- Trapd: PR #91, MERGED — established Jackson XmlMapper pattern
- Syslogd: PR #93, MERGED — interface to config-api
- Discovery: PR #94 + PR #96, MERGED — Jackson loading + DiscoveryConfigFactory moved to features/discovery
- EventTranslator: PR #95, MERGED — interface to config-api, model constructor, JMX deleted

## Current State: Daemons vs opennms-config

| Daemon | opennms-config dep? | Blocking factory | Status |
|--------|---------------------|------------------|--------|
| alarmd | Exclusion only | — | **FREE** |
| bsmd | Exclusion only | — | **FREE** |
| telemetryd | Exclusion only | — | **FREE** |
| trapd | Exclusion only | — | **FREE (PR #91)** |
| syslogd | None | — | **FREE (PR #93)** |
| minion | None | — | **FREE** |
| minion-common | None | — | **FREE** |
| discovery | None | — | **FREE (PR #94 + #96)** |
| eventtranslator | Explicit (factory) | EventTranslatorConfigFactory | **PARTIAL (PR #95)** — interface decoupled, factory in boot |
| pollerd | Direct dep | `SnmpPeerFactory`, `PollerConfigFactory` | BLOCKED on SnmpPeerFactory |
| collectd | Direct dep | `SnmpPeerFactory`, `CollectdConfigFactory` + 3 more | BLOCKED on SnmpPeerFactory |
| enlinkd | Direct dep | `SnmpPeerFactory` only | BLOCKED on SnmpPeerFactory |
| perspectivepollerd | Direct dep | `SnmpPeerFactory`, `PollerConfigFactory` | BLOCKED on SnmpPeerFactory |
| provisiond | Exclusion only | `SnmpPeerFactory` (via SnmpPeerFactoryInitializer) | BLOCKED on SnmpPeerFactory |

## Goal: SnmpPeerFactory Refactoring (unlocks 5 daemons)

**Branch:** `refactor/snmppeer-spring-boot`
**File:** `opennms-config/src/main/java/org/opennms/netmgt/config/SnmpPeerFactory.java` (794 lines)

### Audit Findings (from 2026-03-31 session)

5 hidden dependencies:

1. **`BeanUtils.getBean("daoContext", "entityScopeProvider", EntityScopeProvider.class)`** (line 283)
   - MATE metadata interpolation of SNMP credentials
   - **Fix:** Wire as constructor parameter

2. **`BeanUtils.getBean("daoContext", "textEncryptor", TextEncryptor.class)`** (line 776)
   - SNMP credential encryption (optional, system property gated)
   - **Fix:** Wire as constructor parameter (nullable)

3. **`FileReloadContainer<SnmpConfig>`** (lines 115, 143)
   - Hot-reload snmp-config.xml on filesystem change
   - **Fix:** Remove — container deployments use config overlays, restart to reload

4. **`JaxbUtils.unmarshal(SnmpConfig.class, resource)`** (lines 133, 140)
   - **Fix:** Replace with `XmlMapper.readValue()`

5. **Static singleton `getInstance()`/`setInstance()`** — 33 callers
   - **Fix:** Keep `setInstance()` bridge — boot creates instance, feature modules use `getInstance()`

### Recommended Approach

1. **Do NOT rewrite** SnmpConfigManager or IP range matching logic (500+ lines, battle-tested)
2. Add new constructor: `SnmpPeerFactory(SnmpConfig config, TextEncryptor textEncryptor, EntityScopeProvider entityScopeProvider)`
3. Replace `JaxbUtils.unmarshal()` with `XmlMapper.readValue()` in existing `Resource` constructor
4. Replace `FileReloadContainer` with direct `m_config` storage (no hot-reload)
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

### Static getInstance() callers in feature modules

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

### After SnmpPeerFactory

Once SnmpPeerFactory is decoupled:
- **Enlinkd is automatically free** — its only opennms-config dep is SnmpPeerFactory
- Enables PollerConfig + CollectdConfig work (next priorities)
- Gets closer to deleting opennms-config entirely

## Pre-existing Issues

- `integration-tests/config` has a pre-existing test compilation failure (missing `opennms-provision-persistence`) — ignore
- The `opennms` assembly module has a pre-existing maven-assembly-plugin failure (`unclosed entries`) — does not affect daemon-boot builds
