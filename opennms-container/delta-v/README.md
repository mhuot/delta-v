# OpenNMS Delta-V

**Composable, containerized deployment of OpenNMS Horizon.**

Delta-V decomposes the monolithic OpenNMS into independently scalable services connected by Kafka, PostgreSQL, and (for the flow pipeline) ClickHouse. Each daemon runs in its own container as a Spring Boot fat JAR, communicating via Kafka event topics. There is no core container and no legacy webapp — schema migration is handled by one-shot `db-init` and `clickhouse-init` containers, and operator observability lives on each daemon's Spring Boot Actuator endpoints (`/actuator/health`, `/actuator/prometheus`).

```
                    ┌──────────────────────────────────────────────────┐
                    │                   Kafka (KRaft)                  │
                    │    opennms-fault-events / opennms-ipc-events     │
                    └──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬──┬─────┘
                       │  │  │  │  │  │  │  │  │  │  │  │  │  │
  ┌─────────┐     ┌────┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴──┴────┐
  │Postgres │◄────┤ alarmd │ pollerd │ collectd │ provisiond │ ...   │
  │         │     │ Spring Boot fat JARs — one per daemon           │
  └─────────┘     └─────────────────────────────────────────────────┘
```

## Spring Boot Migration Progress

All 12 daemons have been migrated from Karaf to Spring Boot 4. Each runs as an independent fat JAR with its own JPA/Hibernate context, Kafka event transport, and health endpoint.

| Daemon             | Status | PR   | E2E Verified | Notes |
|--------------------|--------|------|--------------|-------|
| Alarmd             | Done   | #32  | Yes          | Kafka event consumer, alarm processing |
| Pollerd            | Done   | #47  | Yes          | Service polling via Minion RPC |
| PerspectivePollerd | Done   | #48  | Yes          | Perspective polling from remote locations |
| Provisiond         | Done   | #38  | Yes          | Node provisioning, SNMP/ICMP detection via Minion |
| Discovery          | Done   | #42  | Yes          | Network discovery via Minion |
| Trapd              | Done   | #35  | Yes          | SNMP trap reception via Kafka Sink |
| Syslogd            | Done   | #36  | Yes          | Syslog reception via Kafka Sink |
| EventTranslator    | Done   | #37  | Yes          | Event transformation rules |
| BSM Daemon         | Done   | #44  | Yes          | Business Service Monitor |
| Enlinkd            | Done   | #50  | Yes          | Link discovery (LLDP/CDP/OSPF/IS-IS/Bridge) via Minion |
| Telemetryd         | Done   | #52  | Yes          | Telemetry ingestion bridge |
| **Collectd**       | **Done** | **#56** | **Yes** | **SNMP data collection via Minion SNMP proxy** |

### Shared Infrastructure (daemon-common)

All 12 daemons share infrastructure from `core/daemon-common`:

- **BeanUtils bridge**: `BeanUtils` is registered as a Spring bean so legacy static lookups (`BeanUtils.getBean(...)`) route to the daemon's own ApplicationContext instead of falling through to the legacy `ContextRegistry` XML context chain.
- **MATE EntityScopeProvider**: `DaemonEntityScopeProvider` resolves MATE metadata expressions (`${node:label}`, `${scv:alias:password}`, `${asset:region}`) in daemons with database access (Provisiond, Pollerd, Collectd, Enlinkd, PerspectivePollerd). Daemons without DAOs fall back to `NoOpEntityScopeProvider`.
- **Secure Credentials Vault**: JCEKS-backed `SecureCredentialsVault` reads from `${opennms.home}/etc/scv.jce` for `${scv:...}` credential interpolation.
- **Thresholding**: Stubbed with a no-op `ThresholdingService`. Full thresholding support is a follow-up task.

### Karaf Image Retirement

With all 12 daemons on Spring Boot, the Karaf-based Sentinel image (`opennms/daemon-deltav`) can be retired. The only Karaf component remaining is the Minion, which runs the standard OpenNMS Minion distribution.

## Services

