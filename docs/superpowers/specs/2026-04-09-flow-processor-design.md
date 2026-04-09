# Flow Processing Pipeline Design — Nephron Replacement

## Overview

Replace Nephron (Apache Beam/Flink) with two Spring Cloud Stream services that parse, enrich, aggregate, and persist NetFlow/IPFIX/sFlow data. Eliminates the Flink cluster dependency entirely — uses Kafka (already required) and Elasticsearch (newly added) instead.

The architecture creates a public `deltav-flows` Kafka topic as a community API contract. The protobuf `.proto` file is published with the project. Community consumers can build custom processors (Prometheus remote write, ClickHouse sink, custom analytics) against this stable interface.

## Prerequisites

- **Sink topic prefix rename** — `OpenNMS.Sink.*` → `DeltaV.Sink.*` across Minion, Telemetryd, Trapd, Syslogd (separate PR)
- **Elasticsearch cluster** — single-node ES 8.18.2 added to Docker Compose stack (PR #138)
- **Horizon 1.0.7** — published with NMS-19631 ifAlias by ifIndex (done)

## Architecture

```
Minion (field)
  │  Receives NetFlow/IPFIX/sFlow UDP packets
  │  Serializes to protobuf SinkMessage
  │  Publishes to Kafka Sink topics
  ↓
┌─────────────────────────────────────────────────┐
│ Kafka Sink Topics (existing, renamed prefix)    │
│  • DeltaV.Sink.Netflow-5                        │
│  • DeltaV.Sink.Netflow-9                        │
│  • DeltaV.Sink.IPFIX                            │
│  • DeltaV.Sink.SFlow                            │
└───────────────┬─────────────────────────────────┘
                ↓
┌─────────────────────────────────────────────────┐
│ flow-enricher (Spring Cloud Stream, Kafka binder)│
│                                                  │
│  1. Deserialize SinkMessage protobuf             │
│  2. Run protocol adapter (parse to Flow objects) │
│  3. Enrich:                                      │
│     • Node lookup (exporter, src, dst via JDBC)  │
│     • Application classification (decision tree) │
│     • Locality (private/public)                  │
│     • Clock skew correction                      │
│  4. Mark SNMP interfaces (with TTL cache)        │
│  5. Serialize to FlowDocument protobuf           │
│  6. Publish to deltav-flows topic                │
└───────────────┬─────────────────────────────────┘
                ↓
┌─────────────────────────────────────────────────┐
│ deltav-flows topic (PUBLIC CONTRACT)             │
│  • Protobuf FlowDocument format                  │
│  • .proto file published with project            │
│  • Partitioned by exporter node ID               │
└───────┬───────────────────┬─────────────────────┘
        ↓                   ↓
┌───────────────┐   ┌──────────────────────┐
│flow-aggregator│   │ Community consumers   │
│(Spring Cloud  │   │ (Prometheus writer,   │
│ Stream, Kafka │   │  ClickHouse sink,     │
│ Streams binder)│   │  custom analytics)   │
│               │   └──────────────────────┘
│ • Windowed    │
│   aggregation │
│ • Raw persist │
│   (optional)  │
│ • Write to ES │
└───────┬───────┘
        ↓
┌───────────────┐
│ Elasticsearch │
│ 8.18.2        │
│ • netflow_agg │
│ • netflow     │
│   (optional)  │
└───────────────┘
```

### Component Roles

| Component | DB Access | Scales Horizontally | Role |
|-----------|-----------|---------------------|------|
| Telemetryd | Yes (existing) | No (single instance, lightweight) | Kafka Sink bridge — unchanged |
| flow-enricher | Yes (PostgreSQL, read + interface marking) | Yes (Kafka consumer group) | Parse, enrich, classify, publish |
| flow-aggregator | No | Yes (Kafka Streams partition-aware state) | Windowed aggregation → ES |
| Community consumers | No | N/A | Custom processing of enriched flows |

### Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Two services vs one | Two (enricher + aggregator) | Public `deltav-flows` topic enables community consumers; DB access isolated to enricher |
| Spring Cloud Stream | Kafka binder (enricher), Kafka Streams binder (aggregator) | Eliminates Flink cluster; uses Kafka already in the stack |
| Topic format | Protobuf | Community requested; high-volume deployments need compact format |
| Topic name | `deltav-flows` | Avoids collision with OpenNMS Horizon in transitioning orgs |
| Raw ES persistence | Configurable, default off | High-volume users don't want raw storage; topic serves as replay log |
| Interface marking | In flow-enricher with TTL cache | Enricher already has DB access; avoids DB dependency in aggregator/community |
| Configuration | `application.yml` with env var overrides | Jackson-native, no XML/JAXB |
| Modules | Two separate Maven modules under `core/` | Independent deployment, independent scaling, failure isolation |

## flow-enricher Service

**Module:** `core/flow-enricher/`
**Package:** `org.deltav.flows.enricher`
**Image:** `opennms/flow-enricher:${VERSION}`

### Spring Cloud Stream Bindings

```yaml
spring:
  cloud:
    stream:
      bindings:
        netflow5-in-0:
          destination: DeltaV.Sink.Netflow-5
          group: deltav-flow-enricher
        netflow9-in-0:
          destination: DeltaV.Sink.Netflow-9
          group: deltav-flow-enricher
        ipfix-in-0:
          destination: DeltaV.Sink.IPFIX
          group: deltav-flow-enricher
        sflow-in-0:
          destination: DeltaV.Sink.SFlow
          group: deltav-flow-enricher
        enriched-out-0:
          destination: deltav-flows
      kafka:
        binder:
          brokers: ${KAFKA_BOOTSTRAP_SERVERS:kafka:9092}
```

Each input binding gets a `Consumer<byte[]>` function that deserializes the SinkMessage protobuf, runs the appropriate protocol adapter, and feeds into the shared enrichment pipeline.

### Enrichment Pipeline

Reuses horizon JAR classes where possible:

1. **Protocol parsing** — `Netflow5Adapter`, `Netflow9Adapter`, `IpfixAdapter`, `SFlowAdapter` (from horizon `features/telemetry/protocols/flows/`)
2. **Node lookup** — JDBC queries against PostgreSQL (same pattern as `JdbcEventUtil` — no Hibernate, no DAO layer)
3. **Application classification** — `DefaultClassificationEngine` loaded from PostgreSQL `classification_rules` table at startup, reloaded via Kafka event
4. **Locality determination** — private/public based on IP address ranges (stateless, no DB)
5. **Clock skew correction** — timestamp adjustment based on received-vs-reported delta
6. **Interface marking** — `UPDATE snmpinterface SET hasFlows=true WHERE nodeid=? AND snmpifindex=?` with in-memory TTL cache to avoid redundant writes

### Interface Marking Cache

```
Flow arrives with (nodeId=5, inputIfIndex=12)
    ↓
Check cache: is (5, 12) already marked?
    ├─ Yes → skip, no DB call
    └─ No  → UPDATE snmpinterface, add (5, 12) to cache with TTL
```

`ConcurrentHashMap<Long, Set<Integer>>` with scheduled cleanup sweep. One DB write per interface per TTL period instead of per-flow.

**Cache invalidation:** The flow-enricher subscribes to `nodeDeleted` events on the Kafka event topic. When a node is deleted, all cache entries for that nodeId are evicted immediately. This prevents stale markings if a node is deleted and its ID reused, or if interfaces change after rediscovery.

### Dependencies

- **Horizon JARs:** protocol adapters, classification engine, Flow model
- **PostgreSQL:** node lookup, classification rules, interface marking (read + write)
- **Kafka:** input (Sink topics) + output (`deltav-flows`)
- **No Elasticsearch dependency**

### Configuration

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/opennms}
    username: ${SPRING_DATASOURCE_USERNAME:opennms}
    password: ${SPRING_DATASOURCE_PASSWORD:opennms}

