# Flow Processing Pipeline Design — Nephron Replacement

## Overview

Replace Nephron (Apache Beam/Flink) with a single Spring Cloud Stream service that parses and enriches NetFlow/IPFIX/sFlow data, plus a ClickHouse container that persists and continuously aggregates the enriched flows using native materialized views. Eliminates the Flink cluster dependency entirely — uses Kafka (already required) and a new ClickHouse container in place of Nephron and the prior Elasticsearch plan.

The architecture creates a public `deltav-flows` Kafka topic as a community API contract. The protobuf `.proto` file is published with the project. Community consumers can build custom processors (Prometheus remote write, alternate storage backends, custom analytics) against this stable interface without needing any knowledge of the ClickHouse storage layer.

## Prerequisites

- **Sink topic prefix rename** — `OpenNMS.Sink.*` → `DeltaV.Sink.*` across Minion, Telemetryd, Trapd, Syslogd (separate PR)
- **ClickHouse 25.x LTS** — single-node containerized deployment added to Docker Compose stack (this spec)
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
┌────────────────────┐   ┌──────────────────────┐
│ ClickHouse 25.x    │   │ Community consumers  │
│ (single-node)      │   │ (Prometheus writer,  │
│                    │   │  custom analytics,   │
│ flows_kafka        │   │  alternate storage)  │
│  (Kafka engine,    │   └──────────────────────┘
│   Format=Protobuf) │
│      │             │
│      ↓ MV          │
│ flows_raw          │
│  (MergeTree, 14d)  │
│      │             │
│      ├─ MV: app    │
│      ├─ MV: src_ip │
│      ├─ MV: convo  │
│      └─ MV: dscp   │
│   (all 90d)        │
└────────────────────┘
```

### Component Roles

| Component | DB Access | Scales Horizontally | Role |
|-----------|-----------|---------------------|------|
| Telemetryd | Yes (existing) | No (single instance, lightweight) | Kafka Sink bridge — unchanged |
| flow-enricher | Yes (PostgreSQL, read + interface marking) | Yes (Kafka consumer group) | Parse, enrich, classify, publish |
| ClickHouse | Self-contained | Yes (via ClickHouse Operator on Kubernetes; default ship is single-node) | Ingest, store, and continuously aggregate flows |
| Community consumers | No | N/A | Custom processing of enriched flows |

### Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Java aggregator service vs ClickHouse Kafka engine | ClickHouse Kafka engine | Eliminates an entire Java module; ClickHouse's C++ Kafka consumer with native Protobuf parsing is purpose-built for this workload |
| Storage backend | ClickHouse 25.x | Columnar storage, continuous materialized view aggregation, 10-100x compression over ES for flow data, sub-second query latency |
| Materialized view strategy | `SummingMergeTree` targets, continuous aggregation triggered on every raw insert | ClickHouse-native idiom; user-provided MVs via drop-in `user-init/` directory |
| Topic format | Protobuf | Community requested; high-volume deployments need compact format |
| Topic name | `deltav-flows` | Avoids collision with OpenNMS Horizon in transitioning orgs |
| Raw flow persistence | Always on (14d default TTL) | ClickHouse handles raw flow volume efficiently; raw table is the source of all MV derivations |
| Interface marking | In flow-enricher with TTL cache | Enricher already has DB access; avoids DB dependency elsewhere |
| Configuration | `application.yml` (enricher) + env-var-templated SQL (ClickHouse) | Jackson-native on the enricher; `sed`-based placeholder substitution on DDL |
| Modules | One Spring Boot Maven module + SQL files under `opennms-container/delta-v/clickhouse/` | No second Java service; Phase 2 is all SQL |

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
- **No ClickHouse dependency** — the enricher publishes to Kafka; ClickHouse is a downstream consumer

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

## ClickHouse Persistence

The Phase 2 persistence layer is entirely ClickHouse-native: a `Kafka` engine table polls `deltav-flows` directly using ClickHouse's built-in protobuf consumer, a bridge materialized view moves rows into a `MergeTree` raw table, and four dimension materialized views continuously aggregate the raw data via `SummingMergeTree`. There is no Java service in this layer — the entire Phase 2 deliverable is a set of SQL files, a ClickHouse container in Compose, and a short bash init runner.

### Database

```sql
CREATE DATABASE IF NOT EXISTS deltav;
```

### Raw table: `flows_raw`

The raw table carries every field from the `deltav-flows.proto` contract — all 45 non-reserved top-level fields, with the three nested `NodeInfo` messages (`src_node`, `exporter_node`, `dest_node`) flattened into per-prefix columns. Partitioned by day for efficient TTL drops, and sorted by `(exporter_node_id, timestamp, input_snmp_ifindex)` so that the dominant query pattern ("flows from this exporter in this time range") is a contiguous disk read.

```sql
CREATE TABLE IF NOT EXISTS deltav.flows_raw
(
    -- Timing and identity
    timestamp              DateTime64(3, 'UTC'),
    netflow_version        LowCardinality(String),
    direction              LowCardinality(String),
    sampling_algorithm     LowCardinality(String),
    sampling_interval      Nullable(Float64),
    clock_correction       UInt64,

    -- Volume and flow lifetime
    num_bytes              Nullable(UInt64),
    num_packets            Nullable(UInt64),
    num_flow_records       Nullable(UInt32),
    first_switched         Nullable(UInt64),
    last_switched          Nullable(UInt64),
    delta_switched         Nullable(UInt64),
    flow_seq_num           Nullable(UInt64),

    -- Source L3/L4
    src_address            IPv6,
    src_hostname           String,
    src_port               Nullable(UInt16),
    src_as                 Nullable(UInt64),
    src_mask_len           Nullable(UInt8),

    -- Destination L3/L4
    dst_address            IPv6,
    dst_hostname           String,
    dst_port               Nullable(UInt16),
    dst_as                 Nullable(UInt64),
    dst_mask_len           Nullable(UInt8),

    -- Next-hop (often absent in sFlow)
    next_hop_address       Nullable(IPv6),
    next_hop_hostname      String,

    -- Protocol / QoS / TCP
    protocol               Nullable(UInt8),
    ip_protocol_version    Nullable(UInt8),
    tcp_flags              Nullable(UInt32),
    tos                    Nullable(UInt8),
    dscp                   Nullable(UInt8),
    ecn                    Nullable(UInt8),
    vlan                   LowCardinality(String),

    -- Locality
    src_locality           LowCardinality(String),
    dst_locality           LowCardinality(String),
    flow_locality          LowCardinality(String),

    -- Classification
    application            LowCardinality(String),

    -- Exporter display metadata
    host                   String,
    location               LowCardinality(String),
    engine_id              Nullable(UInt32),
    engine_type            Nullable(UInt32),

    -- Exporter node inventory (from NodeInfo exporter_node)
    -- UInt32 with 0 = unknown for sort-key compatibility
    exporter_node_id               UInt32,
    exporter_node_foreign_source   LowCardinality(String),
    exporter_node_foreign_id       String,
    exporter_node_categories       Array(LowCardinality(String)),
    input_snmp_ifindex             UInt32,
    output_snmp_ifindex            Nullable(UInt32),

    -- Source node inventory (from NodeInfo src_node)
    src_node_id                    UInt32,
    src_node_foreign_source        LowCardinality(String),
    src_node_foreign_id            String,
    src_node_categories            Array(LowCardinality(String)),

    -- Destination node inventory (from NodeInfo dest_node)
    dest_node_id                   UInt32,
    dest_node_foreign_source       LowCardinality(String),
    dest_node_foreign_id           String,
    dest_node_categories           Array(LowCardinality(String))
)
ENGINE = MergeTree
PARTITION BY toDate(timestamp)
ORDER BY (exporter_node_id, timestamp, input_snmp_ifindex)
TTL timestamp + INTERVAL ${DELTAV_CLICKHOUSE_FLOWS_RAW_TTL_DAYS} DAY DELETE
SETTINGS index_granularity = 8192;
```

#### Type choices explained

| Field | Type | Why |
|-------|------|-----|
| `timestamp` | `DateTime64(3, 'UTC')` | Millisecond precision matches protobuf; explicit UTC avoids server-timezone surprises |
| IP addresses | `IPv6` | 16 bytes fixed, enables `isIPAddressInRange()`; IPv4 auto-maps as `::ffff:a.b.c.d` |
| `next_hop_address` | `Nullable(IPv6)` | Legitimately absent in many flow types; null carries the "not reported" semantic cleanly |
| Enum fields (direction, locality, version, sampling_algorithm) | `LowCardinality(String)` | Stores enum names dictionary-encoded; no `ALTER TABLE` needed when new values arrive |
| `application`, `node_foreign_source`, `location`, `vlan` | `LowCardinality(String)` | Finite sets with heavy repetition — compress to roughly 1-2 bytes per row |
| Node categories | `Array(LowCardinality(String))` | Dictionary-encoded per-element; supports `has(categories, 'edge')` and similar predicates |
| Wrapper fields (ports, AS, mask_len, engine_id, etc.) | `Nullable(T)` | Preserves proto `google.protobuf.*Value` "unset vs zero" semantic; ClickHouse Nullable uses a single-bit null mask with minimal overhead |
| `exporter_node_id`, `input_snmp_ifindex` | `UInt32` (not Nullable), 0=unknown | Columns in `ORDER BY` cannot be Nullable; the ingestion MV substitutes 0 when the proto field is absent |
| `src_node_id`, `dest_node_id` | `UInt32`, 0=unknown | Consistent with exporter treatment for query-side predictability (`WHERE dest_node_id = 0` → "unknown destination node") |
| `src_port`, `dst_port` | `Nullable(UInt16)` | Port range is 0-65535; narrower type saves disk. Nullable handles unreported ports. |
| `protocol`, `dscp`, `ecn`, `tos`, `mask_len` | `Nullable(UInt8)` | These fit in a byte per RFC; columnar compression benefits from tight types |
| `clock_correction` | `UInt64` | Plain `uint64` at proto tag 45, carried through as-is |

The `${DELTAV_CLICKHOUSE_FLOWS_RAW_TTL_DAYS}` placeholder is substituted by the init sidecar's `sed` pass (default `14`).

**Why the sort order matters:** ClickHouse's `MergeTree` stores data physically sorted by `ORDER BY` inside each part, and its sparse index is built on it. A query like `WHERE exporter_node_id = 42 AND timestamp BETWEEN ? AND ?` becomes a single sequential disk read of a small range of rows. Putting `exporter_node_id` first is the right call because every meaningful flow query filters by exporter (operators think about flows as "what's happening on this router"), and the cardinality is manageable. Putting `timestamp` second lets the same query efficiently narrow by time. An alternate sort like `(timestamp, exporter_node_id, ...)` would make per-exporter queries scan the full time range — typically 10-100x slower.

### Kafka engine table: `flows_kafka`

The Kafka engine table is a virtual consumer of the `deltav-flows` topic. Selecting from it consumes messages (so production code never queries it directly), but a materialized view reading from it triggers on every new message. Column names and types mirror the proto wire format exactly, with `Nullable(T)` for every `google.protobuf.*Value` wrapper, plain scalars for unwrapped fields, `String` for every enum field (ClickHouse's Protobuf format writes the enum NAME when the target column is String), and `Tuple` columns for the three nested NodeInfo messages.

```sql
CREATE TABLE IF NOT EXISTS deltav.flows_kafka
(
    -- Timing and identity
    timestamp               UInt64,                     -- proto: uint64 (epoch ms)
    netflow_version         String,                     -- proto: NetflowVersion enum
    direction               String,                     -- proto: Direction enum
    sampling_algorithm      String,                     -- proto: SamplingAlgorithm enum
    sampling_interval       Nullable(Float64),          -- proto: DoubleValue
    clock_correction        UInt64,

    -- Volume and flow lifetime
    num_bytes               Nullable(UInt64),
    num_packets             Nullable(UInt64),
    num_flow_records        Nullable(UInt32),
    first_switched          Nullable(UInt64),
    last_switched           Nullable(UInt64),
    delta_switched          Nullable(UInt64),
    flow_seq_num            Nullable(UInt64),

    -- Source L3/L4
    src_address             String,                     -- converted to IPv6 in ingest MV
    src_hostname            String,
    src_port                Nullable(UInt32),           -- downcast to UInt16 in MV
    src_as                  Nullable(UInt64),
    src_mask_len            Nullable(UInt32),           -- downcast to UInt8 in MV

    -- Destination L3/L4
    dst_address             String,
    dst_hostname            String,
    dst_port                Nullable(UInt32),
    dst_as                  Nullable(UInt64),
    dst_mask_len            Nullable(UInt32),

    -- Next-hop
    next_hop_address        String,                     -- converted to Nullable(IPv6) in MV
    next_hop_hostname       String,

    -- Protocol / QoS / TCP
    protocol                Nullable(UInt32),           -- downcast to UInt8 in MV
    ip_protocol_version     Nullable(UInt32),
    tcp_flags               Nullable(UInt32),
    tos                     Nullable(UInt32),
    dscp                    Nullable(UInt32),
    ecn                     Nullable(UInt32),
    vlan                    String,

    -- Locality
    src_locality            String,                     -- Locality enum
    dst_locality            String,
    flow_locality           String,

    -- Classification
    application             String,

    -- Exporter display metadata
    host                    String,
    location                String,
    engine_id               Nullable(UInt32),
    engine_type             Nullable(UInt32),

    -- Exporter/src/dest NodeInfo (as Tuple, name-matched to proto)
    src_node                Tuple(
        node_id             UInt32,
        foreign_source      String,
        foreign_id          String,
        categories          Array(String)
    ),
    exporter_node           Tuple(
        node_id             UInt32,
        foreign_source      String,
        foreign_id          String,
        categories          Array(String)
    ),
    dest_node               Tuple(
        node_id             UInt32,
        foreign_source      String,
        foreign_id          String,
        categories          Array(String)
    ),

    -- SNMP ifindex lives at the top level of the proto, not in NodeInfo
    input_snmp_ifindex      Nullable(UInt32),
    output_snmp_ifindex     Nullable(UInt32)
)
ENGINE = Kafka
SETTINGS
    kafka_broker_list          = 'kafka:9092',
    kafka_topic_list           = 'deltav-flows',
    kafka_group_name           = 'deltav-clickhouse-persister',
    kafka_format               = 'Protobuf',
    kafka_schema               = 'deltav-flows.proto:FlowDocument',
    kafka_num_consumers        = 2,
    kafka_max_block_size       = 65536,
    kafka_skip_broken_messages = 100;
