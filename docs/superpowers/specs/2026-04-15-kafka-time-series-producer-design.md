# Kafka Time Series Producer — Design

**Date:** 2026-04-15
**Status:** Approved (pending implementation plan)
**Owner:** David Hustace
**Related memories:**
- `project_nephron_analytics_phase.md` — the architectural phase this unblocks (revised 2026-04-15 to drop Flow Aggregation from scope)
- `project_kafka_timeseries_pipeline.md` — the pipeline this builds the producer side of
- `project_thresholder_brainstorm.md` — the first downstream consumer
- `project_no_newts.md` — the persistence direction this replaces
- `project_minion_snmp_collector_future.md` — the future phase this design forward-accommodates
- `project_flow_enricher_phase2_parser.md` — the producer-pattern precedent (Spring Cloud Stream function bindings)

## Problem

Delta-v has no time-series persistence pipeline. Collectd's default TSS backend is `InMemoryStorage` (a throwaway), Pollerd and PerspectivePollerd have no-op `PersisterFactory` beans, and the inline `ThresholdingVisitor` has already been amputated from Collectd's pipeline (see `CollectdDaemonConfiguration.java:343`). Metrics are collected and dropped on the floor today.

The Nephron replacement phase of the delta-v architecture calls for four Spring Cloud Stream consumers — Thresholder, Prometheus Write, Flow Aggregation, and Streaming Telemetry Processing — that read from a shared Kafka Time Series topic and produce value from the metric stream. One of those (Flow Aggregation) is already covered by the ClickHouse Phase 2 dimension materialized views and has been dropped from scope, leaving three consumers: Thresholder, Prometheus Write, and Streaming Telemetry Processing.

None of those consumers can be built until a producer exists. This spec designs and builds the first producer: Collectd.

## Non-goals

Out of scope for this PR (tracked separately):

- **Pollerd and PerspectivePollerd producers** — deferred to a follow-up PR after learning from the Collectd producer. The wire format is designed to accommodate them without schema changes.
- **Consumer services** (Thresholder, Prometheus Write, Streaming Telemetry) — separate phases.
- **`provisiond` change feed** that populates the `deltav-node-context` topic. This spec designs the topic and protobuf contracts; implementation of the change feed is a separate PR that ships before the first consumer is built.
- **SnmpCollector-on-Minion migration** — future architectural phase. The wire format is designed to be forward-compatible with this migration.
- **Removing or replacing the `InMemoryStorage` TSS backend** — stays in place as a dev/test convenience. The Kafka producer runs alongside it as an additional output, not a replacement.
- **Collectd scheduler/publisher split** — tracked as a followup memory (`project_collectd_scheduler_publisher_split.md`, to be written during implementation). This is the right long-term architecture for restart resilience and horizontal publisher scale but is a separate design phase.
- **Horizontal scale of Collectd itself** — Collectd is a singleton service in current delta-v (no leader election, no work stealing). This spec inherits that constraint and does not attempt to change it.

## Architecture

### The flow

```
                           deltav-node-context (compacted)
                            (NodeContext records, event-driven
                             low rate, keyed {location}@{nodeId})
                                 ▲
                                 │ produce on node change
                                 │
                            provisiond
                            (future PR)
                                 │
                                 ▼
                      ┌─── GlobalKTable (consumer-side) ───┐
                      │                                    │
                      │   Every consumer bootstraps and    │
                      │   maintains a local in-memory      │
                      │   view of node context.            │
                      │                                    │
                      │   Joined against metric records    │
                      │   at process time to attach        │
                      │   node_label, foreign_source,      │
                      │   foreign_id, categories,          │
                      │   metadata.                        │
                      └────────────────────────────────────┘
                                 ▲
                                 │ KTable join at consume time
                                 │
   Collectd ─── produces ──► deltav-timeseries ◄── consumed by ─┬─► Thresholder
   (this PR)                 (keyed {location}@{nodeId})         ├─► Prometheus Write
                                                                 └─► Streaming Telemetry
                              ▲
                              │ (future)
                              │
   Minion SnmpCollector ──────┘   (stateless, schedule from Collectd
   (future phase)                  via Twin API, never touches DB)

   Pollerd ─────────────────►┐
   PerspectivePollerd ──────►┤ (follow-up PR)
                              ▼
                        deltav-timeseries
                        (same topic, same schema)
```

### Key properties

1. **Collectd stays simple.** Poll → translate → produce. No DB calls in the hot path, no enrichment caching, no label-selection logic. The Kafka producer is an additional persister in the existing persister chain, not a replacement for any existing behavior.