| Service            | Image                         | Purpose                                                                  | Host Port |
|--------------------|-------------------------------|--------------------------------------------------------------------------|-----------|
| postgres           | postgres:15                   | Shared database (alarms only)                                            | 5432      |
| kafka              | apache/kafka                  | Event bus (KRaft mode)                                                   | 19092     |
| db-init            | opennms/db-init               | One-shot PostgreSQL schema migration (exits after init)                  | —         |
| clickhouse         | clickhouse/clickhouse-server  | Flow storage: `deltav.flows_raw` + 4 dimension MVs                       | 8123      |
| clickhouse-init    | one-shot                      | One-shot ClickHouse DDL bootstrap                                         | —         |
| minion             | opennms/minion-boot           | Spring Boot 4 distributed data collection agent + UDP flow listener      | 4729/udp  |
| snmp-agent         | tandrup/netsnmp               | Local SNMP test target for Collectd / detectors                          | —         |
| alarmd             | opennms/alarmd                | Alarm processing (Kafka consumer)                                        | —         |
| pollerd            | opennms/pollerd               | Service polling via Minion RPC                                           | —         |
| collectd           | opennms/collectd              | SNMP data collection via Minion SNMP proxy                               | —         |
| discovery          | opennms/discovery             | Network discovery via Minion RPC                                         | —         |
| provisiond         | opennms/provisiond            | Node provisioning and detection                                          | —         |
| trapd              | opennms/trapd                 | SNMP trap reception (Kafka Sink)                                         | —         |
| syslogd            | opennms/syslogd               | Syslog reception (Kafka Sink)                                            | —         |
| eventtranslator    | opennms/eventtranslator       | Event transformation rules                                               | —         |
| enlinkd            | opennms/enlinkd               | Link discovery (CDP, LLDP, OSPF, IS-IS, Bridge)                          | —         |
| bsmd               | opennms/bsmd                  | Business Service Monitor                                                 | 8180      |
| perspectivepollerd | opennms/perspectivepollerd    | Perspective polling from remote locations                                | —         |
| telemetryd         | opennms/telemetryd            | Non-flow telemetry ingestion (OpenConfig via Twin API)                   | —         |
| flow-enricher      | opennms/flow-enricher         | Flow decode (horizon UDP parsers) + enrich + publish to ClickHouse       | 8080      |

All daemon containers extend a shared `opennms/daemon-base` image built on top of `opennms/jre-deltav:21` (a jlink custom JRE on `alpine:3.21`). Each per-daemon image adds only the libraries unique to that daemon via layered Docker image deduplication.

## Quick Start

### Prerequisites

- Docker Engine 24+ with Compose v2
- Java 21 (for building from source)
- 8 GB RAM allocated to Docker (16 GB recommended for full deployment)

### Build from Source

```bash
# Clone the repository
git clone https://github.com/pbrane/delta-v.git
cd delta-v

# Full build: compile + assemble + Docker images
opennms-container/delta-v/build.sh

# Or build just the images (if Maven artifacts exist)
opennms-container/delta-v/build.sh images
```

### Deploy

```bash
cd opennms-container/delta-v

# Start with a profile
./deploy.sh up lite       # Essential daemons
./deploy.sh up passive    # Lite + trapd/syslogd/eventtranslator
./deploy.sh up full       # All services

# Check status
./deploy.sh status

# Verify deployment
./deploy.sh test
```

**No Web UI.** The legacy OpenNMS JSP webapp has been removed from the Maven reactor (`opennms-webapp` + `opennms-webapp-rest`). Operator observability lives on each daemon's Spring Boot Actuator:

```bash
# Per-daemon health (reachable when the daemon exposes a management port)
curl http://localhost:8180/actuator/health       # bsmd
docker compose exec flow-enricher wget -qO- http://localhost:8080/actuator/health

# Prometheus metrics from the flow-enricher (41 flow_enricher_* meters)
docker compose exec flow-enricher wget -qO- http://localhost:8080/actuator/prometheus | grep '^flow_enricher_'
```

### Manage

```bash
# View logs
./deploy.sh logs              # All services
./deploy.sh logs alarmd       # Single service

# Stop (preserve data)
./deploy.sh down

# Reset (destroy all data)
./deploy.sh reset
```

## E2E Tests