```

The `kafka_num_consumers = 2` setting creates two parallel consumer threads inside ClickHouse; scaling beyond that is a matter of running additional ClickHouse replicas (each with its own consumer group member). `kafka_skip_broken_messages = 100` tolerates up to 100 malformed messages per block without halting the consumer, which protects against a single corrupt message wedging the entire persister.

### Bridge materialized view: `flows_ingest`

This is the MV that does the actual ingestion: it reads from `flows_kafka` and inserts into `flows_raw`, normalizing types and flattening the nested node tuples. Every new protobuf message that arrives on `deltav-flows` triggers this SELECT, and the result row lands in `flows_raw` — which in turn fires the four dimension MVs.

```sql
CREATE MATERIALIZED VIEW IF NOT EXISTS deltav.flows_ingest
TO deltav.flows_raw AS
SELECT
    toDateTime64(timestamp / 1000.0, 3, 'UTC')      AS timestamp,
    netflow_version,
    direction,
    sampling_algorithm,
    sampling_interval,
    clock_correction,

    num_bytes,
    num_packets,
    num_flow_records,
    first_switched,
    last_switched,
    delta_switched,
    flow_seq_num,

    toIPv6(src_address)                             AS src_address,
    src_hostname,
    CAST(src_port AS Nullable(UInt16))              AS src_port,
    src_as,
    CAST(src_mask_len AS Nullable(UInt8))           AS src_mask_len,

    toIPv6(dst_address)                             AS dst_address,
    dst_hostname,
    CAST(dst_port AS Nullable(UInt16))              AS dst_port,
    dst_as,
    CAST(dst_mask_len AS Nullable(UInt8))           AS dst_mask_len,

    toIPv6OrNull(next_hop_address)                  AS next_hop_address,
    next_hop_hostname,

    CAST(protocol AS Nullable(UInt8))               AS protocol,
    CAST(ip_protocol_version AS Nullable(UInt8))    AS ip_protocol_version,
    tcp_flags,
    CAST(tos AS Nullable(UInt8))                    AS tos,
    CAST(dscp AS Nullable(UInt8))                   AS dscp,
    CAST(ecn AS Nullable(UInt8))                    AS ecn,
    vlan,

    src_locality,
    dst_locality,
    flow_locality,

    application,
    host,
    location,
    engine_id,
    engine_type,

    -- Exporter node: tuple flatten + ifNull for sort-key compatibility
    ifNull(exporter_node.node_id, 0)                AS exporter_node_id,
    exporter_node.foreign_source                    AS exporter_node_foreign_source,
    exporter_node.foreign_id                        AS exporter_node_foreign_id,
    exporter_node.categories                        AS exporter_node_categories,
    ifNull(input_snmp_ifindex, 0)                   AS input_snmp_ifindex,
    output_snmp_ifindex,

    -- Source node
    ifNull(src_node.node_id, 0)                     AS src_node_id,
    src_node.foreign_source                         AS src_node_foreign_source,
    src_node.foreign_id                             AS src_node_foreign_id,
    src_node.categories                             AS src_node_categories,

    -- Destination node
    ifNull(dest_node.node_id, 0)                    AS dest_node_id,
    dest_node.foreign_source                        AS dest_node_foreign_source,
    dest_node.foreign_id                            AS dest_node_foreign_id,
    dest_node.categories                            AS dest_node_categories