2. **Identity is not on the wire.** The `deltav-timeseries` records carry only `node_id`, `location`, `collection_package`, and the metric tree. No `node_label`, no `foreign_source`, no categories, no metadata. Consumers join against a separate compacted topic (`deltav-node-context`) via Kafka Streams `GlobalKTable` to attach human-meaningful context at consume time.

3. **Metadata is first-class.** The `NodeContext` protobuf carries the full OnmsMetaData surface (node-scoped, interface-scoped, and service-scoped metadata maps) — not a hand-picked label list. No "which fields are worth carrying" compromise.

4. **Minion Zero Trust is preserved.** Nothing in this spec requires Minion to read the database. In the future SnmpCollector-on-Minion phase, Minion publishes `TimeseriesBatch` records with `producer: PRODUCER_MINION_SNMP_COLLECTOR` using the same key shape, and all context resolution happens server-side via the KTable. Minion remains stateless with scheduling and configuration pushed from Collectd via Twin API.

5. **Forward compatibility for all three consumer services is free.** Thresholder, Prometheus Write, and Streaming Telemetry all use the same Kafka Streams pattern: subscribe to `deltav-timeseries`, join against the `deltav-node-context` GlobalKTable, do their own work. The wire format is producer-agnostic and consumer-agnostic.

## Protobuf schemas

### Schema 1: `TimeseriesBatch` — the metric record

Lives at `core/daemon-boot-collectd/src/main/proto/deltav-timeseries.proto`. Generated Java classes land in `org.deltav.timeseries.proto`.

```protobuf
syntax = "proto3";

package deltav.timeseries;

option java_package = "org.deltav.timeseries.proto";
option java_multiple_files = true;

// One record per CollectionSet poll. Published to the deltav-timeseries topic,
// keyed by "{location}@{node_id}". Deliberately producer-agnostic: the same
// shape is valid whether the producer is Collectd, Pollerd, PerspectivePollerd,
// or a future SnmpCollector-on-Minion deployment. No identity labels are
// carried here — consumers join against the deltav-node-context GlobalKTable
// at process time.
message TimeseriesBatch {
  // Milliseconds since Unix epoch when the poll was produced by the collector.
  // "Collection time," not "publish time" — stamped by the producer before
  // serialization and immutable through the pipeline.
  int64 timestamp_ms = 1;

  // Numeric node ID from the nodes table. Used as part of the partition key
  // and as the join key against deltav-node-context.
  int32 node_id = 2;

  // Logical location name (e.g. "Default", "Site-A"). Part of the partition
  // key. Redundant with NodeContext.location but carried here so consumers
  // can derive the join key from the metric record alone.
  string location = 3;

  // The package that produced this batch (e.g. "default", "interfaceSnmp").
  // Used by consumers to filter rules (e.g. "only evaluate threshold X on
  // records from the 'critical-infra' package").
  string collection_package = 4;

  // The producer that emitted this batch. Identifies which daemon wrote the
  // record so consumers can distinguish e.g. Collectd vs. Pollerd origin
  // without adding a separate topic. Enum because the set is small and
  // closed.
  ProducerType producer = 5;

  // The resource tree. A single CollectionSet usually has multiple resources
  // (one per collected entity: node, interface, storage row, etc).
  repeated Resource resources = 6;
}

enum ProducerType {
  PRODUCER_UNSPECIFIED = 0;
  PRODUCER_COLLECTD = 1;
  PRODUCER_POLLERD = 2;
  PRODUCER_PERSPECTIVE_POLLERD = 3;
  PRODUCER_MINION_SNMP_COLLECTOR = 4;  // Future phase
  PRODUCER_STREAMING_TELEMETRY = 5;    // Future phase (gNMI/GPB)
}

// A single resource in the CollectionSet. For Collectd this maps 1:1 to
// org.opennms.netmgt.collection.api.CollectionResource. For Pollerd this
// is the monitored service; for PerspectivePollerd, the (service, perspective)
// pair.
message Resource {
  // Stable resource identifier string. Format matches horizon's resource
  // ID convention: "node[N]", "node[N].interfaceSnmp[eth0]", etc. Used
  // for deduplication, display grouping, and as a Prometheus-style "instance"
  // label component.
  string resource_id = 1;

  // Resource type name (e.g. "node", "interfaceSnmp", "hrStorageIndex").
  // Comes from the collectd-configuration.xml resource type definitions.
  string type = 2;

  // Per-instance key for tabular resources (e.g. "eth0" for an interface,
  // "1.3.6.1.2.1.25.2.3.1.1.1" for a storage row). Empty for non-tabular
  // resources. Distinct from resource_id because instance is the raw
  // collector-side key without the "node[N]." prefix.
  string instance = 3;

  // Attribute groups defined in the collectd-configuration.xml datacollection
  // config for this resource type.
  repeated AttributeGroup groups = 4;
}

// A named group of attributes. Matches horizon's AttributeGroup concept —
// typically one per MIB table or one per logical grouping of metrics.
message AttributeGroup {
  // Group name, e.g. "mib2-tcp", "hrStorage", "response-time".
  string name = 1;

  // The individual metrics in this group.
  repeated Attribute attributes = 2;
}

// One individual metric sample within a group.
message Attribute {
  // Attribute name, e.g. "tcpActiveOpens", "hrStorageUsed", "response".
  string name = 1;

  // The measured value. Use oneof because metrics are either numeric
  // (gauge/counter) or string (stringAttribute in horizon terminology).
  oneof value {
    double numeric = 2;
    string text = 3;
  }

  // Attribute type. Matches horizon's AttributeType enum. Consumers use
  // this to decide how to treat the value (rate for counters, absolute
  // for gauges, ignore for strings unless explicitly handled).
  AttributeType type = 4;
}

enum AttributeType {
  ATTRIBUTE_TYPE_UNSPECIFIED = 0;
  ATTRIBUTE_TYPE_COUNTER = 1;
  ATTRIBUTE_TYPE_GAUGE = 2;
  ATTRIBUTE_TYPE_STRING = 3;
}
```