deltav:
  flows:
    interface-marking:
      cache-ttl: ${DELTAV_FLOWS_INTERFACE_MARKING_CACHE_TTL:24h}
```

## flow-aggregator Service

**Module:** `core/flow-aggregator/`
**Package:** `org.deltav.flows.aggregator`
**Image:** `opennms/flow-aggregator:${VERSION}`

### Spring Cloud Stream Bindings

```yaml
spring:
  cloud:
    stream:
      bindings:
        flows-in-0:
          destination: deltav-flows
          group: deltav-flow-aggregator
      kafka:
        streams:
          binder:
            brokers: ${KAFKA_BOOTSTRAP_SERVERS:kafka:9092}
            configuration:
              application.id: deltav-flow-aggregator
              state.dir: /tmp/kafka-streams
```

Uses the **Kafka Streams binder** (not the plain Kafka binder) for stateful windowed operations with automatic state store management and partition-aware scaling.

### Aggregation Dimensions

Windowed aggregation produces summaries grouped by:

| Dimension | Key Fields | Use Case |
|-----------|-----------|----------|
| APPLICATION | exporter + interface + application name | "Top talkers by app" dashboards |
| HOST | exporter + interface + IP address | "Top talkers by host" dashboards |
| CONVERSATION | exporter + interface + src/dst/app tuple | Drill-down into specific flows |
| DSCP | exporter + interface + DSCP value | QoS analysis |

Each produces `bytes_ingress`, `bytes_egress`, and ranking within the window.

### Windowed Aggregation

```
FlowDocument stream
    ↓