FROM deltav.flows_kafka;
```

Notes on the transformations:

- **Timestamp**: `timestamp / 1000.0` converts protobuf's millisecond epoch into ClickHouse's `DateTime64(3)` seconds-plus-fraction.
- **IP addresses**: `toIPv6` is used for the always-present `src_address` and `dst_address` (a missing value here indicates a malformed flow and should trip `kafka_skip_broken_messages`); `toIPv6OrNull` is used for `next_hop_address` because it is legitimately absent in many flow types (sFlow in particular rarely populates it).
- **Nullable type narrowing**: `CAST(x AS Nullable(UInt16))` and `CAST(x AS Nullable(UInt8))` safely narrow `Nullable(UInt32)` to tighter types, passing nulls through unchanged. This is more efficient and readable than the older `toUInt16OrNull(toString(x))` idiom.
- **Tuple flattening**: `exporter_node.node_id`, `exporter_node.foreign_source`, etc., access NodeInfo message fields by name via ClickHouse's Tuple-element syntax.
- **Sort-key nullability**: `ifNull(exporter_node.node_id, 0)` and `ifNull(input_snmp_ifindex, 0)` substitute `0` as the "unknown" sentinel because ClickHouse does not allow `Nullable` columns in a `MergeTree` primary key. Queries distinguish "known node X" from "unknown" via `WHERE exporter_node_id = 0`.

### Default dimension materialized views

Four default materialized views ship as `.sql` files under `init/`. Each consists of a `SummingMergeTree` target table and the matching MV that GROUPs + INSERTs into it. The `SummingMergeTree` engine automatically sums every non-key numeric column during background merges, so the query interface is plain `sum()` / `sumIf()` — much friendlier than the `sumMerge` / `AggregatingMergeTree` syntax.

All four MVs share the same measure columns, defined consistently by the flow's `direction` field:

| Measure | Definition | Interpretation |
|---------|------------|----------------|
| `bytes_in` | `sumIf(num_bytes, direction = 'INGRESS')` | Bandwidth entering the exporter on this dimension key |
| `bytes_out` | `sumIf(num_bytes, direction = 'EGRESS')` | Bandwidth leaving the exporter on this dimension key |
| `packets_in` | `sumIf(num_packets, direction = 'INGRESS')` | Packet count entering |
| `packets_out` | `sumIf(num_packets, direction = 'EGRESS')` | Packet count leaving |
| `flow_count` | `count()` | Number of flow records contributing to this bucket |

#### `flows_by_application_1m` — top talkers by classified application

```sql
CREATE TABLE IF NOT EXISTS deltav.flows_by_application_1m
(
    t_minute           DateTime('UTC'),
    exporter_node_id   UInt32,
    input_snmp_ifindex UInt32,
    application        LowCardinality(String),
    bytes_in           UInt64,
    bytes_out          UInt64,
    packets_in         UInt64,
    packets_out        UInt64,
    flow_count         UInt64
)
ENGINE = SummingMergeTree
PARTITION BY toYYYYMM(t_minute)
ORDER BY (exporter_node_id, t_minute, input_snmp_ifindex, application)
TTL t_minute + INTERVAL ${DELTAV_CLICKHOUSE_FLOWS_AGG_TTL_DAYS} DAY DELETE;

