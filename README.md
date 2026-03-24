# OpenNMS Delta-V

**Delta-V** is a microservice decomposition of [OpenNMS Horizon][], transforming the monolithic Java application into independently deployable Kafka-connected containers.

> For the original OpenNMS Horizon project description, see [OPENNMS.md](OPENNMS.md).

## Architectural Direction: Karaf Elimination

Delta-V is systematically removing Apache Karaf/OSGi from the runtime architecture. Each service daemon is being migrated from a Karaf OSGi bundle to a standalone **Spring Boot 4 fat JAR** running on a **jlink custom JRE** built from `alpine:3.21`.

**Why:** The monolithic Karaf container was a 4.75GB image carrying the full Sentinel runtime, OSGi framework, and ServiceMix-repackaged Spring 4.2.x — all dead weight for daemons that just need a JVM and a JAR. The Karaf deployment model also couples daemon lifecycles, prevents independent scaling, and makes dependency management painful (ServiceMix Spring 4.x conflicts with Spring Boot 4's Spring 7).

**Where we are:** 11 of 13 daemons migrated. All run on `opennms/daemon-deltav-springboot` — a 143MB Alpine-based custom JRE with diagnostic tools (jcmd, jstack, curl, tcpdump) and the 10 fat JARs layered on top. Each daemon starts in 2–4 seconds.

**Where we're going:** When the remaining 2 Karaf daemons (Collectd, Scriptd) are migrated, the Sentinel/Karaf image is retired entirely. At that point:
- **Phase 2 (Layered JARs)** extracts shared dependencies (~80% overlap across daemons) into a common Docker layer, collapsing the current ~3.5GB of duplicated libraries down to one shared ~300MB layer
- **The `opennms-services` monolith** is eliminated — each daemon's implementation lives in its own focused module
- **ServiceMix Spring bundles** are removed from the dependency tree — no more exclusion blocks
- **Final target:** each daemon runs as a ~200MB image (143MB JRE base + ~5MB application layer + shared dependency layer), starting in 2–4 seconds, independently deployable and scalable

---

## Service Daemon Status

### Deleted (10 daemons)

| Daemon | Reason | PR |
|--------|--------|-----|
| **Notifd** | Notification system eliminated — alerts handled externally | — |
| **Ackd** | Acknowledgment daemon — unused | — |
| **Actiond** | Legacy shell-command execution on events | — |
| **Vacuumd** | Database maintenance automations — replaced by PostgreSQL native | — |
| **Statsd** | Unused statistics reporting | — |
| **Tl1d** | Legacy TL1 telecom protocol support | — |
| **Queued** | RRD write optimization — no longer needed | — |
| **RTCd** | Real-Time Console — dead without webapp | #27 |
| **Ticketer** | Trouble ticketing integration — not in microservice architecture | #29 |
| **DHCPd** | DHCP monitor/detector service | — |

### Migrated to Spring Boot 4 (11 daemons)

| Daemon | Spring Boot Module | Startup | Key Feature | PR |
|--------|--------------------|---------|------------|-----|
| **Alarmd** | `daemon-boot-alarmd` | 2.7s | Full JPA (Hibernate 7) | #27–#30 |
| **EventTranslator** | `daemon-boot-eventtranslator` | 2.4s | JDBC-only, event-conf enrichment | #30 |
| **Trapd** | `daemon-boot-trapd` | 2.0s | Kafka Sink bridge pattern (`daemon-sink-kafka`) | #33 |
| **Syslogd** | `daemon-boot-syslogd` | 2.2s | Reuses Sink bridge, local DNS resolver | #34, #35 |
| **Discovery** | `daemon-boot-discovery` | ~2s | Kafka RPC client pattern (`KafkaRpcClientConfiguration`) | — |
| **Provisiond** | `daemon-boot-provisiond` | 4.2s | JPA + 3× Kafka RPC + Quartz + SNMP adapters (Tier 5) | #41 |
| **BSMd** | `daemon-boot-bsmd` | 3.3s | JPA + AlarmLifecycleListener + REST API | #44 |
| **Pollerd** | `daemon-boot-pollerd` | 4.1s | JPA + Kafka RPC + Twin API + PassiveStatusKeeper | #47 |
| **PerspectivePollerd** | `daemon-boot-perspectivepollerd` | 3.6s | JPA + Kafka RPC + perspective outages + event self-consumption | — |
| **Telemetryd** | `daemon-boot-telemetryd` | 3s | Pure ingestion bridge, multi-bridge KafkaSink (N queues), Twin API for OpenConfig | — |
| **Enlinkd** | `daemon-boot-enlinkd` | 3.5s | JPA + Kafka RPC + LLDP/CDP/OSPF/ISIS/Bridge topology, E2E validated with Containerlab cEOS | #53 |

### Docker Images

| Image | Base | Size | Contents |
|-------|------|------|----------|
| `opennms/jre-deltav:21` | `alpine:3.21` | 143MB | jlink custom JRE (22 modules) + diagnostic tools (jcmd, curl, tcpdump, htop, etc.) |
| `opennms/daemon-deltav-springboot` | `jre-deltav:21` | 4.26GB | 10 Spring Boot fat JARs (Phase 2: layered JAR dedup will reduce significantly) |
| `opennms/daemon-deltav` | `opennms/sentinel` | 4.75GB | 2 remaining Karaf daemons (Collectd, Scriptd) |

### Shared Infrastructure

| Module | Purpose |
|--------|---------|
| `daemon-common` | DataSource, Kafka event transport (with EventConfDao enrichment), Kafka RPC client, JdbcDistPollerDao, JdbcInterfaceToNodeCache, AbstractDaoJpa, EventIpcManagerEnrichingWrapper |
| `daemon-sink-kafka` | KafkaSinkBridge — consumes from Minion Sink topics (`OpenNMS.Sink.*`) |
| `opennms-model-jakarta` | 17 Jakarta Persistence entities + 15 JPA DAOs for Hibernate 7 + 15 Enlinkd AttributeConverters |

### Running on Karaf (2 daemons — migration candidates)

| Daemon | Tier | Complexity | Infrastructure |
|--------|------|-----------|----------------|
| **Scriptd** | 1 | Very Low | Events only |
| **Collectd** | 4 | High | Events + Kafka RPC |

### Other Components Removed

| Component | Reason |
|-----------|--------|
| **opennms-webapp** | Legacy JSP webapp — removed from Maven reactor |
| **opennms-webapp-rest** | REST API for dead webapp |
| **opennms-full-assembly** | Monolithic assembly that packaged the webapp |
| **Notification system** | Tables, entities, DAOs, REST, Vaadin UI, config managers |
| **OnmsEvent entity** | Denormalized into OnmsAlarm/OnmsOutage; EventDao deleted |
| **Database Reports** | Jasper Reports, Availability Reports |
| **Device Config Backup** | Entire feature |
| **Charts** | Legacy JFreeChart bar charts |
| **Self-monitoring** | Deprecated monitors for monitoring OpenNMS itself |
| **Eventd TCP/UDP listeners** | Events arrive via Kafka only |
| **MessageBus JMS** | JMS implementation removed; Kafka-only |
| **Minion webapp dependency** | Minion no longer needs REST connection to core |

---

## Plan Status Dashboard

### Complete (38 docs)

| Date | Plan | Key Achievement |
|------|------|-----------------|
| 03-02 | EventBus Redesign (design + impl + phase2) | Kafka-backed fault events, TSID generation, Alarmd extraction |
| 03-05 | KafkaEventForwarder (design + impl + OSGi) | Per-daemon event enrichment + Kafka publish, no centralized Eventd |
| 03-05 | Karaf-Only Daemon Assembly | Daemon-loader bundle pattern established |
| 03-07 | Strike Fighter Completion (design + impl) | **18/18 tasks**, 4 dead daemons deleted, 8 daemons extracted |
| 03-08 | Feature Removal (design + impl) | Tl1d, Charts, Device Config Backup, Database Reports/Jasper all deleted |
| 03-08 | Enlinkd & Scriptd Extraction | Both running as standalone containers |
| 03-10 | E2E Integration Test | `test-e2e.sh` — 11 tests, 3 phases all passing |
| 03-10 | Project Status Analysis | Snapshot: 100% Strike Fighter, 100% Phase A |
| 03-11 | Db-Init Extraction | Spring Boot 4.0.3 app, 312 MB image (vs 35.6 GB Horizon) |
| 03-11 | EventDao/Notifd/Minion REST Elimination | EventDao/OnmsEvent deleted, Notifd eliminated, Minion REST replaced with Twin API |
| 03-12 | Minion E2E Pipeline Report | 13/13 tests passing, 3 race-condition bugs fixed |
| 03-12 | Minion-Mandatory RPC Migration | **All 6 daemons migrated** to real KafkaRpcClientFactory (PR #17) |
| 03-12 | PerspectivePollerd Cleanup | Standalone container running healthy (TSID=7, PR #15) |
| 03-13 | Minion-Only Listeners (design + impl) | Eventd/DHCP deleted, Syslogd KafkaSinkBridge, Telemetryd container (TSID=18) |
| 03-14 | Java 21 Runtime Upgrade (design + impl) | Karaf 4.4.9, Felix 7.0.5, OSGi R8, Pax Web 8.0 — all daemons + Minion on JRE 21 |
| 03-14 | Webapp Elimination from Test Pipeline | E2E tests use SQL-only verification, webapp removed from docker-compose |
| 03-15 | opennms-model-jakarta (design + impl) | 13 Jakarta Persistence entities, 5 AttributeConverters, 4 JPA DAOs for Hibernate 7 |
| 03-15 | Alarmd Spring Boot 4 Migration | First daemon on Spring Boot 4 — fat JAR, 2.7s startup, HikariCP + Hibernate 7 |
| 03-15 | EventTranslator Spring Boot 4 Migration | Second daemon migrated — JPA excluded, raw JDBC, event-conf enrichment |
| 03-15 | Ticketer/Webapp Deletion | Ticketer daemon deleted, webapp removed from Maven reactor |
| 03-15 | Trapd Spring Boot 4 Migration | Kafka Sink bridge pattern — `daemon-sink-kafka` shared module, E2E trap pipeline verified |
| 03-15 | Syslogd Spring Boot 4 Migration | Reuses Sink bridge, shared JDBC extracted to `daemon-common`, local DNS resolver |
| 03-16 | Discovery Spring Boot 4 Migration | Kafka RPC client pattern — `KafkaRpcClientConfiguration` in `daemon-common`, Minion ping sweeps |
| 03-16 | Provisiond Shared Infrastructure | `DaemonProvisioningConfiguration` — shared NoOpEntityScopeProvider, LocalServiceDetectorRegistry |
| 03-17 | Provisiond Spring Boot 4 Migration | Tier 5: JPA + 3× Kafka RPC + Quartz + SNMP adapters, constructor injection, 13 JPA DAOs, E2E with 22 SNMP interfaces |
| 03-20 | BSMd Spring Boot 4 Migration | JPA + AlarmLifecycleListener + REST API, alarm snapshot polling (10s interval) |
| 03-21 | Pollerd Spring Boot 4 Migration | JPA + Kafka RPC + Twin API + PassiveStatusKeeper, constructor injection, `%service%` token fix, transport-layer EventConfDao enrichment — BSM E2E + Passive E2E passing |
| 03-21 | PerspectivePollerd Spring Boot 4 Migration | First daemon without opennms-services dep, JPA + Kafka RPC + perspective outages, ServiceMix exclusion cleanup, 0-arg event adapter pattern |
| 03-22 | Lightweight Docker Images | jlink custom JRE on Alpine (143MB base), all Spring Boot daemons on `daemon-deltav-springboot` image, `-XX:MaxMetaspaceSize=256m` cap |
| 03-22 | Telemetryd Spring Boot 4 Migration | Pure ingestion bridge (no adapters, no ES), multi-bridge KafkaSink, Twin API for OpenConfig, field injection pattern |
| 03-24 | Enlinkd Spring Boot 4 Migration + E2E Test | JPA + Kafka RPC + LLDP/CDP/OSPF/ISIS/Bridge topology, @Transactional fix for link persistence, E2E test with Containerlab cEOS (20/20 passing) |

### Superseded (2 docs)

| Date | Plan | Superseded By |
|------|------|---------------|
| 03-02 | EventBus Follow-ups | Later phases (Vacuumd deleted, not migrated) |
| 03-07 | Strike Fighter Design | Exceeded — 17 services achieved vs. 15 planned |

### In Progress / Partial (1 doc)

| Date | Plan | Remaining Work |
|------|------|----------------|
| 03-09 | Microservice Event Architecture | Two-topic Kafka design working; full IPC flow documented |

### Deferred (2 docs)

| Date | Plan | Reason |
|------|------|--------|
| 03-09 | Minion-Mandatory Architecture | Non-distributable monitors, collector delegation gaps |
| 03-12 | Next-session prompts (×2) | Handoff docs for future sessions |

### Architectural Milestones Achieved

1. **Events table eliminated** — events never touch PostgreSQL
2. **ActiveMQ eliminated** — all IPC via Kafka
3. **Core container eliminated** — replaced by lightweight `db-init` Spring Boot app
4. **Spring Boot 4 migration** — 11 daemons migrated as `java -jar` fat JARs with 2–4s startup on jlink Alpine JRE
5. **Kafka Sink bridge** — `daemon-sink-kafka` module consumes from Minion Sink topics (`OpenNMS.Sink.*`), reused by Trapd and Syslogd
6a. **Kafka RPC client** — `KafkaRpcClientConfiguration` in `daemon-common` sends RPC requests to Minions, used by Discovery and Provisiond (3× RPC: SNMP, Detector, DNS)
6. **opennms-model-jakarta** — 17 Jakarta Persistence entities with JPA AttributeConverters + 13 JPA DAOs replacing Hibernate 3.6 UserTypes
7. **Event-conf enrichment** — `EventConfEnrichmentService` in daemon-common loads alarm-data from PostgreSQL for all Spring Boot daemons
8. **10 daemons deleted** — Notifd, Ackd, Actiond, Vacuumd, Statsd, Tl1d, Queued, RTCd, Ticketer, DHCPd
9. **Minion RPC mandatory** — all 6 polling/collection daemons use real Kafka RPC
10. **End-to-end validated** — direct (11 tests), Minion (13 tests), and Enlinkd (20 tests — LLDP topology via remote Minion + Containerlab cEOS) pipelines passing
11. **Legacy features removed** — Tl1d, Charts, Device Config Backup, Database Reports/Jasper, DHCP monitor, webapp, notifications
12. **Minion-only network ingress** — Eventd listeners deleted, Syslogd/Telemetryd consume via KafkaSinkBridge from Minion
13. **Java 21 runtime** — all daemon + Minion containers run JRE 21 with Karaf 4.4.9, Felix 7.0.5, OSGi R8, Pax Web 8.0

### Remaining Work

**Spring Boot 4 migration** — 2 Karaf daemons remain (Collectd, Scriptd). Three shared infrastructure patterns established: Kafka event transport (all daemons), Kafka Sink bridge (Trapd, Syslogd, Telemetryd), Kafka RPC client (Discovery, Provisiond, Pollerd, Collectd, Enlinkd, PerspectivePollerd). When both are migrated: retire Karaf/Sentinel image, implement Phase 2 layered JAR deduplication, eliminate `opennms-services` monolith. Deferred items: HW inventory adapter (Hibernate 7 entity issue), Minion echo probes, service detector RPC, MATE scopes.

**Deferred** — Minion-Mandatory Architecture requires prerequisite work on non-distributable ServiceMonitors and collector delegation.

All plan documents are in [`docs/plans/`](docs/plans/) and [`docs/superpowers/`](docs/superpowers/).

---

## What Is Delta-V?

OpenNMS Horizon is an enterprise-grade open-source network monitoring platform. Delta-V restructures it from a single 35.6 GB monolith into lean, focused microservices:

- **Each daemon runs in its own container** — independent scaling, isolation, and restartability
- **Kafka-only event transport** — no ActiveMQ, no shared event bus
- **Events never touch PostgreSQL** — only alarms are persisted to the database
- **Lightweight Docker images** — 9 Spring Boot daemons run on `opennms/daemon-deltav-springboot` (143MB jlink Alpine JRE + fat JARs); 4 Karaf daemons on `opennms/daemon-deltav`; Minion on `opennms/minion-deltav`
- **Spring Boot 4 migration: 11 of 13 daemons done** — Alarmd, EventTranslator, Trapd, Syslogd, Discovery, Provisiond, BSMd, Pollerd, PerspectivePollerd, Telemetryd, Enlinkd run as fat JARs (2–4s startup); 2 Karaf daemons remain (Collectd, Scriptd)
- **One-shot database initialization** — `opennms/db-init` (312 MB) replaces the Core container for schema setup

## Architecture

```
Minion → Kafka Sink → Trapd/Syslogd
                          ↓
            KafkaEventForwarder → opennms-fault-events (Kafka)
                                        ↓
                        ┌───────────────┼───────────────┐
                        ↓               ↓               ↓
                    Alarmd      EventTranslator    All Daemons
                   (→ PostgreSQL)  (→ translate     (subscribe to
                                    → re-publish)   relevant events)
                                        ↓
                              opennms-ipc-events (Kafka)
                                        ↓
                              Provisiond, Discovery, etc.
```

### Services

| Service | Runtime | TSID | Purpose |
|---------|---------|------|---------|
| alarmd | **Spring Boot 4** | 2 | Kafka → alarm creation/reduction → PostgreSQL |
| pollerd | **Spring Boot 4** | 4 | Service availability polling |
| collectd | Karaf | 5 | Performance data collection |
| perspectivepollerd | **Spring Boot 4** | 7 | Perspective (remote location) polling |
| discovery | **Spring Boot 4** | 9 | Network discovery (via Minion Kafka RPC) |
| trapd | **Spring Boot 4** | 10 | SNMP trap reception (via Minion Kafka Sink) |
| syslogd | **Spring Boot 4** | 11 | Syslog reception (via Minion Kafka Sink) |
| eventtranslator | **Spring Boot 4** | 13 | Event translation rules + enrichment |
| enlinkd | Karaf | 14 | Enhanced link discovery |
| scriptd | Karaf | 15 | Script-based event automation |
| provisiond | **Spring Boot 4** | 25 | Node provisioning and scanning (via Minion 3× Kafka RPC) |
| bsmd | **Spring Boot 4** | 17 | Business service monitoring |
| telemetryd | **Spring Boot 4** | 18 | Telemetry/flow ingestion bridge (via Minion Kafka Sink) |
| minion | Karaf | — | Distributed data collection agent |
| db-init | Spring Boot 4 | — | One-shot Liquibase schema migration |
| postgres | postgres:15 | — | PostgreSQL database (alarms only) |
| kafka | Apache Kafka | — | Event transport backbone |

### Kafka Topics

| Topic | Purpose |
|-------|---------|
| `opennms-fault-events` | Alarm-bearing events (traps, syslog, translated events with alarm-data) |
| `opennms-ipc-events` | Daemon-to-daemon internal events (newSuspect, nodeScanCompleted, reloadDaemonConfig) |
| `OpenNMS.Sink.Trap` | Minion → Trapd raw trap forwarding |
| `OpenNMS.Sink.Syslog` | Minion → Syslogd raw syslog forwarding |
| `OpenNMS.Sink.Telemetry-*` | Minion → Telemetryd per-protocol flow forwarding |

## Quick Start

```bash
cd opennms-container/delta-v

# Start core infrastructure + all daemons
COMPOSE_PROFILES=full docker compose up -d

# Start minimal set (alarmd, pollerd, trapd, provisiond)
COMPOSE_PROFILES=lite docker compose up -d

# Check service health
docker compose ps

# Run all E2E tests (no webapp required — SQL-only verification)
./test-e2e.sh          # 11 tests: trap → provision → alarm lifecycle
./test-minion-e2e.sh   # 13 tests: Minion → Kafka Sink → alarm lifecycle
./test-syslog-e2e.sh   # 15 tests: syslog → Minion → Cisco alarm lifecycle
```

### Prerequisites

- **JDK 21** (daemon/minion build and runtime)
- Docker Desktop with **16 GB memory** (17+ JVM containers)
- `snmptrap` (net-snmp) for E2E tests

## Building

Requires **JDK 21** (`jenv`, `JAVA_HOME`, or temurin-21 auto-detected).

```bash
cd opennms-container/delta-v

# Full build: compile → assemble → images → deltav
./build.sh

# Or individual steps:
./build.sh compile    # Maven compile with JDK 21
./build.sh assemble   # Build Karaf assemblies (sentinel, minion, daemon, alarmd)
./build.sh images     # Build base Docker images (sentinel, minion, db-init)
./build.sh jre        # Build jlink custom JRE base image (rarely needed)
./build.sh deltav     # Build Delta-V layered images (daemon-deltav-springboot, daemon-deltav, minion-deltav)
```

See [BUILD.md](BUILD.md) for detailed build instructions.

## Key Design Decisions

1. **No events table** — Events flow exclusively via Kafka. Only alarms are persisted to PostgreSQL by Alarmd. The `events`, `event_parameters`, `notifications`, and `usersnotified` tables are eliminated.

2. **No ActiveMQ** — All cross-container communication uses Kafka topics. The AMQ hub-and-spoke transport is fully removed.

3. **Each daemon is self-contained** — Every daemon container has its own `EventWriter`, `EventListener`, `EventExpander`, and `KafkaEventForwarder`. No dependency on Eventd or a central event bus.

4. **Producer-side event enrichment** — Each daemon's `KafkaEventForwarder` loads 157 event definitions from the database via `EventConfInitializer` and applies severity + alarm-data to events before publishing to Kafka.

5. **Minion communicates via Kafka only** — No REST dependency. SNMPv3 user config distributed via Twin API. Traps, syslog, and telemetry forwarded via Kafka Sink topics.

6. **Minion is sole network ingress** — No daemon container binds external UDP/TCP monitoring ports. All protocol data (traps, syslog, flows) enters via Minion → Kafka Sink → KafkaSinkBridge → daemon container.

## Project Status

See [DELTA-V_Status.md](DELTA-V_Status.md) for detailed progress tracking.

**Current state:** 15 services running (10 daemons deleted, 10 migrated to Spring Boot 4). Alarmd, EventTranslator, Trapd, Syslogd, Discovery, Provisiond, BSMd, Pollerd, PerspectivePollerd, and Telemetryd run as Spring Boot 4 fat JARs (2–4s startup). 3 daemons remain on Karaf. Three shared infrastructure patterns: event transport, Sink bridge, RPC client.

## Documentation

| Document | Description |
|----------|-------------|
| [OPENNMS.md](OPENNMS.md) | Original OpenNMS Horizon project description |
| [DELTA-V_Status.md](DELTA-V_Status.md) | Detailed status of all Delta-V work |
| [BUILD.md](BUILD.md) | Build instructions |
| [CLAUDE.md](CLAUDE.md) | AI assistant project context |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Contribution guidelines |

Design documents are in `docs/plans/`.

## License

This project is licensed under the [GNU Affero General Public License v3](LICENSE.md).

[OpenNMS Horizon]: http://www.opennms.com/
