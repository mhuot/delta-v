# Features Directory Relevancy Review

**Date:** 2026-04-03
**Branch:** `claude/review-features-relevancy-nWQlG`
**Status:** Complete

## Context

Delta-V has transformed OpenNMS from a Karaf/OSGi monolith into 12 independently deployable Spring Boot 4 daemons + Minion, all communicating via Kafka. This review evaluates every module in `features/` against the current architecture to identify what is essential, transitively needed, or removable dead weight.

**Key architectural facts driving this analysis:**
- Karaf/OSGi fully removed — Spring Boot 4 only
- ActiveMQ eliminated — Kafka only
- Events table eliminated — events never touch PostgreSQL
- Legacy webapp (JSP/Vaadin) removed
- Sentinel retired
- Notification system removed
- Docker-only deployment (no RPM/Debian)
- 12 deleted daemons: Scriptd, Notifd, Ackd, Actiond, Vacuumd, Statsd, Tl1d, Queued, RTCd, Ticketer, DHCPd, Eventd

---

## Classification Legend

| Category | Meaning |
|----------|---------|
| **ESSENTIAL** | Directly depended on by a daemon-boot module |
| **TRANSITIVE** | Depended on by an ESSENTIAL module (needed at compile/runtime) |
| **CANDIDATE FOR REMOVAL** | No daemon-boot dependency, tied to removed subsystems |
| **REVIEW NEEDED** | Indirect or partial use; needs closer inspection before removing |

---

## ESSENTIAL — Directly Used by Daemon-Boot Modules (21 modules)

| Module | Used By | Notes |
|--------|---------|-------|
| `alarms` | daemon-boot-alarmd (indirect via daemon-common) | Alarm lifecycle, reduction keys |
| `bsm` | daemon-boot-bsmd | Business service monitoring daemon, service API, impl |
| `collection` | daemon-boot-collectd, daemon-boot-minion | Collection API, impl, config, client-rpc, snmp-collector, thresholding |
| `discovery` | daemon-boot-discovery | Discovery daemon |
| `enlinkd` | daemon-boot-enlinkd | Full link discovery: daemon, API, config, service, all adapters/updaters/discovers |
| `event-translator` | daemon-boot-eventtranslator | Single-file daemon, event-conf enrichment |
| `events` | daemon-common, daemon-boot-trapd, daemon-boot-syslogd | events.api, events.traps, events.syslog |
| `executor-factory` | (transitive via collection) | Thread pool factories |
| `kafka` | all daemons (Kafka transport) | Kafka producer/consumer, RPC, Sink bridge |
| `minion` | daemon-boot-minion | Heartbeat producer/common, Minion core |
| `perspectivepoller` | daemon-boot-perspectivepollerd | Perspective polling daemon |
| `poller` | daemon-boot-pollerd, daemon-boot-perspectivepollerd, daemon-boot-minion | Poller API, impl, config, client-rpc, monitors.core |
| `provisioning` | daemon-boot-provisiond | Provisioning API (aggregator POM) |
| `scv` | daemon-common | Secure Credentials Vault API + JCEKS impl |
| `telemetry` | daemon-boot-telemetryd | Telemetry daemon, API, common, config.jaxb, registry |
| `timeseries` | daemon-boot-collectd | Timeseries storage API |
| `topologies` | daemon-boot-enlinkd | topologies.service.impl used by Enlinkd |

### Sub-modules confirmed in use:
- `collection/thresholding/api` — collectd
- `distributed/kv-store/json/noop` — pollerd, collectd (no-op key-value store)
- `rest/common` — BSMd REST API
- `rest-provider` — BSMd REST impl

---

## TRANSITIVE — Needed by Essential Modules (8 modules)

| Module | Pulled In By | Notes |
|--------|-------------|-------|
| `api-layer` | collection/thresholding, minion/heartbeat, minion/repository | API layer common + minion |
| `dnsresolver` | telemetry/protocols/netflow/parser, sflow/parser, bmp/parser, minion | DNS resolution for flow parsers |
| `flows` | telemetry/protocols/netflow, sflow, bmp, flows | Flow processing for Telemetryd |
| `openconfig` | telemetry/protocols/openconfig | gRPC-based telemetry streaming |
| `rest` | bsm/rest/api, bsm/rest/impl | REST common utilities for BSMd |
| `rest-provider` | bsm/rest/impl | REST provider interfaces |
| `distributed` | pollerd, collectd (kv-store/json/noop only) | Only the no-op KV store submodule |
| `graphml` | (only if graph features are active) | May be dead; verify if graph/provider/graphml is used |

---

## CANDIDATE FOR REMOVAL — 37 Modules (No Daemon-Boot Dependencies)

### Karaf/OSGi-Specific (dead without Karaf)

| Module | Reason for Removal |
|--------|-------------------|
| `karaf-health` | Karaf health checks — Karaf eliminated |
| `opennms-osgi-core` | OSGi core bundle — OSGi eliminated |
| `opennms-osgi-core-rest` | OSGi REST bridge — OSGi eliminated |
| `osgi-jsr223` | OSGi script engine — OSGi eliminated |
| `sentinel` | Sentinel container — Sentinel retired (1 Java file remains) |
| `config` | Karaf ConfigAdmin-based configuration manager — replaced by Spring Boot config |

### ActiveMQ (replaced by Kafka)

| Module | Reason for Removal |
|--------|-------------------|
| `activemq` | ActiveMQ broker, API, pool, shell — ActiveMQ eliminated |