CREATE MATERIALIZED VIEW IF NOT EXISTS deltav.flows_by_application_1m_mv
TO deltav.flows_by_application_1m AS
SELECT
    toStartOfMinute(timestamp)                      AS t_minute,
    exporter_node_id,
    input_snmp_ifindex,
    application,
    sumIf(num_bytes,   direction = 'INGRESS')       AS bytes_in,
    sumIf(num_bytes,   direction = 'EGRESS')        AS bytes_out,
    sumIf(num_packets, direction = 'INGRESS')       AS packets_in,
    sumIf(num_packets, direction = 'EGRESS')        AS packets_out,
    count()                                         AS flow_count
FROM deltav.flows_raw
GROUP BY t_minute, exporter_node_id, input_snmp_ifindex, application;
```

**Example query — "top 10 applications on router 42 in the last hour":**

```sql
SELECT application,
       sum(bytes_in + bytes_out) AS total_bytes
FROM deltav.flows_by_application_1m
WHERE exporter_node_id = 42
  AND t_minute >= now() - INTERVAL 1 HOUR
GROUP BY application
ORDER BY total_bytes DESC
LIMIT 10;
```

#### `flows_by_source_ip_1m` — top talkers by source IP

Named "source_ip" rather than "host" to accurately reflect its semantics: this is a "Top Sources" aggregation. NetFlow and IPFIX exporters typically report unidirectional flows, so a key on `src_address` captures "who is generating traffic through this exporter." A true bidirectional "host" view would require an unpivoted union of src and dst; users who want it can add one to `user-init/`. Structurally identical to `flows_by_application_1m` except the dimension key is `src_address` instead of `application`.

```sql
CREATE TABLE IF NOT EXISTS deltav.flows_by_source_ip_1m
(
    t_minute           DateTime('UTC'),
    exporter_node_id   UInt32,
    input_snmp_ifindex UInt32,
    src_address        IPv6,
    bytes_in           UInt64,
    bytes_out          UInt64,
    packets_in         UInt64,
    packets_out        UInt64,
    flow_count         UInt64
)
ENGINE = SummingMergeTree
PARTITION BY toYYYYMM(t_minute)
ORDER BY (exporter_node_id, t_minute, input_snmp_ifindex, src_address)
TTL t_minute + INTERVAL ${DELTAV_CLICKHOUSE_FLOWS_AGG_TTL_DAYS} DAY DELETE;

CREATE MATERIALIZED VIEW IF NOT EXISTS deltav.flows_by_source_ip_1m_mv
TO deltav.flows_by_source_ip_1m AS
SELECT
    toStartOfMinute(timestamp)                      AS t_minute,
    exporter_node_id,
    input_snmp_ifindex,
    src_address,
    sumIf(num_bytes,   direction = 'INGRESS')       AS bytes_in,
    sumIf(num_bytes,   direction = 'EGRESS')        AS bytes_out,
    sumIf(num_packets, direction = 'INGRESS')       AS packets_in,
    sumIf(num_packets, direction = 'EGRESS')        AS packets_out,
    count()                                         AS flow_count