Group by (exporter_node_id, input_snmp_ifindex, dimension_key)
    ↓
Windowed aggregation (tumbling window, configurable size, default 60s)
    ↓
Compute: sum(bytes_in), sum(bytes_out), count, top-K ranking
    ↓
Materialize to Elasticsearch (netflow_agg-YYYY.MM index)
```

**Unaligned windows:** Windows are shifted per exporter node ID to prevent all exporters flushing aggregations simultaneously. This spreads ES write load evenly. (Follows Nephron's proven design.)

### Elasticsearch Persistence

| Index | Content | Default |
|-------|---------|---------|
| `netflow_agg-YYYY.MM` | Windowed aggregation summaries | Always on |
| `netflow-YYYY.MM` | Raw enriched flow documents | Off (configurable) |

Uses the official Elasticsearch Java client (`co.elastic.clients:elasticsearch-java`) for ES 8.x — no Jest, no Drift plugin, no deprecated HLRC, no horizon ES dependencies. Bulk indexing with configurable flush size and interval.

### Dependencies

- **Kafka:** input (`deltav-flows`) + Kafka Streams state stores
- **Elasticsearch:** output (aggregated + optional raw indices)
- **No PostgreSQL** — aggregator has no DB dependency
- **No horizon JARs for persistence** — clean ES REST client

### Configuration

```yaml
deltav:
  flows:
    elasticsearch:
      url: ${ELASTICSEARCH_URL:http://elasticsearch:9200}
      raw-persistence-enabled: ${DELTAV_FLOWS_ELASTICSEARCH_RAW_PERSISTENCE_ENABLED:false}
      index-prefix: netflow
      index-strategy: monthly
      bulk-size: 1000
      flush-interval: 500ms
    aggregation:
      window-size: ${DELTAV_FLOWS_AGGREGATION_WINDOW_SIZE:60s}
      top-k: ${DELTAV_FLOWS_AGGREGATION_TOP_K:10}
      dimensions:
        - APPLICATION
        - HOST
        - CONVERSATION
        - DSCP
```

## Protobuf Contract & Community API

### Proto File

Location: `core/flow-enricher/src/main/proto/deltav-flows.proto`

Based on the existing horizon `FlowDocument` protobuf schema with package renamed to `org.deltav.flows`. Key fields:

| Field | Type | Description |
|-------|------|-------------|
| `timestamp` | uint64 | Flow timestamp (epoch ms) |
| `num_bytes` | uint64 | Bytes transferred |
| `num_packets` | uint64 | Packets transferred |
| `direction` | enum | INGRESS / EGRESS |
| `src_address` | string | Source IP |
| `dst_address` | string | Destination IP |
| `src_port` | uint32 | Source port |
| `dst_port` | uint32 | Destination port |
| `protocol` | uint32 | IP protocol (6=TCP, 17=UDP) |
| `application` | string | Classified application name |
| `exporter_node` | NodeInfo | Exporter node metadata |
| `input_snmp_ifindex` | uint32 | Input interface index |
| `output_snmp_ifindex` | uint32 | Output interface index |
| `src_locality` | enum | PRIVATE / PUBLIC |
| `dst_locality` | enum | PRIVATE / PUBLIC |
| `dscp` | uint32 | DSCP value |
| `netflow_version` | enum | V5 / V9 / IPFIX / SFLOW |
| `sampling_interval` | double | Sampling rate |

### Versioning Policy

- **Additive changes** (new fields) — backward compatible, no version bump needed
- **Breaking changes** (rename/remove fields, change types) — require major version bump and migration period with both old and new topics
- The `.proto` file includes a `// API version: 1` comment at the top

### Community Documentation

A `docs/flows/community-consumer-guide.md` ships with the project explaining:
- Topic name and format
- How to generate client code from the `.proto` file
- Example consumers in Java and Python
- Partition key semantics (exporter node ID)

## Docker Compose Integration

```yaml
flow-enricher:
  profiles: [full]
  image: ${IMAGE_PREFIX:-opennms}/flow-enricher:${VERSION}
  container_name: delta-v-flow-enricher
  hostname: flow-enricher
  depends_on:
    db-init:
      condition: service_completed_successfully
    kafka:
      condition: service_healthy
  environment:
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/opennms
    SPRING_DATASOURCE_USERNAME: opennms
    SPRING_DATASOURCE_PASSWORD: opennms
    KAFKA_BOOTSTRAP_SERVERS: kafka:9092
  healthcheck:
    test: ["CMD", "curl", "-sf", "http://localhost:8080/actuator/health"]
    interval: 10s
    timeout: 5s
    retries: 12
    start_period: 20s

flow-aggregator:
  profiles: [full]
  image: ${IMAGE_PREFIX:-opennms}/flow-aggregator:${VERSION}
  container_name: delta-v-flow-aggregator
  hostname: flow-aggregator
  depends_on:
    kafka:
      condition: service_healthy
    elasticsearch:
      condition: service_healthy
  environment:
    KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    ELASTICSEARCH_URL: http://elasticsearch:9200
  healthcheck:
    test: ["CMD", "curl", "-sf", "http://localhost:8080/actuator/health"]
    interval: 10s
    timeout: 5s
    retries: 12
    start_period: 20s
```

### Build Integration

Both modules follow the existing daemon build pattern:
- `make build` compiles them as part of the reactor
- `build.sh deltav` stages their JARs and builds per-service Docker images
- Same `Dockerfile.daemon-per` template as existing daemons

## E2E Testing

### Test Script

New script: `opennms-container/delta-v/test-flows-e2e.sh`

Uses a `softflowd` container to generate deterministic NetFlow traffic through Minion:

```yaml
flow-generator:
  profiles: [test]
  image: opennms/flow-generator:${VERSION}  # lightweight image with softflowd
  environment:
    FLOW_TARGET: minion:4729   # Minion NetFlow-9 port
    FLOW_PROTOCOL: netflow9
  depends_on:
    minion:
      condition: service_healthy
```

The `flow-generator` image is a minimal Alpine container with `softflowd` installed, built as part of `build.sh deltav`. Exact image definition determined during Phase 3 implementation.

**Template frequency:** NetFlow v9 and IPFIX are template-based — Minion must receive a Template FlowSet before decoding Data FlowSets. Templates are cached in Minion memory and lost on restart. Configure `softflowd` to send templates every 60 seconds. The E2E test should allow time for template receipt before expecting flow data.

### Test Flow

1. Start `softflowd` flow generator targeting Minion
2. Wait for enriched flows on `deltav-flows` topic (verify protobuf deserializable)
3. Verify enrichment: `application` field populated, `exporter_node` resolved
4. Wait for aggregated documents in `netflow_agg-*` ES index
5. Verify aggregation dimensions present (APPLICATION, HOST)
6. If raw persistence enabled, verify `netflow-*` index populated

## Implementation Phases

### Phase 1: flow-enricher MVP

**Delivers:** Raw flows parsed, enriched, and published to `deltav-flows`.

- Maven module `core/flow-enricher/` with Spring Cloud Stream + Kafka binder
- Consume from 4 Sink topics (Netflow-5/9, IPFIX, sFlow)
- Protocol parsing via horizon adapter JARs
- Node lookup via JDBC
- Application classification via `DefaultClassificationEngine` from PostgreSQL
- Locality determination + clock skew correction
- Interface marking with configurable TTL cache
- Publish enriched FlowDocument protobuf to `deltav-flows`
- Docker image + compose service
- `.proto` file committed to repo

**Verification:** Consume `deltav-flows` with `kafka-console-consumer`, confirm enriched protobuf messages appear when Minion forwards flow traffic.

### Phase 2: flow-aggregator + ES persistence

**Delivers:** Windowed aggregation written to Elasticsearch.

- Maven module `core/flow-aggregator/` with Spring Cloud Stream + Kafka Streams binder
- Consume from `deltav-flows`
- Windowed aggregation (APPLICATION, HOST, CONVERSATION, DSCP)
- Unaligned windows per exporter
- ES bulk indexing to `netflow_agg-YYYY.MM`
- Configurable raw flow persistence to `netflow-YYYY.MM`
- ES index templates committed to repo
- Docker image + compose service

**Verification:** Trigger flow traffic via Minion, confirm aggregated documents in Elasticsearch. Query via `curl http://localhost:9200/netflow_agg-*/_search`.

### Phase 3: E2E testing + community documentation

**Delivers:** Production-ready with documentation.

- `test-flows-e2e.sh` with `softflowd` container
- `docs/flows/community-consumer-guide.md` with proto usage examples
- Example consumer (Python or Go) that reads `deltav-flows` and prints summaries
- Performance tuning documentation (partition count, consumer scaling, ES bulk settings)

## What This Replaces

| Component | Status |
|-----------|--------|
| Nephron (Apache Beam/Flink) | **Eliminated** — no Flink cluster needed |
| ElasticFlowRepository (horizon) | **Replaced** — flow-aggregator writes directly via ES REST client |
| KafkaFlowForwarder (horizon) | **Replaced** — flow-enricher publishes to `deltav-flows` |
| DocumentEnricherImpl (horizon) | **Rewritten** — JDBC-based enrichment in flow-enricher (no Hibernate/DAO) |
| AggregatedFlowQueryService (horizon) | **Future** — REST API for querying aggregated data (separate service or added to flow-aggregator later) |

## Future Considerations (Out of Scope)

| Item | Description |
|------|-------------|
| Custom field pass-through | Add `map<string, string> custom_fields` to FlowDocument proto for vendor-specific fields (Palo Alto, Cisco extensions). Wait for community feedback before designing. Additive proto change — non-breaking. |
| Minion template persistence | Persist NetFlow v9/IPFIX templates to Kafka so Minion survives restarts without waiting for template retransmission. Requires Minion-level change in delta-v-horizon. |
| Streaming telemetry protocols | JTI, OpenConfig, NXOS, BMP, Graphite — separate services with different data models and persistence paths. |
| Flow query REST API | REST endpoint for querying aggregated flow data from ES. Either added to flow-aggregator or a separate query service. |
| Grafana integration | Grafana dashboards querying ES indices directly or via the flow query API. |