### Schema 2: `NodeContext` — the GlobalKTable record

Lives at `core/daemon-boot-collectd/src/main/proto/deltav-node-context.proto`. Same generated package so both schemas end up in `org.deltav.timeseries.proto`.

**Note:** This schema is designed in this spec but its producer (provisiond change feed) ships in a separate follow-up PR. The schema is included here so the overall contract is fully specified and the `deltav-node-context` topic's `NewTopic` bean declaration can be included in the initial producer PR.

```protobuf
syntax = "proto3";

package deltav.timeseries;

option java_package = "org.deltav.timeseries.proto";
option java_multiple_files = true;

// One record per node, published to the deltav-node-context topic whenever
// a node is provisioned, updated, deleted, or its metadata changes. The
// topic is compacted (log.cleanup.policy=compact), so the latest record
// per key survives forever and a new consumer can bootstrap from the
// beginning of the topic to materialize the full current state.
//
// Keyed by "{location}@{node_id}" — same key shape as TimeseriesBatch so
// consumers can join directly without a key transformer.
message NodeContext {
  // Numeric node ID. Matches TimeseriesBatch.node_id.
  int32 node_id = 1;

  // Location. Matches TimeseriesBatch.location.
  string location = 2;

  // Human-readable node label as displayed in the UI (e.g.
  // "server-01.prod.example.com"). Comes from OnmsNode.label.
  string node_label = 3;

  // Provisioning foreign source (e.g. "provision-prod"). Empty if the
  // node was not created via provisioning.
  string foreign_source = 4;

  // Provisioning foreign ID (e.g. "server-01"). Stable identifier for
  // this node within its foreign source.
  string foreign_id = 5;

  // Categories assigned to the node. Typically 0-5 entries.
  repeated string categories = 6;

  // Full metadata map for the node. Keys follow OnmsMetaData convention:
  // "context:key" (e.g. "requisition:sysLocation", "snmp:sysContact",
  // "ip:hostname"). Values are whatever provisiond or SNMP discovery
  // populated. Can be large — hundreds of entries for heavily-metadataed
  // nodes is not unusual.
  //
  // Consumer responsibility: metadata values should be used as-is; don't
  // attempt to parse or reinterpret them. If a consumer needs to filter
  // by a metadata value, it filters on the string.
  map<string, string> metadata = 7;

  // Interface-level metadata, keyed by IP address (IPv4 or IPv6 string).
  // For nodes with many interfaces this can grow — each entry carries
  // its own metadata map. Empty map is a valid value meaning "no
  // interface-scoped metadata for this node."
  map<string, InterfaceContext> interface_metadata = 8;

  // Service-level metadata, keyed by "{ip_address}/{service_name}" for
  // stable uniqueness (e.g. "192.0.2.1/ICMP").
  map<string, ServiceContext> service_metadata = 9;

  // Wall-clock time when this record was produced, in epoch milliseconds.
  // Consumers use this for freshness checks. Strictly informational —
  // the topic's log compaction determines which record wins per key, not
  // this timestamp.
  int64 updated_at_ms = 10;

  // Tombstone marker. When a node is deleted from provisioning, provisiond
  // publishes a final NodeContext with deleted=true, allowing log
  // compaction to eventually garbage-collect the key. Consumers should
  // treat a tombstoned node as "not present."
  bool deleted = 11;
}

// Interface-scoped metadata. Carried as a nested message so the map value
// in NodeContext.interface_metadata can hold both a metadata map and any
// future interface-level fields without schema churn.
message InterfaceContext {
  map<string, string> metadata = 1;
  // Future: interface description, interface alias, snmp_ifindex, etc.
  // Leaving space open (fields 2+) for incremental additions.
}

// Service-scoped metadata. Same structure as InterfaceContext.
message ServiceContext {
  map<string, string> metadata = 1;
  // Future: service-level display name, criticality hint, etc.
}
```

