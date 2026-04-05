# OpenNMS Delta-V

**Composable, containerized deployment of OpenNMS Horizon.**

Delta-V decomposes the monolithic OpenNMS into 16 independently scalable services connected by Kafka and PostgreSQL. Each daemon runs in its own container as a Spring Boot fat JAR, communicating via Kafka event topics. There is no core container — schema migration is handled by a one-shot db-init container, and the webapp serves only the Web UI and REST API.

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

| Service          | Image                          | Purpose                                       | Host Port |
|------------------|--------------------------------|-----------------------------------------------|-----------|
| postgres         | postgres:15                    | Shared database                               | 5432      |
| kafka            | apache/kafka                   | Event bus (KRaft mode)                        | 19092     |
| db-init          | opennms/db-init                | One-shot schema migration (exits after init)  | —         |
| minion           | opennms/minion-deltav          | Distributed data collection agent (Karaf)     | —         |
| alarmd           | opennms/daemon-deltav-springboot | Alarm processing (Kafka consumer)           | —         |
| pollerd          | opennms/daemon-deltav-springboot | Service polling via Minion RPC              | —         |
| collectd         | opennms/daemon-deltav-springboot | SNMP data collection via Minion SNMP proxy  | —         |
| discovery        | opennms/daemon-deltav-springboot | Network discovery via Minion RPC            | —         |
| provisiond       | opennms/daemon-deltav-springboot | Node provisioning and detection             | —         |
| trapd            | opennms/daemon-deltav-springboot | SNMP trap reception (Kafka Sink)            | —         |
| syslogd          | opennms/daemon-deltav-springboot | Syslog reception (Kafka Sink)               | —         |
| eventtranslator  | opennms/daemon-deltav-springboot | Event transformation rules                  | —         |
| enlinkd          | opennms/daemon-deltav-springboot | Link discovery (CDP, LLDP, OSPF, IS-IS, Bridge) | —  |
| bsmd             | opennms/daemon-deltav-springboot | Business Service Monitor                    | 8180      |
| perspectivepollerd | opennms/daemon-deltav-springboot | Perspective polling from remote locations | —         |
| telemetryd       | opennms/daemon-deltav-springboot | Telemetry ingestion bridge                  | —         |

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

Web UI: **http://localhost:8980/opennms** (admin / admin)

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

Six end-to-end test suites validate the full pipeline:

```bash
cd opennms-container/delta-v

bash test-collectd-e2e.sh        # SNMP data collection via Minion
bash test-minion-e2e.sh          # Trap → Minion → Kafka → Alarmd lifecycle
bash test-minion-rpc-e2e.sh      # Detector + Monitor via Minion RPC (Phase 3 canary)
bash test-syslog-e2e.sh          # Syslog → Minion → Kafka → Alarmd lifecycle
bash test-passive-e2e.sh         # Passive status via EventTranslator + Pollerd
bash test-enlinkd-e2e.sh         # LLDP/CDP link discovery on Containerlab cEOS
bash test-e2e.sh                 # Full alarm create/clear via SNMP traps
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

## Troubleshooting

**Images not found:** Run `./build.sh` to build all images. Verify with `docker images | grep opennms`.

**OOM kills (exit 137):** Increase Docker Desktop memory or use `./deploy.sh up lite`.

**Service won't start:** Check logs: `./deploy.sh logs <service>`. Spring Boot daemons log to stdout. Check `/actuator/health` for health status.

**Database connection errors:** Ensure postgres is healthy before other services start. The compose healthchecks handle this, but initial schema creation takes time.

**Stale data after rebuild:** Run `./deploy.sh reset` to remove all volumes, then `./deploy.sh up`.

## License

AGPL v3 — see [LICENSE.md](../../LICENSE.md)