FROM deltav.flows_raw
GROUP BY t_minute, exporter_node_id, input_snmp_ifindex, src_address;
```

#### `flows_by_conversation_1m` — finest drill-down

Keys on (src, dst, application) so you can isolate specific conversations. This is the highest-cardinality of the four MVs — a full conversation tuple produces many more distinct keys per minute than the top-talker dimensions — but `LowCardinality(String)` on `application` and the 90-day TTL keep storage manageable.

```sql
CREATE TABLE IF NOT EXISTS deltav.flows_by_conversation_1m
(
    t_minute           DateTime('UTC'),
    exporter_node_id   UInt32,
    input_snmp_ifindex UInt32,
    src_address        IPv6,
    dst_address        IPv6,
    application        LowCardinality(String),
    bytes_in           UInt64,
    bytes_out          UInt64,
    packets_in         UInt64,
    packets_out        UInt64,
    flow_count         UInt64
)
ENGINE = SummingMergeTree
PARTITION BY toYYYYMM(t_minute)
ORDER BY (exporter_node_id, t_minute, input_snmp_ifindex, src_address, dst_address, application)
TTL t_minute + INTERVAL ${DELTAV_CLICKHOUSE_FLOWS_AGG_TTL_DAYS} DAY DELETE;

CREATE MATERIALIZED VIEW IF NOT EXISTS deltav.flows_by_conversation_1m_mv
TO deltav.flows_by_conversation_1m AS
SELECT
    toStartOfMinute(timestamp)                      AS t_minute,
    exporter_node_id,
    input_snmp_ifindex,
    src_address,
    dst_address,
    application,
    sumIf(num_bytes,   direction = 'INGRESS')       AS bytes_in,
    sumIf(num_bytes,   direction = 'EGRESS')        AS bytes_out,
    sumIf(num_packets, direction = 'INGRESS')       AS packets_in,
    sumIf(num_packets, direction = 'EGRESS')        AS packets_out,
    count()                                         AS flow_count
FROM deltav.flows_raw
GROUP BY t_minute, exporter_node_id, input_snmp_ifindex, src_address, dst_address, application;
```

#### `flows_by_dscp_1m` — QoS analysis

Lowest-cardinality dimension; DSCP has at most 64 distinct values per interface. DSCP is stored as plain `UInt8` in the target table with `255` as the "unknown/unreported" sentinel, so that DSCP `0` (Best Effort) remains distinct from "not reported."

```sql
CREATE TABLE IF NOT EXISTS deltav.flows_by_dscp_1m
(
    t_minute           DateTime('UTC'),
    exporter_node_id   UInt32,
    input_snmp_ifindex UInt32,
    dscp               UInt8,            -- 0-63 = real DSCP, 255 = unknown/unreported
    bytes_in           UInt64,
    bytes_out          UInt64,
    packets_in         UInt64,
    packets_out        UInt64,
    flow_count         UInt64
)
ENGINE = SummingMergeTree
PARTITION BY toYYYYMM(t_minute)
ORDER BY (exporter_node_id, t_minute, input_snmp_ifindex, dscp)
TTL t_minute + INTERVAL ${DELTAV_CLICKHOUSE_FLOWS_AGG_TTL_DAYS} DAY DELETE;

CREATE MATERIALIZED VIEW IF NOT EXISTS deltav.flows_by_dscp_1m_mv
TO deltav.flows_by_dscp_1m AS
SELECT
    toStartOfMinute(timestamp)                      AS t_minute,
    exporter_node_id,
    input_snmp_ifindex,
    ifNull(dscp, 255)                               AS dscp,
    sumIf(num_bytes,   direction = 'INGRESS')       AS bytes_in,
    sumIf(num_bytes,   direction = 'EGRESS')        AS bytes_out,
    sumIf(num_packets, direction = 'INGRESS')       AS packets_in,
    sumIf(num_packets, direction = 'EGRESS')        AS packets_out,
    count()                                         AS flow_count
FROM deltav.flows_raw
GROUP BY t_minute, exporter_node_id, input_snmp_ifindex, dscp;
```

Dashboards should document `dscp = 255` as "Unknown" in the legend.

### Retention policy

Two env vars control the default TTLs:

```bash
DELTAV_CLICKHOUSE_FLOWS_RAW_TTL_DAYS=14   # raw flows retention
DELTAV_CLICKHOUSE_FLOWS_AGG_TTL_DAYS=90   # materialized view retention
```

**Why 14/90:** The raw table is dominated by tail-end low-value flows (DNS chatter, NTP, random probes) that nobody queries but that fill disk. The aggregated tables are tiny by comparison because high-cardinality tail collapses into summary rows. The usage pattern diverges too: people query raw for recent incidents ("why was this host unreachable 10 minutes ago?") and aggregated for trends ("what applications have been growing over the last month?"). Keeping raw for two weeks and aggregates for three months matches both the cost profile and the query pattern.

Operators who need different retention for specific dimensions can override individual MV files via `user-init/` rather than the global env var. For example, keeping conversations for 365 days while leaving the other three at 90 is a matter of dropping a file like `user-init/30-conversation-ttl-override.sql` with `ALTER TABLE deltav.flows_by_conversation_1m MODIFY TTL t_minute + INTERVAL 365 DAY`.

### Init sidecar and idempotent DDL bootstrap

A dedicated `clickhouse-init` sidecar container runs the DDL on every ClickHouse start. It waits for the main server to be healthy (via `depends_on.condition: service_healthy`), then applies all files from `init/` followed by all files from `user-init/`, in lexical filename order.

#### File layout

```
opennms-container/delta-v/clickhouse/
├── init-runner.sh                   # bash bootstrap script
├── init/
│   ├── 01-database.sql              # CREATE DATABASE IF NOT EXISTS deltav
│   ├── 02-flows-raw.sql             # flows_raw (full proto schema, NodeInfo flattened)
│   ├── 03-flows-kafka.sql           # flows_kafka (Kafka engine, Nullable wrappers)
│   ├── 04-flows-ingest.sql          # MV: flows_kafka -> flows_raw
│   ├── 10-mv-application.sql        # flows_by_application_1m
│   ├── 11-mv-source-ip.sql          # flows_by_source_ip_1m
│   ├── 12-mv-conversation.sql       # flows_by_conversation_1m
│   └── 13-mv-dscp.sql               # flows_by_dscp_1m
└── user-init/
    └── .gitkeep                     # empty by default; users drop overrides here