### Schema evolution rules (hard constraints once Phase 2 ships)

**Forward-compatible changes (always safe):**
- Adding fields to any message using unused field numbers
- Adding enum values at the end of an enum
- Adding new scoped-metadata maps (e.g., a `group_metadata` map)
- Adding new producer types, attribute types

**Not forward-compatible (require deprecation cycle):**
- Renaming existing fields
- Changing field types
- Deleting fields
- Renumbering field numbers

**During Phase 1 (dev-only flag state),** breaking changes are permissible because no production consumer depends on the schema. Once Phase 2 ships (production-enabled), only forward-compatible changes are allowed.

## Topic configuration

### `deltav-timeseries` (metric records)

| Setting | Value | Rationale |
|---|---|---|
| Name | `deltav-timeseries` | Matches `deltav-*` convention (flow-enricher uses `deltav-flows`) |
| Partitions | 16 (default, configurable via `DELTAV_TIMESERIES_PARTITIONS`) | Enough for consumer parallelism at production scale without being excessive for small dev deployments |
| Replication factor | 3 in production, 1 in dev Docker Compose | Matches existing delta-v topic conventions |
| Cleanup policy | `delete` | Time-series records are immutable and time-bounded |
| Retention | 7 days (configurable via `DELTAV_TIMESERIES_RETENTION_DAYS`) | Covers long weekends; bounded Kafka disk use |
| Partition assignment strategy | `CooperativeStickyAssignor` (consumer side) | Minimizes rebalance churn when consumers scale |
| Key | `{location}@{node_id}` UTF-8 bytes | Matches `NodeContext` topic key shape for direct joining |
| Value | `TimeseriesBatch` protobuf | Per schema above |
| `min.insync.replicas` | 2 in production | Record is committed when 2 of 3 replicas have it |

### `deltav-node-context` (context records)

| Setting | Value | Rationale |
|---|---|---|
| Name | `deltav-node-context` | Same convention |
| Partitions | 8 (default, configurable via `DELTAV_NODE_CONTEXT_PARTITIONS`) | Lower rate than metrics; enough parallelism for `GlobalKTable` initial load |
| Replication factor | 3 in production, 1 in dev | Matches metrics topic |
| Cleanup policy | `compact` | Defining property — keeps latest record per key forever |
| Retention | `-1` (infinite for non-deleted keys) | Log compaction retains latest-per-key |
| `min.compaction.lag.ms` | 60000 (1 minute) | Forces log cleaner to wait before compacting so new consumers bootstrapping from the beginning see recent updates |
| `delete.retention.ms` | 86400000 (24 hours) | Time a tombstone record must remain before compaction removes it; gives consumers 24 hours to observe deletion |
| Key | `{location}@{node_id}` UTF-8 bytes | Same as metrics |
| Value | `NodeContext` protobuf (null value is also a tombstone, but we prefer `deleted=true` records for observability) | Per schema above |

### Topic provisioning ownership (R8 mitigation)

Topics **must be explicitly provisioned** as Spring Boot `NewTopic` beans at application startup. Relying on Kafka broker auto-create-topics is explicitly forbidden: auto-create uses broker defaults (typically 1 partition, 1 replica), which silently defeats the 16-partition design.

In this producer PR, both `NewTopic` beans are declared in `TimeseriesKafkaPublisherConfiguration`:

```java
@Bean
public NewTopic deltavTimeseriesTopic(
        @Value("${deltav.timeseries.partitions:16}") int partitions,
        @Value("${deltav.timeseries.replication-factor:1}") short replicationFactor,
        @Value("${deltav.timeseries.retention-days:7}") int retentionDays) {
    return TopicBuilder.name("deltav-timeseries")
        .partitions(partitions)
        .replicas(replicationFactor)
        .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
        .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(Duration.ofDays(retentionDays).toMillis()))
        .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "lz4")
        .build();
}

@Bean
public NewTopic deltavNodeContextTopic(
        @Value("${deltav.node-context.partitions:8}") int partitions,
        @Value("${deltav.node-context.replication-factor:1}") short replicationFactor) {
    return TopicBuilder.name("deltav-node-context")
        .partitions(partitions)
        .replicas(replicationFactor)
        .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT)
        .config(TopicConfig.RETENTION_MS_CONFIG, "-1")
        .config(TopicConfig.MIN_COMPACTION_LAG_MS_CONFIG, "60000")
        .config(TopicConfig.DELETE_RETENTION_MS_CONFIG, "86400000")
        .build();
}
```