Nine end-to-end test suites validate the full pipeline:

```bash
cd opennms-container/delta-v

bash test-e2e.sh                 # Full alarm create/clear via SNMP traps
bash test-minion-e2e.sh          # Trap → Minion → Kafka → Alarmd lifecycle
bash test-minion-rpc-e2e.sh      # Detector + Monitor via Minion RPC
bash test-syslog-e2e.sh          # Syslog → Minion → Kafka → Alarmd lifecycle
bash test-passive-e2e.sh         # Passive status via EventTranslator + Pollerd
bash test-collectd-e2e.sh        # SNMP data collection via Minion
bash test-perspective-e2e.sh     # Perspective polling from remote locations + outage lifecycle
bash test-enlinkd-e2e.sh         # LLDP/CDP link discovery on Containerlab cEOS
bash test-flows-e2e.sh           # softflowd + hsflowd → Minion → flow-enricher → ClickHouse (18 assertions across 4 phases)
```

All scripts support:
- `--verbose` — show diagnostic output on failure
- `--pre-clean` — delete all nodes and alarms from DB before running
- `--post-cleanup` — delete test data after run

## Build Script Reference

```bash
./build.sh              # Full build (compile + assemble + images)
./build.sh compile      # Maven compile only
./build.sh assemble     # Build distribution tarballs
./build.sh images       # Build Docker images (requires prior assembly)
./build.sh deltav       # Build Delta-V layered images
./build.sh push         # Build and push to registry
./build.sh clean        # Remove Docker volumes

# Push to custom registry
DOCKER_ORG=pbranestrategy ./build.sh push
```

## Architecture

Delta-V replaces the monolithic OpenNMS runtime with a composable service mesh:

- **db-init** runs schema migration (Liquibase) and exits — no persistent core container
- **Daemon containers** each run a single daemon as a Spring Boot fat JAR with embedded Tomcat for health endpoints (`/actuator/health`)
- **Minion** handles distributed data collection (SNMP, ICMP) via Kafka IPC
- All daemons use **Hibernate 7 / Jakarta Persistence** with the `opennms-model-jakarta` entity model

All services communicate via two Kafka topics: `opennms-fault-events` (alarm-bearing events) and `opennms-ipc-events` (daemon-to-daemon coordination). Each service generates globally unique event IDs using TSID (Time-Sorted IDs) with a unique node-id per JVM.

### Event Flow

```
Daemon → KafkaEventForwarder → Kafka → KafkaEventSubscriptionService → Alarmd
                                  ↓
                    Other daemons subscribe to relevant events
```

Events bypass the traditional `events` database table entirely. They flow through Kafka in real-time, and Alarmd processes them directly into alarms.

### Daemon Boot Pattern

Each Spring Boot daemon follows a consistent pattern:

```
core/daemon-boot-<name>/
├── src/main/java/.../boot/
│   ├── <Name>Application.java          # @SpringBootApplication entry point
│   ├── <Name>JpaConfiguration.java     # JPA entities, FilterDao, TransactionTemplate
│   ├── <Name>DaemonConfiguration.java  # Daemon bean, lifecycle, config factories
│   └── <Name>RpcConfiguration.java     # Kafka RPC client (if needed)
└── src/main/resources/
    └── application.yml                 # Datasource, Kafka, RPC settings
```

Infrastructure beans (Kafka event transport, RPC client factory, TSID) are shared via `core/daemon-common`.

## Memory Requirements

Running all services requires significant memory. If Docker Desktop runs out of memory (exit code 137), use deployment profiles:

| Profile | Services | Approx. Memory |
|---------|----------|-----------------|
| lite    | ~10      | ~8 GB           |
| passive | ~13      | ~10 GB          |
| full    | all      | ~12 GB          |

## Kafka Time-Series Producer (Collectd)

### Enabling

The Kafka Time-Series producer in the Collectd daemon is off by default. To enable:

```bash
export DELTAV_TIMESERIES_ENABLED=true
docker compose up -d
```

When enabled, Collectd publishes one `TimeseriesBatch` protobuf record per CollectionSet poll to the `deltav-timeseries` Kafka topic, keyed `{location}@{node_id}`. The existing `InMemoryStorage` TSS backend continues to run alongside — the Kafka publisher is additive, not a replacement.