```

The numeric prefix gap between 04 and 10 is intentional — it leaves room for additional ingestion-layer DDL (dictionaries, helper functions) without renumbering the dimension files.

#### Init runner script

`opennms-container/delta-v/clickhouse/init-runner.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

# 1. Safety check — verify the proto file is actually mounted.
#    Catches "ran compose from wrong dir" with a clear error rather than
#    a cryptic "schema not found" from ClickHouse.
if [[ ! -f /delta-v/proto/deltav-flows.proto ]]; then
    echo "ERROR: deltav-flows.proto not found at /delta-v/proto/deltav-flows.proto" >&2
    echo "       Check that docker compose is being run from opennms-container/delta-v/" >&2
    echo "       (the volume mount '../../core/flow-enricher/src/main/proto' resolves" >&2
    echo "        relative to the compose file's directory)." >&2
    exit 1
fi

echo "[init-runner] found deltav-flows.proto, proceeding with DDL bootstrap"

# 2. Client invocation helper. Uses sed for TTL placeholder substitution
#    because gettext-base (envsubst) is not in the clickhouse-server image.
run_sql() {
    local file="$1"
    echo "[init-runner] applying $file"
    sed -e "s/\${DELTAV_CLICKHOUSE_FLOWS_RAW_TTL_DAYS}/${DELTAV_CLICKHOUSE_FLOWS_RAW_TTL_DAYS}/g" \
        -e "s/\${DELTAV_CLICKHOUSE_FLOWS_AGG_TTL_DAYS}/${DELTAV_CLICKHOUSE_FLOWS_AGG_TTL_DAYS}/g" \
        < "$file" | clickhouse-client \
        --host "${CLICKHOUSE_HOST}" \
        --user "${CLICKHOUSE_USER}" \
        --password "${CLICKHOUSE_PASSWORD}" \
        --multiquery
}