**Initial dual-ownership:** both `NewTopic` beans are declared in Collectd in this PR, even though `deltav-node-context` has no producer yet. When the `provisiond` change feed PR ships, the `deltavNodeContextTopic` bean is removed from Collectd and declared in provisiond instead. Temporary double-ownership is fine because `KafkaAdminClient.createTopics` is idempotent — if the topic already exists with the requested configuration, the call is a no-op.

## Spring Cloud Stream producer binding

Added to `core/daemon-boot-collectd/src/main/resources/application.yml`:

```yaml
spring:
  cloud:
    stream:
      bindings:
        publishTimeseries-out-0:
          destination: deltav-timeseries
          producer:
            partition-count: ${DELTAV_TIMESERIES_PARTITIONS:16}
            use-native-encoding: true
      kafka:
        bindings:
          publishTimeseries-out-0:
            producer:
              configuration:
                key.serializer: org.apache.kafka.common.serialization.ByteArraySerializer
                value.serializer: org.apache.kafka.common.serialization.ByteArraySerializer
                compression.type: lz4
                linger.ms: 50
                batch.size: 65536
                acks: all
                enable.idempotence: true
                max.in.flight.requests.per.connection: 5

deltav:
  timeseries:
    enabled: ${DELTAV_TIMESERIES_ENABLED:false}
    partitions: ${DELTAV_TIMESERIES_PARTITIONS:16}
    replication-factor: ${DELTAV_TIMESERIES_REPLICATION_FACTOR:1}
    retention-days: ${DELTAV_TIMESERIES_RETENTION_DAYS:7}
  node-context:
    partitions: ${DELTAV_NODE_CONTEXT_PARTITIONS:8}
    replication-factor: ${DELTAV_NODE_CONTEXT_REPLICATION_FACTOR:1}
```

**Configuration notes:**
- `use-native-encoding: true` tells SCS to pass the byte array payload through to Kafka unchanged, without JSON/message-converter interference. Same pattern flow-enricher uses.
- `compression.type: lz4` gives ~3× wire-size reduction on CollectionSet protobuf at low CPU cost.
- `linger.ms: 50` and `batch.size: 65536` let the producer batch records for up to 50 ms or 64 KB, whichever comes first.
- `acks: all` + `enable.idempotence: true` are the strongest durability guarantees. Slightly heavier than strictly necessary for metrics but establishes a consistent pattern across delta-v producers.
- `max.in.flight.requests.per.connection: 5` is the maximum allowed under idempotence while preserving per-partition ordering.
- `deltav.timeseries.enabled` is the kill-switch feature flag (default `false`, opt-in).

## Producer implementation

### Component layout

```
core/daemon-boot-collectd/src/main/java/org/deltav/collectd/timeseries/
├── TimeseriesKafkaPublisherConfiguration.java   (@Configuration, @ConditionalOnProperty)
├── TimeseriesKafkaPublisher.java                (orchestration: translate + send)
├── CollectionSetToProtobufTranslator.java       (pure function, CollectionSet → TimeseriesBatch)
└── TimeseriesKafkaPersister.java                (horizon Persister adapter)

core/daemon-boot-collectd/src/main/proto/
├── deltav-timeseries.proto                      (TimeseriesBatch schema)
└── deltav-node-context.proto                    (NodeContext schema)
```

### Component responsibilities

**`CollectionSetToProtobufTranslator`** — Pure function. Walks a horizon `CollectionSet` visitor tree and emits a `TimeseriesBatch`. No side effects, no Spring state, no logging (callers log). Unit-tested exhaustively against mocked CollectionSet instances.

**`TimeseriesKafkaPublisher`** — Orchestration layer. Takes a `CollectionSet` from the caller, invokes the translator, serializes to bytes, builds the partition key as `{location}@{node_id}`, and calls `streamBridge.send("publishTimeseries-out-0", Message<byte[]>)`. Handles all error paths (empty batch, translator failure, send failure, size warning) without throwing back to the caller. Increments Micrometer counters for each path.

**`TimeseriesKafkaPersister`** — Thin adapter implementing horizon's `Persister` interface. Delegates to `TimeseriesKafkaPublisher`. Isolates the Kafka-specific logic from the horizon integration point so the publisher can be unit-tested without horizon types.