### Topic provisioning

Both topics are declared as Spring Boot `NewTopic` beans in the Collectd application and are created with the following default configuration the first time Collectd starts with the flag on:

| Topic | Partitions | Retention | Cleanup | Compression |
|-------|-----------|-----------|---------|-------------|
| `deltav-timeseries` | 16 | 7 days | delete | lz4 |
| `deltav-node-context` | 8 | infinite | compact | default |

Do **not** rely on Kafka broker auto-create: defaults are 1 partition / 1 replica, which silently defeats the 16-partition design.

### Tunables (environment variables)

| Variable | Default | Purpose |
|---|---|---|
| `DELTAV_TIMESERIES_ENABLED` | `false` | Kill switch — opt-in feature flag |
| `DELTAV_TIMESERIES_PARTITIONS` | `16` | `deltav-timeseries` partition count |
| `DELTAV_TIMESERIES_REPLICATION_FACTOR` | `1` dev / `3` prod | `deltav-timeseries` replication |
| `DELTAV_TIMESERIES_RETENTION_DAYS` | `7` | Time-series record retention |
| `DELTAV_NODE_CONTEXT_PARTITIONS` | `8` | Context topic partitions |
| `DELTAV_NODE_CONTEXT_REPLICATION_FACTOR` | `1` dev / `3` prod | Context topic replication |

### Observability

Collectd's `/actuator/prometheus` endpoint exposes:

| Metric | Type | Purpose |
|---|---|---|
| `deltav_timeseries_batches_published_total` | counter | Successful publishes |
| `deltav_timeseries_batches_failed_total{reason}` | counter | Failures by reason (`serialization_error`, `kafka_send_error`, `empty_batch`, `translator_error`) |
| `deltav_timeseries_batch_size_bytes` | distribution summary | Wire size per record |
| `deltav_timeseries_batch_size_warning_total` | counter | Records over 800 KB |
| `deltav_timeseries_resources_per_batch` | distribution summary | Resource count per record |
| `deltav_timeseries_publish_duration_seconds` | timer | End-to-end publish latency |

### Expected disk footprint

A 1,000-node deployment polling every 5 minutes with default settings produces roughly 140 GB of `deltav-timeseries` log on disk before the 7-day retention window rolls. Adjust `DELTAV_TIMESERIES_RETENTION_DAYS` to shape this, or reduce per-poll scope in `collectd-configuration.xml` for dense nodes.

### Known limitations (schema v1)

- Collectd is a singleton service today: there is no horizontal scale and no leader election. If Collectd restarts mid-poll, the current CollectionSet may not reach Kafka. The scheduler/publisher split that addresses this is tracked separately.
- The wire format is subject to breaking changes during Phase 1 (dev-only). Once Phase 2 ships (production-enabled), only forward-compatible schema changes are allowed.
- The `deltav-node-context` topic has no producer in this release — provisiond's change feed ships in a separate follow-up PR. Consumers that depend on context joining will need to wait for that PR or tolerate "unknown node" fallback behavior.
- Node identity on the wire (`node_id`, `location`) is sourced from Collectd's `ServiceParameters` keys `node-id` and `location`. If Collectd does not populate these keys in a given deployment, records land with `node_id=0` / `location=""`; consumers must still be able to join via the `deltav-node-context` GlobalKTable to resolve identity.

## Troubleshooting

**Images not found:** Run `./build.sh` to build all images. Verify with `docker images | grep opennms`.

**OOM kills (exit 137):** Increase Docker Desktop memory or use `./deploy.sh up lite`.

**Service won't start:** Check logs: `./deploy.sh logs <service>`. Spring Boot daemons log to stdout. Check `/actuator/health` for health status.

**Database connection errors:** Ensure postgres is healthy before other services start. The compose healthchecks handle this, but initial schema creation takes time.

**Stale data after rebuild:** Run `./deploy.sh reset` to remove all volumes, then `./deploy.sh up`.

## License

AGPL v3 — see [LICENSE.md](../../LICENSE.md)