### Legacy Web UI (webapp removed)

| Module | Reason for Removal |
|--------|-------------------|
| `root-webapp` | Legacy JSP webapp root — webapp deleted |
| `themes` | Vaadin themes (CSS only) — Vaadin UI removed |
| `vaadin` | Vaadin aggregator POM — Vaadin UI removed |
| `vaadin-components` | Generic Vaadin components — Vaadin UI removed |
| `vaadin-dashboard` | Vaadin dashboard — Vaadin UI removed |
| `vaadin-dashlets` | Vaadin dashlets — Vaadin UI removed |
| `vaadin-jmxconfiggenerator` | JMX config generator UI — Vaadin UI removed |
| `vaadin-snmp-events-and-metrics` | SNMP events/metrics UI — Vaadin UI removed |
| `vaadin-surveillance-views` | Surveillance views UI — Vaadin UI removed |
| `topology-map` | Topology map UI (566 Java files) — Vaadin UI removed |
| `ui-extension` | UI extension framework — no UI consumers |
| `springframework-security` | Spring Security for legacy webapp — webapp removed |

### Notification/Ticketing (systems removed)

| Module | Reason for Removal |
|--------|-------------------|
| `request-tracker` | RT ticketing integration — ticketing removed |

### External Integrations (unused)

| Module | Reason for Removal |
|--------|-------------------|
| `amqp` | AMQP event/alarm gateway — ActiveMQ/AMQP eliminated |
| `eif-adapter` | IBM EIF adapter (5 Java files) — unused |
| `opennms-es-rest` | Elasticsearch REST integration — legacy ES approach |
| `situation-feedback` | Alarm correlation feedback — unused in microservices |
| `datachoices` | Opt-in usage data collection — legacy webapp feature |
| `usageanalytics` | Usage analytics reporting — legacy webapp feature |

### Utilities with No Consumers

| Module | Reason for Removal |
|--------|-------------------|
| `instrumentationLogReader` | Reads instrumentation logs — no daemon consumer |
| `name-cutter` | String truncation utility (2 Java files) — no daemon consumer |
| `search` | Search REST API — no daemon consumer, webapp removed |
| `system-report` | System report generator — legacy diagnostic tool |
| `nrtg` | Near Real-Time Graphing — legacy webapp feature |
| `mib-compiler` | MIB compiler — standalone tool, not a daemon dependency |
| `jmx-config-generator` | JMX config generator — standalone tool |
| `timeseries-shell` | Karaf shell command (1 Java file) — Karaf removed |
| `timeseries-evaluate` | Timeseries evaluation tool — no daemon consumer |
| `inmemory-timeseries-plugin` | In-memory timeseries (2 Java files) — test/dev only |

---

## REVIEW NEEDED — 8 Modules (Partial or Uncertain Use)

| Module | Question | Recommendation |
|--------|----------|----------------|
| `graph` | Used by api-layer/core and topology-map. Topology-map is removable. Is api-layer/core still needed? | **Likely removable** if api-layer only uses `.common` and `.minion` |
| `graphml` | Only used by graph/provider/graphml and topology-map. | **Likely removable** with topology-map |
| `geolocation` | Only used by topology-map. | **Removable** with topology-map |
| `geocoder` | Only used by geolocation/service. | **Removable** with geolocation |
| `status` | Only used by geolocation/api. | **Removable** with geolocation |
| `measurements` | Used by timeseries, topology-map, newts. Timeseries IS used by collectd. | **Check** if timeseries actually needs measurements at runtime |
| `newts` | Cassandra-backed timeseries. Is this the active TSDB backend? | **Keep if Newts is the TSDB**; remove if replaced |
| `newts-repository-converter` | Migration tool for Newts. | **Removable** — one-time migration utility |
| `elastic` | Elasticsearch client utilities. Used by flows and alarms/history. | **Keep if flows/ES are active**; otherwise removable |
| `jest` | Jest (ES HTTP client). Used by alarms/history/elastic. | **Keep if ES alarm history is active**; otherwise removable |
| `grpc` | gRPC exporter. Not referenced by any daemon-boot. | **Likely removable** unless planned for future use |
| `endpoints` | Grafana endpoint support. | **Likely removable** — no daemon consumer |

---

## Summary

| Category | Count | Action |
|----------|-------|--------|
| **ESSENTIAL** | 21 | Keep — actively used by daemon-boot modules |
| **TRANSITIVE** | 8 | Keep — needed by essential modules |
| **CANDIDATE FOR REMOVAL** | 37 | Remove from `features/pom.xml` reactor; archive or delete |
| **REVIEW NEEDED** | 8 | Investigate before removing; some may be transitively required |
| **Total** | 74 | (of ~79 directories in features/) |

### Estimated Impact of Removal

- **~37 modules** can be removed from the Maven reactor immediately
- **topology-map** alone accounts for **566 Java files** — the largest removable module
- Removing Vaadin modules (7 modules + themes) eliminates the entire legacy UI layer
- Removing ActiveMQ, AMQP, Sentinel, and OSGi modules eliminates retired infrastructure
- The 8 "review needed" modules should be validated with a test build before removal

### Recommended Next Steps

1. **Quick win:** Remove the 37 candidate modules from `features/pom.xml` `<modules>` section
2. **Validate:** Run `make build` to confirm no transitive dependency breaks
3. **Investigate:** Check the 8 "review needed" modules with targeted dependency analysis
4. **Clean up:** Remove directories for confirmed-dead modules to reduce repo size