**`TimeseriesKafkaPublisherConfiguration`** — Spring `@Configuration` class that declares all beans. Gated on `@ConditionalOnProperty(name = "deltav.timeseries.enabled", havingValue = "true")`. When the feature flag is off, none of the beans are created and the persister chain is unchanged.

### Size safety check (R7 mitigation)

`TimeseriesKafkaPublisher` checks the serialized protobuf size before sending:

```java
byte[] payload = batch.toByteArray();
if (payload.length > 800_000) {
    log.warn("Oversized TimeseriesBatch: {} bytes for node {} in package {}. " +
             "Kafka max.request.size default is 1 MB. Consider reducing poll scope " +
             "for this node in collectd-configuration.xml.",
             payload.length, batch.getNodeId(), batch.getCollectionPackage());
    meterRegistry.counter("deltav_timeseries_batch_size_warning_total",
                          "location", batch.getLocation()).increment();
}
// still publish — do not drop the record
streamBridge.send(...);
```

The safety threshold is 800 KB (80% of the 1 MB Kafka default `max.request.size`). The check only warns; it does not drop the record or attempt to split it (splitting would break the atomicity invariant the wire format relies on). Operators who see sustained warnings reduce the per-poll scope in `collectd-configuration.xml` — the fix is operator-facing, not producer-side.

### Wiring into the persister chain

The `TimeseriesKafkaPersister` bean is registered as an *additional* persister alongside the existing `InMemoryStorage`-backed TSS persister. Both run when the feature flag is on; only `InMemoryStorage` runs when the flag is off. The chain's existing behavior is unchanged.

### Exposed metrics (Micrometer → Prometheus via `/actuator/prometheus`)

| Metric | Type | Labels | Purpose |
|---|---|---|---|
| `deltav_timeseries_batches_published_total` | counter | `location`, `producer="collectd"` | Successful publishes |
| `deltav_timeseries_batches_failed_total` | counter | `location`, `producer="collectd"`, `reason` | Failures with reason tag (`serialization_error`, `kafka_send_error`, `empty_batch`) |
| `deltav_timeseries_batch_size_bytes` | distribution summary | `location`, `producer="collectd"` | Wire size distribution |
| `deltav_timeseries_batch_size_warning_total` | counter | `location` | Oversized-batch warnings (R7) |
| `deltav_timeseries_resources_per_batch` | distribution summary | `location`, `producer="collectd"` | Resource-count distribution |
| `deltav_timeseries_publish_duration_seconds` | timer | `location`, `producer="collectd"` | End-to-end publish latency |

## Testing strategy

Five test categories, each covering a distinct failure mode.

### 1. Unit tests — `CollectionSetToProtobufTranslator`

Pure function, exhaustive coverage.

Test cases:
- Empty CollectionSet → empty `resources` list
- Single node resource, one gauge → correct field mapping, `ATTRIBUTE_TYPE_GAUGE`
- Single node resource, one counter → `ATTRIBUTE_TYPE_COUNTER`
- String attribute → `oneof value.text`, `ATTRIBUTE_TYPE_STRING`
- Interface-scoped resource → `type="interfaceSnmp"`, correct `instance` and `resource_id`
- Mixed resource types in one CollectionSet (node + interfaces + storage rows)
- Multiple attribute groups per resource
- Null/missing `CollectionAgent` → throws or returns empty (not silent NPE)
- Negative and zero numeric values
- Very large CollectionSet (100 resources × 10 groups × 20 attributes)
- Unicode attribute names
- `STATUS_FAILED` CollectionSet → skip (not published)
- Resource with zero groups → omitted

Target: ~20 tests, all sub-millisecond.

### 2. Unit tests — `TimeseriesKafkaPublisher`

Orchestration layer coverage.

Test cases:
- Happy path → one `streamBridge.send()` call with correct destination and key
- Empty batch → skip, no send, skip counter incremented
- Translator throws → log, failure counter, no rethrow
- `streamBridge.send()` returns false → log, failure counter, continue
- `streamBridge.send()` throws → same
- Oversized batch (>800 KB) → warning log, warning counter, still publishes
- Partition key edge cases (location with `@`, nodeId=0, long location names)
- Metrics increment correctly across all paths

Target: ~14 tests.

### 3. Integration tests — Spring Cloud Stream test binder

Validates Spring wiring, SCS binding declarations, feature-flag toggling, protobuf serialization round-trip, and partition key handling. Uses `spring-cloud-stream-test-binder` (same pattern as flow-enricher parser bridge ITs).