# 3. Apply default DDL in order, then user overrides.
for f in /delta-v/init/*.sql; do
    [[ -e "$f" ]] || continue
    run_sql "$f"
done

for f in /delta-v/user-init/*.sql; do
    [[ -e "$f" ]] || continue
    run_sql "$f"
done

echo "[init-runner] DDL bootstrap complete"
```

**Idempotency comes from the SQL, not the runner.** Every `.sql` file uses `CREATE TABLE IF NOT EXISTS` and `CREATE MATERIALIZED VIEW IF NOT EXISTS`. Re-running the script on every container start is safe because the DDL is a no-op when objects already exist. Changing a view's definition requires an explicit `DROP VIEW IF EXISTS foo; CREATE VIEW foo ...` sequence, typically added as a dated override file under `user-init/` (e.g., `2026-05-15-alter-application-mv.sql`).

## Protobuf Contract & Community API

### Proto File

Location: `core/flow-enricher/src/main/proto/deltav-flows.proto`

The `.proto` file is the public contract and the single source of truth for the wire format. The ClickHouse container mounts this directory directly (read-only) at `/var/lib/clickhouse/format_schemas/`, so the database and the enricher always agree on the exact same schema — zero drift. Key fields on `FlowDocument`:

| Field | Type | Description |
|-------|------|-------------|
| `timestamp` | uint64 | Flow timestamp (epoch ms) |
| `num_bytes` | UInt64Value | Bytes transferred |
| `num_packets` | UInt64Value | Packets transferred |
| `direction` | Direction enum | INGRESS / EGRESS |
| `src_address` | string | Source IP |
| `dst_address` | string | Destination IP |
| `src_port` | UInt32Value | Source port |
| `dst_port` | UInt32Value | Destination port |
| `protocol` | UInt32Value | IP protocol (6=TCP, 17=UDP) |
| `application` | string | Classified application name |
| `exporter_node` | NodeInfo | Exporter node metadata (node_id, foreign_source, foreign_id, categories) |
| `src_node` | NodeInfo | Source node metadata |
| `dest_node` | NodeInfo | Destination node metadata |
| `input_snmp_ifindex` | UInt32Value | Input interface index |
| `output_snmp_ifindex` | UInt32Value | Output interface index |
| `src_locality` | Locality enum | PRIVATE / PUBLIC |
| `dst_locality` | Locality enum | PRIVATE / PUBLIC |
| `flow_locality` | Locality enum | Overall flow locality |
| `dscp` | UInt32Value | DSCP value |
| `netflow_version` | NetflowVersion enum | V5 / V9 / IPFIX / SFLOW |
| `sampling_interval` | DoubleValue | Sampling rate |

The full set of 45 non-reserved top-level fields (including the three nested `NodeInfo` messages) is documented in the `.proto` file itself. Reserved tag numbers 25 and 44 (formerly `src_node_identifier` and `convo_key`) must remain reserved.

### Versioning Policy

- **Additive changes** (new fields at new tag numbers) — backward compatible, no version bump needed
- **Breaking changes** (rename/remove fields, change types, reuse reserved tags) — require major version bump and migration period with both old and new topics
- The `.proto` file includes a `// API version: 1` comment at the top

### Community Documentation

A `docs/flows/community-consumer-guide.md` ships with the project explaining:
- Topic name and format
- How to generate client code from the `.proto` file
- Example consumers in Java, Python, and Go
- Example Grafana connection to the ClickHouse HTTP endpoint with a sample dashboard query
- Partition key semantics (exporter node ID)

## Docker Compose Integration

```yaml
clickhouse:
  profiles: [full]
  image: clickhouse/clickhouse-server:25.8       # latest LTS at implementation time
  container_name: delta-v-clickhouse
  hostname: clickhouse
  restart: unless-stopped
  ulimits:
    nofile: { soft: 262144, hard: 262144 }
  environment:
    CLICKHOUSE_DB: deltav
    CLICKHOUSE_USER: deltav
    CLICKHOUSE_PASSWORD: deltav
    CLICKHOUSE_DEFAULT_ACCESS_MANAGEMENT: 1
    CLICKHOUSE_FORMAT_SCHEMA_PATH: /var/lib/clickhouse/format_schemas
  volumes:
    - clickhouse-data:/var/lib/clickhouse
    - ../../core/flow-enricher/src/main/proto:/var/lib/clickhouse/format_schemas:ro
  ports:
    - "8123:8123"   # HTTP (Grafana, clickhouse-client over HTTP)
    - "9000:9000"   # native TCP (clickhouse-client native protocol)
  depends_on:
    kafka:
      condition: service_healthy
  healthcheck:
    test: ["CMD", "wget", "-qO-", "http://localhost:8123/ping"]
    interval: 10s
    timeout: 5s
    retries: 12
    start_period: 15s

clickhouse-init:
  profiles: [full]
  image: clickhouse/clickhouse-server:25.8       # reused for clickhouse-client binary
  container_name: delta-v-clickhouse-init
  hostname: clickhouse-init
  restart: "no"                                  # runs once and exits
  depends_on:
    clickhouse:
      condition: service_healthy
  environment:
    CLICKHOUSE_HOST: clickhouse
    CLICKHOUSE_USER: deltav
    CLICKHOUSE_PASSWORD: deltav
    DELTAV_CLICKHOUSE_FLOWS_RAW_TTL_DAYS: "14"
    DELTAV_CLICKHOUSE_FLOWS_AGG_TTL_DAYS: "90"
  volumes:
    - ./clickhouse/init:/delta-v/init:ro
    - ./clickhouse/user-init:/delta-v/user-init:ro
    - ./clickhouse/init-runner.sh:/usr/local/bin/init-runner.sh:ro
    - ../../core/flow-enricher/src/main/proto:/delta-v/proto:ro
  entrypoint: ["/bin/bash", "/usr/local/bin/init-runner.sh"]

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

volumes:
  clickhouse-data:
```

**Removed from the Compose stack:** the `elasticsearch` service added in PR #138 is deleted entirely. Nothing currently writes to it, no indices were ever created, and no other delta-v service consumes it — a clean delete with no migration required.

### Build Integration

The flow-enricher module follows the existing daemon build pattern:
- `make build` compiles it as part of the reactor
- `build.sh deltav` stages its JAR and builds `opennms/flow-enricher` via `do_flow_enricher_image()`
- Standalone Docker image (not part of `daemon-base`) because its dependency profile differs fundamentally from the horizon-derived daemons

No build integration is needed for ClickHouse — it uses the upstream `clickhouse/clickhouse-server:25.8` image directly, and the init sidecar reuses the same image to get `clickhouse-client`.

## E2E Testing

### Test Script

The existing `opennms-container/delta-v/test-flows-e2e.sh` uses a `softflowd` container to generate deterministic NetFlow traffic through Minion and asserts on the enriched output. The test harness is unchanged through the enricher layer; only the persistence assertions change from Elasticsearch queries to ClickHouse queries.

```yaml
flow-generator:
  profiles: [test]
  image: opennms/flow-generator:${VERSION}       # lightweight image with softflowd
  environment:
    FLOW_TARGET: minion:4729                     # Minion NetFlow-9 port
    FLOW_PROTOCOL: netflow9
  depends_on:
    minion:
      condition: service_healthy
```

**Template frequency:** NetFlow v9 and IPFIX are template-based — Minion must receive a Template FlowSet before decoding Data FlowSets. Templates are cached in Minion memory and lost on restart. The `softflowd` generator sends templates every 60 seconds, and the E2E test allows up to 90 seconds for template receipt before expecting flow data.

### Test Flow

1. Start `softflowd` flow generator targeting Minion
2. Wait for enriched flows on `deltav-flows` topic (verify protobuf deserializable via `kafka-console-consumer`)
3. Verify enrichment in the raw topic: `application` field populated, `exporter_node` resolved
4. Verify raw persistence in ClickHouse:
   ```sh
   docker compose exec clickhouse clickhouse-client \
       --user deltav --password deltav \
       --format TabSeparated \
       -q "SELECT count() FROM deltav.flows_raw WHERE timestamp > now() - INTERVAL 5 MINUTE"
   ```
   Assert non-zero.
5. Verify each dimension MV populated (four separate queries, one per MV):
   ```sh
   docker compose exec clickhouse clickhouse-client \
       --user deltav --password deltav \
       --format TabSeparated \
       -q "SELECT count() FROM deltav.flows_by_application_1m WHERE t_minute > now() - INTERVAL 5 MINUTE"
   # Repeat for flows_by_source_ip_1m, flows_by_conversation_1m, flows_by_dscp_1m
   ```
   Assert each non-zero.
6. Spot-check data quality by pulling the top application:
   ```sh
   docker compose exec clickhouse clickhouse-client \
       --user deltav --password deltav \
       --format TabSeparated \
       -q "SELECT application, sum(bytes_in + bytes_out) AS total
           FROM deltav.flows_by_application_1m
           WHERE t_minute > now() - INTERVAL 5 MINUTE
           GROUP BY application ORDER BY total DESC LIMIT 1"
   ```
   Assert a non-empty application name.

The `--format TabSeparated` flag produces output that is trivial to parse with `awk`/`cut` in bash, avoiding JSON parsing overhead. All assertions use a 60-second window after flow generation as the polling budget.

## Implementation Phases

### Phase 1: flow-enricher MVP — **DONE (PR #139, merged 2026-04-09)**

**Delivered:** Raw Spring Cloud Stream plumbing — flows parsed, near-empty `FlowDocument` published to `deltav-flows`.

- Maven module `core/flow-enricher/` with Spring Boot 4.0.3, Spring Cloud 2025.1.1, Kafka binder
- Consume from 4 Sink topics (Netflow-5/9, IPFIX, sFlow) via comma-separated multi-topic input binding
- 24 green unit tests covering `FlowLocalityCalculator`, `InterfaceMarkingCache`, `SinkMessageDeserializer`, `JdbcNodeInfoLookup` (with Caffeine cache)
- Standalone Docker image (`opennms/flow-enricher`), not sharing `daemon-base`
- `.proto` file committed to `core/flow-enricher/src/main/proto/deltav-flows.proto`

### Phase 1.5: horizon adapter integration — **NEXT**

**Delivers:** Real enrichment. Each incoming `TelemetryMessage` parsed into one or more `Flow` objects, enriched (locality, src/dst/exporter NodeInfo, interface marking), and mapped to a fully-populated `FlowDocumentProtos.FlowDocument` on the `deltav-flows` topic.

- `CapturingPipeline` implementation of horizon's `Pipeline` interface (stashes `List<Flow>` instead of persisting)
- `FlowToDocumentMapper` — field-by-field mapper from horizon `Flow` to `FlowDocumentProtos.FlowDocument`
- Per-protocol processors (`Netflow5MessageProcessor`, `Netflow9MessageProcessor`, `IpfixMessageProcessor`, `SFlowMessageProcessor`) wrapping horizon's adapter classes
- Fan-out strategy for Spring Cloud Stream `Function<byte[], List<byte[]>>` (or reactive equivalent) so one input Sink message can produce N output `FlowDocument` records
- Classification, clock-skew correction, and interface-marking integration
- Live integration test against the running delta-v stack

### Phase 2: ClickHouse persistence — **THIS SPEC SECTION**

**Delivers:** Full ingestion and aggregation via ClickHouse-native Kafka engine + materialized views. No Java code.

- `clickhouse` service in Docker Compose with persistent volume and proto directory mount
- `clickhouse-init` sidecar with `init-runner.sh` bash bootstrap
- Eight `.sql` files under `clickhouse/init/`: database, raw table, Kafka engine table, ingestion MV, plus four dimension MVs (application, source_ip, conversation, dscp)
- Env var templating for raw and aggregate TTLs via `sed`
- Removal of the `elasticsearch` service from Compose (PR #138 cleanup)
- `user-init/` override directory with `.gitkeep` placeholder
- Updated `test-flows-e2e.sh` asserting against ClickHouse raw table and all four MVs

**Verification:** Trigger flow traffic via Minion, confirm raw and aggregated rows in ClickHouse via `clickhouse-client` queries. All four dimension MVs must show non-zero rows within 60 seconds of flow generation.

### Phase 3: E2E hardening and community documentation

**Delivers:** Production-ready with full documentation story.

- `test-flows-e2e.sh` hardening (edge cases, template retransmission, multi-protocol fan-in)
- `docs/flows/community-consumer-guide.md` with protobuf contract examples in Java, Python, and Go
- Grafana connection example against ClickHouse HTTP port, with a sample dashboard JSON
- Performance tuning guide covering Kafka partition count, `kafka_num_consumers` sizing, ClickHouse TTL tuning, and compression settings
- Example user-init/ override files (GeoIP, BGP ASN, 1-hour rollup) as community starting points

## What This Replaces

| Component | Status |
|-----------|--------|
| Nephron (Apache Beam/Flink) | **Eliminated** — no Flink cluster needed |
| Elasticsearch flow storage (PR #138 plan) | **Replaced** — ClickHouse handles all persistence and aggregation |
| `flow-aggregator` Java module (planned in prior spec revision) | **Not built** — replaced by ClickHouse Kafka engine + SummingMergeTree MVs |
| Kafka Streams windowed aggregation | **Replaced** — ClickHouse materialized views handle continuous aggregation server-side |
| ElasticFlowRepository (horizon) | **Replaced** — ClickHouse `flows_raw` table |
| KafkaFlowForwarder (horizon) | **Replaced** — flow-enricher publishes to `deltav-flows` |
| DocumentEnricherImpl (horizon) | **Rewritten** — JDBC-based enrichment in flow-enricher (no Hibernate/DAO) |
| AggregatedFlowQueryService (horizon) | **Future** — REST API for querying aggregated data (separate service; ClickHouse SQL is the default "API" until then) |

## Future Considerations (Out of Scope)

| Item | Description |
|------|-------------|
| Custom field pass-through | Add `map<string, string> custom_fields` to FlowDocument proto for vendor-specific fields (Palo Alto, Cisco extensions). Wait for community feedback before designing. Additive proto change — non-breaking. |
| Minion template persistence | Persist NetFlow v9/IPFIX templates to Kafka so Minion survives restarts without waiting for template retransmission. Requires Minion-level change in delta-v-horizon. |
| Streaming telemetry protocols | JTI, OpenConfig, NXOS, BMP, Graphite — separate services with different data models and persistence paths. |
| Flow query REST API | REST endpoint for querying aggregated flow data. Either added as a new service or a thin JDBC proxy in front of ClickHouse. |
| BGP ASN enrichment views | User-init MV that aggregates by `src_as`/`dst_as` — already possible today against `flows_raw`; document as a community example in Phase 3. |
| GeoIP enrichment via ClickHouse dictionaries | ClickHouse dictionaries can pull from MaxMind GeoIP2 databases at query time, eliminating the need to denormalize country/city into every flow row. Candidate for a follow-up PR. |
| PostgreSQL node-label dictionary | ClickHouse dictionary pointed at `opennms_node` table, refreshed every 60s, enabling `dictGet('nodes', 'label', exporter_node_id)` in queries without a JOIN. Eliminates the need for the enricher to denormalize labels. |
| 1-hour rollup MVs | Additional materialized views at coarser granularity for multi-month queries. Current 1-minute MVs handle 7-day queries sub-second, so 1-hour rollups are only needed at very large scale. |
| Replicated cluster migration | Single-node `MergeTree` → `ReplicatedMergeTree` via ClickHouse Operator on Kubernetes. The operator generates its own macro-substituted DDL, so our single-node files become a template rather than a direct input. |
| Grafana dashboards shipped in repo | Phase 3 or a follow-up — JSON dashboard definitions for the four top-talker panels, committed to the repo under `opennms-container/delta-v/grafana/dashboards/`. |

## Revision History

- **2026-04-09**: Initial design. Two Spring Cloud Stream services (`flow-enricher` + `flow-aggregator`) with Elasticsearch as the aggregated storage backend; `flow-aggregator` used the Kafka Streams binder for windowed top-K aggregation.
- **2026-04-10**: Pivoted Phase 2 to ClickHouse. Community feedback requested ClickHouse with materialized views in place of Elasticsearch with a Java aggregator. The `flow-aggregator` Maven module is no longer planned; Phase 2 is now entirely SQL + a ClickHouse container. The `flow-enricher` service, the `deltav-flows` Kafka topic contract, and the `.proto` schema are all unchanged by this pivot — the public API remained stable, which is the architectural property the two-phase design was explicitly built to provide.
