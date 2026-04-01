# Next Session: Karaf Removal Phase 4 — Eliminate opennms-config

> Copy everything below the line into the next Claude Code conversation.

---

## Context

Design spec: `docs/superpowers/specs/2026-03-30-karaf-removal-design.md`
Phase 1 (delete dead Karaf infrastructure) — PR #84, MERGED.
Phase 2 (convert bundle to jar packaging) — PR #89, MERGED.

**This phase can run in parallel with Phase 3** (eliminate opennms-model). They are independent.

## Goal

Replace all `opennms-config` factory singletons used by daemon-boot modules with Spring Boot `@Bean` / `@Configuration` equivalents, then delete `opennms-config/` from the reactor.

## Current State

### Daemons that depend on opennms-config (7 of 14)

| Daemon | Config Classes Used |
|--------|-------------------|
| **pollerd** | `PollerConfigFactory`, `SnmpPeerFactory` |
| **collectd** | `CollectdConfigFactory`, `DataCollectionConfigFactory`, `DefaultDataCollectionConfigDao`, `DefaultResourceTypesDao`, `SnmpPeerFactory` |
| **perspectivepollerd** | `PollerConfigFactory`, `SnmpPeerFactory` |
| **syslogd** | `SyslogdConfigFactory` |
| **trapd** | `TrapdConfig` (via `TrapdConfigBean` — partially replaced) |
| **discovery** | `DiscoveryConfigFactory` |
| **enlinkd** | Implicit via features/enlinkd |

### Daemons already free of opennms-config (7 of 14)
alarmd, bsmd, eventtranslator, minion, minion-common, provisiond, telemetryd

### daemon-common does NOT depend on opennms-config
It only depends on `opennms-config-api` (for `EventConfDao` interface) and `opennms-config-model` (for eventconf JAXB classes).

### Already replaced configs
- **eventconf** — `DaemonEventConfDao` in daemon-common uses Jackson XmlMapper (PR #87)
- **poll-outages** — `OnmsPollOutagesDao` in opennms-config-dao-outages

## Config Strategy (from design spec)

Three patterns based on config complexity:

1. **File-based XML readers** (most configs): Replace JAXB factory singletons with `@Configuration` beans that use Jackson XmlMapper to read the same XML files. Pattern: `@Bean` method reads XML, returns config POJO.

2. **`@Value` properties** (simple tunables): Ports, intervals, thread counts → `application.yml` or env vars.

3. **JDBC** (eventconf): Already handled by `DaemonEventConfDao`.

## Priority Order

### Priority 1: SnmpPeerFactory (unlocks 3 daemons directly, 5 total)

**Location:** `opennms-config/src/main/java/org/opennms/netmgt/config/SnmpPeerFactory.java`

**What it does:**
- Singleton factory that loads `snmp-config.xml` via JAXB
- Implements `SnmpAgentConfigFactory` interface (from `core/snmp/api`)
- Provides `getAgentConfig(InetAddress, location, version)` for SNMP operations
- Supports file-reload (hot-reload from disk)
- Used by: pollerd, collectd, perspectivepollerd (direct); enlinkd, provisiond (transitive)

**Replacement approach:**
1. Create a Spring Boot `@Configuration` class in `core/daemon-common` (or a new `core/snmp/snmp-config-spring` module)
2. Read `snmp-config.xml` using Jackson XmlMapper (not JAXB — per project rule: use Jackson, never JAXB)
3. Implement the same `SnmpAgentConfigFactory` interface
4. Expose as a `@Bean` — no singleton pattern needed; Spring manages the lifecycle
5. The config model classes (`SnmpConfig`, `Definition`, `Range`) are in `opennms-config-model` which stays

**Key dependency chain:**
```
SnmpPeerFactory → JaxbUtils.unmarshal() → SnmpConfig (opennms-config-model)
                → SnmpAgentConfigFactory interface (core/snmp/api)
```
Replace `JaxbUtils.unmarshal()` with `XmlMapper.readValue()` and wire as a Spring bean.

### Priority 2: PollerConfigFactory (unlocks pollerd, perspectivepollerd)

**What it does:**
- Loads `poller-configuration.xml` via JAXB
- Validates filter rules against `FilterDaoFactory`
- Provides package/service/monitor configuration

**Replacement approach:** Same as SnmpPeerFactory — Jackson XmlMapper `@Bean` in daemon-boot-pollerd.

### Priority 3: CollectdConfigFactory + DataCollection configs (unlocks collectd)

**What they do:**
- `CollectdConfigFactory`: loads `collectd-configuration.xml`
- `DataCollectionConfigFactory`: loads `datacollection-config.xml`
- `DefaultDataCollectionConfigDao`: SNMP OID definitions
- `DefaultResourceTypesDao`: resource type definitions from `datacollection/*.xml`

**Replacement approach:** Four `@Bean` methods in daemon-boot-collectd's `@Configuration` class.

### Priority 4: Per-daemon configs (syslogd, discovery, enlinkd)

- `SyslogdConfigFactory`: Jackson XmlMapper reader for `syslogd-configuration.xml`
- `DiscoveryConfigFactory`: Jackson XmlMapper reader for `discovery-configuration.xml`
- `EnhancedLinkdConfig`: Jackson XmlMapper reader for `enlinkd-configuration.xml`

### Priority 5: Cleanup

- Delete `EventConfInitializer` in `core/event-forwarder-kafka` (dead code — replaced by `KafkaEventTransportConfiguration` in daemon-common)
- Remove `opennms-config` from root POM `<modules>`
- Delete `opennms-config/` directory

## Approach per config class

For each factory class:

1. **Check the interface**: Most factories implement an interface (e.g., `SnmpAgentConfigFactory`). The Spring bean implements the same interface.

2. **Identify the XML model class**: The JAXB model class lives in `opennms-config-model` (e.g., `SnmpConfig`, `PollerConfiguration`). These stay — only the factory loading mechanism changes.

3. **Write a `@Configuration` class** in the daemon-boot module:
```java
@Configuration
public class SnmpPeerConfiguration {
    @Bean
    public SnmpAgentConfigFactory snmpAgentConfigFactory(
            @Value("${opennms.home}/etc/snmp-config.xml") String configPath) {
        // Read XML with Jackson XmlMapper
        // Return implementation of SnmpAgentConfigFactory
    }
}
```

4. **Remove opennms-config dependency** from that daemon-boot POM.

5. **Verify**: `make module MODULE=:daemon-boot-xxx` compiles; deploy and health-check the daemon.

## Recommended session structure

Start with SnmpPeerFactory since it's the highest-fanout dependency (5 daemons). Each config replacement is an independent PR:

```bash
# PR 1: SnmpPeerFactory replacement
gh pr create --repo pbrane/delta-v --base develop \
  --title "refactor: replace SnmpPeerFactory with Spring Boot config bean"

# PR 2: PollerConfigFactory replacement
# PR 3: CollectdConfigFactory + data collection configs
# PR 4: SyslogdConfigFactory
# PR 5: DiscoveryConfigFactory
# PR 6: EnhancedLinkdConfig
# PR 7: Delete opennms-config
```

## Branch naming

Use per-config branches:
- `refactor/snmp-peer-factory-spring-boot`
- `refactor/poller-config-spring-boot`
- etc.

## Pre-existing issues to be aware of

- `integration-tests/config` has a pre-existing test compilation failure — ignore it.
- `EventBuilder` ClassNotFoundException at runtime — this is Phase 3 scope (model migration), not Phase 4.
- TrapdConfig is partially replaced already (`TrapdConfigBean` exists) — verify what's still needed from opennms-config.