Test cases:
- Publisher bean exists when `deltav.timeseries.enabled=true`
- Publisher bean absent when flag is false (feature-flag kill switch verification)
- When flag is false, no `StreamBridge` calls are made; persister chain is legacy-only
- Publish a CollectionSet, read back from test binder, verify protobuf fields
- Partition key lands on `KafkaHeaders.KEY` as UTF-8 bytes
- Large CollectionSet round-trips correctly

Target: ~8 tests.

### 4. Integration tests — Kafka Testcontainers

Validates real Kafka wire path: actual broker, actual serializer config, actual partitioning, actual compression. Uses Testcontainers `KafkaContainer`.

Test cases:
- Round-trip with lz4 compression, verify wire size is compressed
- Partition distribution: 100 records for 8 different nodeIds, verify partition-stickiness
- Key bytes UTF-8 decode to `{location}@{node_id}`
- Value bytes parse as valid protobuf
- Idempotent retry: drop one connection, verify no duplicates land
- `NewTopic` bean creates topic with correct partition count and retention policy (verify via `AdminClient.describeTopics`)

Target: ~6 tests.

### 5. E2E test — Docker Compose

Full stack validation via `opennms-container/delta-v/test-timeseries-e2e.sh`, following the `test-flows-e2e.sh` pattern.

Sequence:
1. Start delta-v Docker Compose with `DELTAV_TIMESERIES_ENABLED=true` for Collectd
2. Provision a test node via REST API
3. Wait for Collectd to poll it
4. Small tap consumer (shell `kcat` or lightweight Java) reads from `deltav-timeseries`
5. Assert: at least one record within 60 seconds, expected node ID, at least one resource, key format matches `{location}@{node_id}`
6. Assert: `deltav_timeseries_batches_published_total{producer="collectd"} > 0` on Prometheus endpoint
7. Assert: `deltav_timeseries_batches_failed_total` counters all zero
8. Tear down cleanly

This is the golden-path smoke test and the definitive ship criterion.

## Rollout and feature flag lifecycle

### Phase 0 — Flag off by default (this PR ships)
- Default `false` everywhere
- No behavior change for anyone who doesn't opt in
- PR merges without touching any production deployment
- **Success criterion:** all five test categories pass, E2E script validates golden path

### Phase 1 — Flag on in dev, off in production
- Default flipped to `true` in `opennms-container/delta-v/.env.example` (or equivalent)
- Developers get the publisher automatically on `docker compose up`
- Production config templates stay off
- **Success criterion:** two weeks of dev usage with no failures in `deltav_timeseries_batches_failed_total`, no unexpected Kafka disk growth, no Collectd performance regressions
- **This is the schema-iteration window.** Breaking schema changes are permissible here; once Phase 2 ships, only forward-compatible changes are allowed.

### Phase 2 — Flag on by default in production
- Default flipped to `true` in production config templates
- **Gated on consumer existence:** at least one consumer (most likely Prometheus Write or Thresholder in pilot) must exist before Phase 2. Turning on a producer with no consumer is pointless Kafka disk tax.
- **Success criterion:** at least one production-scale deployment has run Phase 1 for a week with clean metrics

### Phase 3 — Flag removed entirely
- `@ConditionalOnProperty` annotation removed, publisher bean always wired, flag disappears from `application.yml`
- **Success criterion:** Phase 2 has run for a month with no rollback requests
- Closes out the feature flag as technical debt

## Rollback

**Producer rollback is trivial.** Set `DELTAV_TIMESERIES_ENABLED=false`, restart Collectd. The `@ConditionalOnProperty` kill switch means the publisher beans don't exist when the flag is off; the persister chain returns exactly to its pre-flag state. No data to unwind, no schema migrations to reverse, no topics to delete.

**Kafka state is idempotent from the consumer's perspective.** A bad batch of records can be corrected by republishing; the 7-day retention ages out bad data automatically if consumers don't deduplicate.

**Schema evolution is one-way** during Phase 2+. A wrong field requires a deprecation cycle (add new field, deprecate old, wait for consumers to switch). This is the normal protobuf story.

## Risks

### R1 — Wire format is wrong and we don't discover it until a consumer is built
**Likelihood:** moderate. Schema design without a real consumer is speculative.
**Mitigation:** Phase 1 (dev-on, prod-off) is the low-stakes iteration window.
**Acceptance:** breaking schema changes during Phase 1 are acceptable; once Phase 2 ships, additive-only.

### R2 — Collectd scale: the publisher becomes a bottleneck at large deployments
**Likelihood:** low-to-moderate at current scale, rising as deployments grow.
**Mitigation:** the Kafka producer is async (non-blocking) and batching (`linger.ms=50`, `batch.size=65536`). Steady-state throughput at typical delta-v scale is far below the Kafka producer ceiling.
**Acceptance:** metrics on `/actuator/prometheus` show exactly when the ceiling is approached. The scheduler-publisher split (R5) is the long-term fix.

### R3 — Kafka broker disk pressure from 7-day retention
**Likelihood:** low. Compressed CollectionSet records are small (tens of KB), and a 1,000-node deployment polling every 5 minutes uses roughly 140 GB total over 7 days.
**Mitigation:** retention is configurable via `DELTAV_TIMESERIES_RETENTION_DAYS`.
**Acceptance:** document expected disk footprint in operator docs.

### R4 — NodeContext topic lag: metrics arrive before context is available
**Likelihood:** real, especially for newly provisioned nodes.
**Mitigation:** `TimeseriesBatch` carries `node_id` and `location` directly, so consumers can fall back to "unknown node, log, continue" rather than dropping the record. This risk is more relevant to future consumer PRs than to this producer PR but is flagged here because it motivates the producer's design choice.

### R5 — Future architectural direction: Collectd scheduler-publisher split
**Likelihood:** certain if delta-v scales significantly.
**Mitigation:** wire format is producer-agnostic. When the split happens, the topic contract is unchanged and the split is internal to Collectd.
**Acceptance:** tracked as followup memory `project_collectd_scheduler_publisher_split.md` written during implementation.

### R6 — SnmpCollector-on-Minion migration obsoletes the Collectd publisher
**Likelihood:** probable within 6+ months.
**Mitigation:** wire format is producer-agnostic. Minion's future publisher uses the same `TimeseriesBatch` shape with `producer: PRODUCER_MINION_SNMP_COLLECTOR`. Collectd publisher becomes maintenance-only until decommissioned in a coordinated cutover.
**Acceptance:** work invested in this PR is not wasted — it establishes the pattern, topic, contract, and operational muscle.

### R7 — Oversized CollectionSets from dense nodes exceed Kafka `max.request.size`
**Likelihood:** low but real. A 1000+ interface core switch polled across multiple MIB tables can produce a CollectionSet that, when serialized, approaches 1 MB even with lz4 compression.
**Mitigation:** `TimeseriesKafkaPublisher` checks serialized size before send. If greater than 800 KB (80% of default 1 MB ceiling), emit a WARN log with node ID and package, and increment `deltav_timeseries_batch_size_warning_total`. Still publish — do not drop the record.
**Operator fix:** reduce poll scope in `collectd-configuration.xml` for the affected node (exclude dense MIB tables, use more focused collection packages). This is not a producer-side concern; splitting batches would break the atomicity invariant the wire format relies on.
**Broker-side option:** operators can raise `max.request.size` on the Kafka producer config if they truly need larger batches, but this should be a deliberate decision.

### R8 — Topic auto-creation with wrong partition count
**Likelihood:** certain if left unchecked. Kafka broker auto-create-topics defaults to 1 partition / 1 replica, silently defeating the 16-partition design.
**Mitigation:** both topics are explicitly provisioned via Spring Boot `NewTopic` beans at application startup. `KafkaAdminClient.createTopics` is idempotent: if the topic already exists, the call is a no-op; if it doesn't, it creates with the specified configuration.
**Documentation:** the spec explicitly forbids reliance on auto-create-topics; all delta-v topics are provisioned by declared beans.

## Success criteria for this PR to ship

- [ ] All five test categories pass
- [ ] Feature flag verified as a clean kill switch (Layer 3 test case)
- [ ] `deltav_timeseries_*` metrics exposed on `/actuator/prometheus`
- [ ] Both `NewTopic` beans declared and `AdminClient.describeTopics` verifies correct configuration in the Testcontainers test
- [ ] R7 oversize-warning path tested (Layer 2 unit test)
- [ ] E2E script ships and passes in CI or local dev
- [ ] Operator documentation updated:
  - `DELTAV_TIMESERIES_ENABLED` env var and what it does
  - New Prometheus metrics
  - Expected Kafka disk footprint
  - Known limitations (Collectd single-replica, schema v1 subject to refinement during Phase 1)
- [ ] Followup memory `project_collectd_scheduler_publisher_split.md` written
- [ ] Design doc committed before implementation begins

## Explicit non-commitments

- **No performance SLO.** Metrics measure what we achieve; if insufficient, we optimize.
- **No production deployment timeline.** Phase 2 happens when a consumer exists and an operator volunteers.
- **No consumer guarantee.** Order of Thresholder vs. Prometheus Write vs. Streaming Telemetry is a separate decision.
- **No Minion telemetry listener changes.** Minion stays exactly as it is today.
- **No change to `InMemoryStorage` TSS path.** Continues to work for dev/test; new publisher runs alongside.
