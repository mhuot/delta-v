# Kafka Time Series Pipeline — Phase 1: Provisiond Node-Context Change Feed

**Date:** 2026-04-16
**Status:** Approved (pending implementation plan)
**Owner:** David Hustace
**Predecessor:** `2026-04-15-kafka-time-series-producer-design.md` (Phase 0 — Collectd producer + wire contracts, shipped in delta-v#169 / #170)
**Related memories:**
- `project_kafka_timeseries_producer_next_session.md` — Phase 0 outcomes + E2E evidence
- `project_provisioning_adapters_as_sidecars.md` — future re-evaluation of the in-process Kafka event-roundtrip pattern and restart-republish amplification; bundled
- `feedback_spring_boot_scan_package_trap.md` — mandatory scan-package coverage to avoid the #169→#170 cascade
- `project_eventd_eliminated.md` — delta-v has no in-JVM event bus; events flow through Kafka
- `project_collectd_scheduler_publisher_split.md` — tracked followup for Collectd architecture

## Problem

Phase 0 shipped the Collectd producer for the `deltav-timeseries` Kafka topic. Those records carry only **numeric identity** (`node_id`, `location`, `collection_package`, `producer`). Every downstream consumer — Prometheus Write, Thresholder, Streaming Telemetry — must join against the `deltav-node-context` compacted topic to attach human-meaningful labels (`node_label`, `foreign_source`, `foreign_id`, categories, the full `OnmsMetaData` surface).

Phase 0 already committed the `NodeContext` protobuf schema and the `NewTopic` bean for `deltav-node-context` (8 partitions, `cleanup.policy=compact`, `min.compaction.lag.ms=60000`, `delete.retention.ms=86400000`). But **nothing produces to it yet.** No consumer can be meaningfully built until the change feed exists.

Phase 1 is the provisiond-side producer that writes to `deltav-node-context` on every node lifecycle change plus a bootstrap pass at startup. Once it ships, the GlobalKTable pattern is unblocked and any consumer service (Phase 2a, 2b, 2c) can be built against a fully-populated topic.

## Non-goals

Out of scope for this PR:

- **Consumer services.** Phase 2a (Prometheus Write), Phase 2b (Pollerd + PerspectivePollerd producers), Phase 2c (Thresholder) all consume from `deltav-node-context` but each is its own phase. Phase 1 only ships the producer.
- **Schema changes.** The `NodeContext` protobuf was frozen in Phase 0 and stays frozen. No new fields, no renames.
- **Scheduler-publisher split for Collectd.** Tracked separately in `project_collectd_scheduler_publisher_split.md`.
- **Smart-skip restart bootstrap** (compare bytes, skip unchanged-node publish). Deferred and bundled with the sidecar-adapter evaluation per `project_provisioning_adapters_as_sidecars.md`.
- **In-process provisiond-to-provisiond event shortcut.** Events roundtrip through Kafka. Documented as a known architectural artifact to revisit with the sidecar evaluation.
- **Metadata-changed UEI.** Horizon has no dedicated `metadataChanged` event. We cover metadata writes indirectly via `nodeUpdated`, `IMPORT_SUCCESSFUL`, and the bootstrap pass on restart. Direct DB writes that bypass `EventForwarder` remain a documented gap.

## Architecture

### The flow

```
Provisioning change (import / scan / REST edit / adapter write)
  ↓
DefaultProvisionService / adapter
  ↓  EventForwarder.sendNow(event)
Kafka opennms-fault-events  ── (~ms roundtrip) ──────────┐
                                                         │
                                                         ▼
                       NodeContextChangeFeedListener (@EventListener)
                         - 13 @EventHandler methods for lifecycle UEIs
                         - delete / relocation bypass the debouncer
                         - everything else enqueues into:
                                                         ▼
                           NodeContextDebouncer (per-nodeId, 250 ms default)
                                                         ▼
                                         NodeContextPublisher
                                         - TransactionTemplate (read-only)
                                         - NodeDao.get(nodeId) + LAZY metadata fetch
                                         - NodeToProtobufTranslator.translate()
                                         - size-check (warn at 800 KB)
                                         - streamBridge.send("publishNodeContext-out-0")
                                                         ▼
                              Kafka deltav-node-context (compacted, 8 partitions)
                                                         ▲
                                                         │
Provisiond startup  ─────────────────────────────────────┘
  ↓
NodeContextBootstrapRunner (SmartLifecycle.start, phase after provisionerLifecycle)
  - nodeDao.findAll() in read-only transaction
  - publisher.publishNode(nodeId) per row (bypasses debouncer)
  - logs every 1000 nodes
```

### Key properties

1. **Single producer, authoritative stream.** Provisiond is the only process writing to `deltav-node-context`. The NewTopic bean moves from Collectd (Phase 0 dual-ownership for bootstrap) to provisiond in the same PR. `KafkaAdmin.createTopics` is idempotent so the handoff has no race window.

2. **Post-commit reads, Kafka roundtrip.** Provisiond emits UEIs to Kafka and subscribes to the same Kafka topic in-process (via `kafkaEventSubscriptionService`). This is structurally circular but consistent with every other provisiond event listener in delta-v (four already exist). The post-commit property comes for free: by the time our listener sees an event, the DB transaction is committed.

3. **Debounced burst coalescing.** During an import, a single node fires multiple UEIs within milliseconds (`nodeAdded`, `nodeInfoChanged`, `nodeLabelChanged`, `nodeUpdated`). The per-nodeId debouncer collapses these into one DB read + one publish per node per 250 ms quiet window. Deletes and relocations bypass the debouncer to avoid stale writes after state-terminating events.

4. **Blocking bootstrap on restart.** Provisiond `start()` enumerates all `OnmsNode` rows and publishes each before reporting ready. At delta-v scale (hundreds of nodes per deployment) bootstrap is dwarfed by Hibernate + Quartz startup. Every restart republishes the full snapshot; compaction absorbs duplicates within `min.compaction.lag.ms`.

5. **Explicit tombstones.** On `nodeDeleted`, the listener emits a `NodeContext { node_id, location, updated_at_ms, deleted=true }` record, then evicts any pending debouncer future for that nodeId. Consumers treat `deleted=true` as "node is gone, drop from local state." After `delete.retention.ms=24h`, Kafka's log cleaner garbage-collects the key.

6. **Old-key tombstone on location change.** `nodeLocationChanged` changes the partition key (`{location}@{nodeId}`). The listener handles it as two synchronous sends: a tombstone at the **old** `{oldLocation}@{nodeId}` key plus a fresh `NodeContext` at the new `{newLocation}@{nodeId}` key. This prevents stale records at the old key from haunting the compacted log.

7. **Feature-flag kill switch.** `DELTAV_NODE_CONTEXT_ENABLED=true` by default (Phase 1 option C — on everywhere from day one). `@ConditionalOnProperty(matchIfMissing=true)` gates all seven new beans. Flipping to `false` and restarting provisiond removes the beans entirely; the topic sits idle, existing data unchanged.

8. **Schema freeze deferred.** Phase 0's spec stated schema freeze begins when Phase 2 ships any real consumer. Phase 1 has no consumer, so the `NodeContext` schema stays breakable (forward-incompatible changes permitted) through Phase 1. After Phase 2a (Prometheus Write) ships, schema becomes append-only.

## Protobuf contracts

**No schema changes.** `deltav-node-context.proto` was committed in Phase 0 and stays frozen for Phase 1. The `deleted = 11` field exists and is used as-is.

**Structural change: extract to shared contracts module.** Phase 0 parked both `.proto` files in `core/daemon-boot-collectd/src/main/proto/` because there was only one producer. Phase 1 introduces a second producer, so:

```
core/deltav-kafka-contracts/
├── pom.xml                          (applies protobuf-maven-plugin, nothing else)
└── src/main/proto/
    ├── deltav-timeseries.proto      (moved from daemon-boot-collectd)
    └── deltav-node-context.proto    (moved from daemon-boot-collectd)
```

Both `daemon-boot-collectd` and `daemon-boot-provisiond` add `core/deltav-kafka-contracts` as a `compile`-scope dep and drop their own `src/main/proto/` + protobuf-maven-plugin configs. Generated classes stay in `org.deltav.timeseries.proto` (same package as today).

This eliminates the risk of schema drift between daemons that consume the same topic and prevents the same structural debt from recurring for every future producer (Pollerd, PerspectivePollerd, SnmpCollector-on-Minion).

## Component layout

All new code in `core/daemon-boot-provisiond/src/main/java/org/deltav/netmgt/provision/nodecontext/`:

```
NodeContextProducerConfiguration.java   @Configuration + @ConditionalOnProperty(matchIfMissing=true)
NodeContextChangeFeedListener.java      @EventListener with 13 @EventHandler methods
NodeContextDebouncer.java                per-nodeId cancel-and-reschedule
NodeContextPublisher.java                translate + send orchestration
NodeToProtobufTranslator.java            pure function, OnmsNode → NodeContext
NodeContextBootstrapRunner.java          SmartLifecycle, enumerate + publish on start
```

Package choice matches existing `daemon-boot-provisiond` convention (`org.deltav.netmgt.provision.boot`, `org.deltav.netmgt.provision.snmp`, etc.) — deviating from the scaffold's `org.deltav.provisiond.nodecontext` would break the prevailing structure.

### Component responsibilities

**`NodeToProtobufTranslator`** — Pure function, no Spring state, no DB access, no logging (callers log). Public API:
- `NodeContext translate(OnmsNode node, long updatedAtMs)` — walks OnmsNode + interfaces + services + metadata, returns populated `NodeContext`
- `NodeContext tombstone(int nodeId, String location, long updatedAtMs)` — returns `NodeContext { node_id, location, updated_at_ms, deleted=true }`, everything else default

Exhaustively unit-tested against synthetic `OnmsNode` instances. Service-metadata key format is `{ipAddress}/{serviceName}`. Interface-metadata key format is the InetAddress string (IPv4 or IPv6). IPv6 keying uses the canonical RFC-5952 form (no collision risk with IPv4).

**`NodeContextPublisher`** — Orchestration. Constructor takes `StreamBridge`, `NodeDao`, `TransactionTemplate`, `MeterRegistry`, `NodeToProtobufTranslator`. Public API:
- `void publishNode(int nodeId)` — read-only transaction → `nodeDao.get(nodeId)` → translate → serialize → size-check → `streamBridge.send`, `reason="change"` label
- `void publishTombstone(int nodeId, String location)` — no DB read, translator's `tombstone()` helper, `reason="tombstone"` label
- `void publishRelocation(int nodeId, String oldLocation, String newLocation)` — `publishTombstone(id, oldLocation)` + `publishNode(id)` (whose send will use the new location via DB read), `reason="relocation_old_key"` and `"relocation_new_key"` labels respectively

All methods are error-isolated: any failure logs + increments a failure counter, never throws. Metrics per Section below.

**`NodeContextDebouncer`** — `ConcurrentHashMap<Integer, ScheduledFuture<?>>` + `ScheduledExecutorService` (2 threads default, via `DELTAV_NODE_CONTEXT_DEBOUNCE_THREADS`). Public API:
- `void enqueueUpdate(int nodeId)` — atomically cancel any pending future for that nodeId, schedule a new one `debounceMs` (default 250) out calling `publisher.publishNode(nodeId)`. Cancellation is resetting (leading + trailing edge debounce).
- `void evict(int nodeId)` — cancel any pending future for that nodeId, do not reschedule. Called by delete handler to prevent queued update from racing past a tombstone.
- `void flushAndClose()` — called from listener's `SmartLifecycle.stop`. Iterates pending futures, fires each synchronously (cancels the scheduled delay, invokes the target work directly), then shuts the executor with a 5-second wait for in-progress tasks.

Each cancel-and-reschedule increments `deltav_node_context_debounce_coalesced_total`. Current pending size is exposed as `deltav_node_context_debounce_pending_gauge`.

**`NodeContextChangeFeedListener`** — `@EventListener(name="NodeContextChangeFeed")` class. Constructor takes `NodeContextDebouncer`, `NodeContextPublisher`, `NodeDao`. `@EventHandler` methods:

| UEI | Handler behavior |
|---|---|
| `NODE_ADDED_EVENT_UEI` | `debouncer.enqueueUpdate(event.getNodeid())` |
| `NODE_UPDATED_EVENT_UEI` | `debouncer.enqueueUpdate(event.getNodeid())` |
| `NODE_LABEL_CHANGED_EVENT_UEI` | `debouncer.enqueueUpdate(event.getNodeid())` |
| `NODE_INFO_CHANGED_EVENT_UEI` | `debouncer.enqueueUpdate(event.getNodeid())` |
| `NODE_CATEGORY_MEMBERSHIP_CHANGED_EVENT_UEI` | `debouncer.enqueueUpdate(event.getNodeid())` |
| `ASSET_INFO_CHANGED_EVENT_UEI` | `debouncer.enqueueUpdate(event.getNodeid())` |
| `NODE_GAINED_INTERFACE_EVENT_UEI` | `debouncer.enqueueUpdate(event.getNodeid())` |
| `INTERFACE_DELETED_EVENT_UEI` | `debouncer.enqueueUpdate(event.getNodeid())` |
| `NODE_GAINED_SERVICE_EVENT_UEI` | `debouncer.enqueueUpdate(event.getNodeid())` |
| `SERVICE_DELETED_EVENT_UEI` | `debouncer.enqueueUpdate(event.getNodeid())` |
| `NODE_DELETED_EVENT_UEI` | Pull location from event parms (post-commit, the DB row is already deleted — the event must carry location or we skip with a `reason="missing_location"` failure counter). `publisher.publishTombstone(nodeId, location)`. `debouncer.evict(nodeId)`. |
| `NODE_LOCATION_CHANGED_EVENT_UEI` | Pull `oldLocation` + `newLocation` from event parms. `publisher.publishRelocation(nodeId, oldLocation, newLocation)`. `debouncer.evict(nodeId)`. |
| `IMPORT_SUCCESSFUL_UEI` | Pull `foreignSource` parm. For each `OnmsNode` in `nodeDao.findByForeignSource(foreignSource)`: `debouncer.enqueueUpdate(node.getId())`. Catch-all for metadata writes that didn't fire `nodeUpdated`. |

Each handler extracts `event.getNodeid()` with a range guard (long → int). If the UEI carries a null nodeId (shouldn't happen for any of these UEIs), log WARN + increment `reason="malformed_event"` counter + return.

Registered via `AnnotationBasedEventListenerAdapter` against `@Qualifier("kafkaEventSubscriptionService")` — same pattern as `provisiondEventListener`, `snmpAssetEventListener`, and the other two existing listeners in `ProvisiondBootConfiguration`.

Implements `SmartLifecycle`. `stop()` delegates to `debouncer.flushAndClose()` so pending updates are flushed before shutdown.

**`NodeContextBootstrapRunner`** — `SmartLifecycle` with `getPhase()` numerically **greater than** `provisiondLifecycle` (later start, earlier stop). Constructor takes `NodeContextPublisher`, `NodeDao`, `TransactionTemplate`, `MeterRegistry`. `start()`:

1. `INFO` log: "Bootstrap starting"
2. `transactionTemplate.execute(status -> { status.setReadOnly(true); ... })` wrapping a `nodeDao.findAll()` stream
3. For each node: `publisher.publishNode(node.getId())` with `reason="bootstrap"` metric tag (publisher takes a `reason` parameter, defaulting to `"change"`)
4. Progress log every 1000 nodes
5. Record `deltav_node_context_bootstrap_duration_seconds` timer
6. `INFO` log: "Bootstrap complete, published N records"

`stop()` is a no-op (bootstrap is run-once, not long-running).

**`NodeContextProducerConfiguration`** — `@Configuration` gated on `@ConditionalOnProperty(name="deltav.node-context.enabled", havingValue="true", matchIfMissing=true)`. Declares:

- `NodeToProtobufTranslator` bean
- `NodeContextPublisher` bean
- `NodeContextDebouncer` bean (with `@Value` injection for debounce-ms / threads)
- `NodeContextChangeFeedListener` bean
- `AnnotationBasedEventListenerAdapter nodeContextChangeFeedEventListener` binding the above to `kafkaEventSubscriptionService`
- `NodeContextBootstrapRunner` bean
- `NewTopic deltavNodeContextTopic` bean (**moved here**; deleted from `TimeseriesKafkaPublisherConfiguration` in the same PR)

**`ProvisiondApplication.scanBasePackages`** gets `"org.deltav.netmgt.provision.nodecontext"` added to the existing three-entry list, per `feedback_spring_boot_scan_package_trap`.

## Spring Cloud Stream binding

Added to `core/daemon-boot-provisiond/src/main/resources/application.yml`:

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:kafka:9092}
  cloud:
    stream:
      bindings:
        publishNodeContext-out-0:
          destination: deltav-node-context
          producer:
            partition-count: ${DELTAV_NODE_CONTEXT_PARTITIONS:8}
            use-native-encoding: true
      kafka:
        bindings:
          publishNodeContext-out-0:
            producer:
              configuration:
                key.serializer: org.apache.kafka.common.serialization.ByteArraySerializer
                value.serializer: org.apache.kafka.common.serialization.ByteArraySerializer
                compression.type: lz4
                linger.ms: 50
                batch.size: 32768
                acks: all
                enable.idempotence: true
                max.in.flight.requests.per.connection: 5

deltav:
  node-context:
    enabled: ${DELTAV_NODE_CONTEXT_ENABLED:true}
    partitions: ${DELTAV_NODE_CONTEXT_PARTITIONS:8}
    replication-factor: ${DELTAV_NODE_CONTEXT_REPLICATION_FACTOR:1}
    debounce-ms: ${DELTAV_NODE_CONTEXT_DEBOUNCE_MS:250}
    debounce-threads: ${DELTAV_NODE_CONTEXT_DEBOUNCE_THREADS:2}
```

Config notes:
- `spring.kafka.bootstrap-servers` is **required** — missing in Phase 0 triggered the #170 cascade. `KafkaAdmin` can't provision the topic without it.
- `batch.size: 32768` is half of Phase 0's 65536 because node-context records are bursty-low-volume vs timeseries; smaller batches mean lower publish latency.
- `use-native-encoding: true` passes byte-array payloads unchanged (no JSON message conversion), matching Phase 0 and flow-enricher conventions.
- `acks: all` + `enable.idempotence: true` + `max.in.flight.requests.per.connection: 5` are the strongest durability guarantees consistent with per-partition ordering under idempotence.

## Topic configuration

`deltav-node-context` stays exactly as Phase 0 declared it (no config drift during the handoff):

| Setting | Value | Rationale |
|---|---|---|
| Name | `deltav-node-context` | Unchanged |
| Partitions | 8 (configurable via `DELTAV_NODE_CONTEXT_PARTITIONS`) | Unchanged |
| Replication factor | 3 in production, 1 in dev (via `DELTAV_NODE_CONTEXT_REPLICATION_FACTOR`) | Unchanged |
| `cleanup.policy` | `compact` | Latest-per-key semantics |
| `retention.ms` | `-1` | Compaction retains, not time-based retention |
| `min.compaction.lag.ms` | `60000` (1 min) | Consumers bootstrapping from offset 0 see recent updates before compaction runs |
| `delete.retention.ms` | `86400000` (24 h) | Tombstone retention window — gives consumers 24 h to see deletions |
| Key | `{location}@{node_id}` UTF-8 bytes | Matches `TimeseriesBatch` key shape for direct KTable join |
| Value | `NodeContext` protobuf bytes | Per Phase 0 schema |

**Handoff mechanics:** `KafkaAdmin.createTopics` is idempotent. Both modules declaring the same `NewTopic` bean is fine during a rolling deploy — whichever runs first wins, the second is a no-op. Delete the Collectd-side bean in the same PR so only one source of truth survives in code.

## Related change — Collectd timeseries retention 7 → 1 day

Same PR, one-line each:

- `core/daemon-boot-collectd/src/main/resources/application.yml`: `retention-days: ${DELTAV_TIMESERIES_RETENTION_DAYS:1}` (was `7`)
- `core/daemon-boot-collectd/src/main/java/org/deltav/collectd/timeseries/TimeseriesKafkaPublisherConfiguration.java`: `@Value("${deltav.timeseries.retention-days:1}")`

Rationale: at production scale (10k nodes × 288 polls/day × ~2 KB per record) the difference is 40 GB → 5.76 GB steady-state. Operator can still override via `DELTAV_TIMESERIES_RETENTION_DAYS`. Phase 0 shipped earlier on 2026-04-16; no operator has yet relied on the 7-day default.

## Error handling and observability

### Failure isolation

Every external-state interaction is wrapped in try/catch that logs + increments a failure counter with a `reason` tag and never rethrows. Guiding principle: no single-node failure stops the listener, no Kafka hiccup breaks provisiond.

| Failure point | Reason tag | Log level |
|---|---|---|
| `nodeDao.get(id)` returns null | `node_not_found` | DEBUG |
| `nodeDao.get(id)` or metadata fetch throws | `db_read_error` | WARN |
| `translator.translate()` throws | `translator_error` | WARN |
| `batch.toByteArray()` throws | `serialization_error` | WARN |
| `streamBridge.send()` throws or returns false | `kafka_send_error` | WARN |
| Debouncer executor rejects task | `debouncer_rejected` | DEBUG (only happens during shutdown) |
| Bootstrap enumeration throws | `bootstrap_error` | ERROR (but continue with remaining nodes) |
| Event carries null nodeId | `malformed_event` | WARN |
| `nodeDeleted` / `nodeLocationChanged` event missing required location parm | `missing_location` | WARN |

### Size safety

Post-serialize check: if `payload.length > 800_000` bytes, log WARN with `nodeId` + `foreignSource`, increment `deltav_node_context_record_size_warning_total`, and **still publish** (don't drop — operator fixes at source by trimming metadata). Same threshold and policy as Phase 0.

### Metrics exposed on `/actuator/prometheus`

| Metric | Type | Tags | Purpose |
|---|---|---|---|
| `deltav_node_context_records_published_total` | counter | `location`, `producer="provisiond"`, `reason` | Successful publishes. `reason` ∈ {`change`, `bootstrap`, `tombstone`, `relocation_old_key`, `relocation_new_key`} |
| `deltav_node_context_records_failed_total` | counter | `location`, `reason` | Failures; reason per table above |
| `deltav_node_context_record_size_bytes` | distribution summary | `location` | Wire-size distribution (protobuf, pre-compression) |
| `deltav_node_context_record_size_warning_total` | counter | `location` | Oversized-record warnings (>800 KB) |
| `deltav_node_context_publish_duration_seconds` | timer | `location`, `reason` | End-to-end publish latency including DB read |
| `deltav_node_context_debounce_coalesced_total` | counter | - | Events that hit an existing pending future (measurable amplification reduction) |
| `deltav_node_context_debounce_pending_gauge` | gauge | - | Current pending-future count (health signal) |
| `deltav_node_context_bootstrap_duration_seconds` | timer | - | One-shot, total bootstrap wall-clock |

Deviation from the next-session scaffold: the scaffold listed a separate `deltav_node_context_bootstrap_records_total` counter. Dropped here — covered by `records_published_total{reason="bootstrap"}`. Two debouncer meters added because they tell operators whether coalescing is working.

## Testing strategy

Five-layer pattern matching Phase 0.

### Layer 1 — Translator unit tests

`NodeToProtobufTranslatorTest` — pure function, exhaustive, sub-millisecond. ~18 tests covering:
- Full node → `NodeContext` with every field populated
- Empty / null edge cases (empty label, null location, no categories, no metadata)
- Metadata across OnmsMetaData contexts formatted as `{context}:{key}`
- IPv4 and IPv6 interface-metadata keying
- Service-metadata keying as `{ip}/{serviceName}`
- `tombstone(nodeId, location)` shape
- Unicode labels and metadata values
- Large node (100 interfaces × 50 metadata entries)

### Layer 2 — Publisher + Debouncer unit tests

`NodeContextPublisherTest` (~14 tests, mocked dependencies):
- Happy path `publishNode` → one correctly-shaped send
- `NodeDao.get` returns null → `node_not_found` counter
- `NodeDao.get` throws → `db_read_error`
- Translator throws → `translator_error`
- `streamBridge.send` returns false → `kafka_send_error`
- `streamBridge.send` throws → `kafka_send_error`
- Oversized payload → warning counter increments, send still happens
- `publishTombstone` → correct record shape with `deleted=true`
- `publishRelocation` → two sends, correct counters
- Partition key edge cases: location containing `@`, nodeId=0

`NodeContextDebouncerTest` (~8 tests with deterministic scheduler):
- Single enqueue → one publish after window
- Burst of 6 enqueues in 10 ms → one publish after window-from-last
- Different nodeIds → independent parallel publishes
- `evict(id)` cancels pending
- `flushAndClose` fires all pending synchronously
- Task rejection during shutdown → logged counter, no throw

### Layer 3 — Listener SCS test-binder IT

`NodeContextChangeFeedListenerIT` (~10 tests) using `spring-cloud-stream-test-binder` + `OutputDestination`:
- Each supported UEI fires → expected record shape arrives
- `nodeDeleted` → tombstone arrives, debouncer pending is 0
- 10 consecutive `nodeUpdated` for same nodeId → 1 record after debounce window
- `nodeLocationChanged` → two records (old-key tombstone + new-key publish)
- `IMPORT_SUCCESSFUL_UEI` → one record per node in the foreign source
- Feature-flag off: beans absent, zero records on any event

### Layer 4 — Testcontainers Kafka IT

`NodeContextKafkaIT` (~6 tests) with a real `KafkaContainer`:
- `AdminClient.describeConfigs` confirms topic provisioned with `cleanup.policy=compact`, `min.compaction.lag.ms=60000`, `delete.retention.ms=86400000`, 8 partitions
- Produce 50 same-key records over 120 s → wait past compaction lag → consumer from offset 0 sees only latest record (real compaction verified)
- Tombstone after live record, same key → consumer from offset 0 sees only the tombstone
- Idempotent-producer retry: drop a broker connection → no duplicate records
- Partition distribution: 100 records for 8 keys → every partition used, sticky by key
- Flag off: boot context, topic still provisioned (NewTopic bean), but no records produced

### Layer 5 — E2E Docker Compose

`opennms-container/delta-v/test-node-context-e2e.sh` following the `test-timeseries-e2e.sh` pattern:

1. `docker compose up`
2. Wait for provisiond `/actuator/health` → UP
3. Tail `deltav-node-context` via `kcat` → bootstrap records land
4. Provision test node via REST (foreignSource `phase1-test`, two metadata entries)
5. Wait ≤ 5 s → new `NodeContext` record with matching fields
6. Update label via requisition → second record with updated label
7. Delete node → tombstone record arrives
8. Assert `deltav_node_context_records_published_total{reason="change"} > 0` and `{reason="tombstone"} > 0` on Prometheus endpoint
9. Assert `deltav_node_context_records_failed_total` all-zero
10. Tear down cleanly

### Mandatory additional test — real-main-class IT

Per `feedback_spring_boot_scan_package_trap`:

`NodeContextProducerSpringContextIT` — `@SpringBootTest(classes = ProvisiondApplication.class, properties = "deltav.node-context.enabled=true")` with `ApplicationContext.getBean()` assertions for each new bean. Catches the scan-package trap at build time.

Target total: ~60 tests + 1 E2E script.

## Rollout

### Phase 1a — this PR

`DELTAV_NODE_CONTEXT_ENABLED=true` everywhere by default (Q6 option C). Producer ships, topic handoff from Collectd completes, 5-layer tests pass, E2E script is CI-green. First `docker compose up` after merge: provisiond's bootstrap pass publishes every node, subsequent changes publish as events arrive. Collectd's `deltav-timeseries` retention flips 7 → 1 day in the same PR.

### Phase 1b — measure amplification

A few days of dev usage. Monitor:
- `deltav_node_context_debounce_coalesced_total / deltav_node_context_records_published_total` — debouncer effectiveness
- `deltav_node_context_publish_duration_seconds` histogram — DB + send latency stability
- `deltav_node_context_records_failed_total` — should remain zero; any persistent failure path investigated

Tune `DELTAV_NODE_CONTEXT_DEBOUNCE_MS` via env without code change if needed.

### Phase 1c — schema freeze

Once **any** Phase 2 consumer (Prometheus Write, Thresholder, Streaming Telemetry) merges, the `NodeContext` schema is locked. Only forward-compatible changes (new fields at new tag numbers, new enum values) allowed from that point. Phase 1 retains breaking-change permission until that moment.

## Rollback

Producer rollback is trivial: `DELTAV_NODE_CONTEXT_ENABLED=false`, restart provisiond. `@ConditionalOnProperty` removes all seven beans — listener, debouncer, publisher, translator, bootstrap runner, config, and NewTopic bean — from the context. The topic is not deleted (Kafka convention); existing records stay. No state to unwind, no DB changes to revert.

If a specific bug surfaces post-merge and a targeted rollback is needed but full kill-switch is undesirable, the flag granularity is all-or-nothing for Phase 1. If bug-specific flags become valuable (e.g., `deltav.node-context.bootstrap.enabled` separate from producer enabled), add them in a followup — premature for Phase 1.

## Operator documentation

`opennms-container/delta-v/README.md` updates:

- **Feature flag**: `DELTAV_NODE_CONTEXT_ENABLED` (default `true`, kill switch)
- **Topic**: `deltav-node-context` — 8 partitions, compacted, ~2 KB per record, ~20 MB steady state for 10k nodes
- **Env vars**: `DELTAV_NODE_CONTEXT_PARTITIONS`, `DELTAV_NODE_CONTEXT_REPLICATION_FACTOR`, `DELTAV_NODE_CONTEXT_DEBOUNCE_MS`, `DELTAV_NODE_CONTEXT_DEBOUNCE_THREADS`
- **Metrics**: full list from Section above
- **Bootstrap behavior**: every provisiond restart republishes all nodes; compaction absorbs duplicates within 60 s
- **Known limitations**:
  - Metadata written to the DB without firing a `nodeUpdated` / `nodeInfoChanged` / `nodeLabelChanged` UEI is invisible until the next UEI or restart bootstrap. No horizon `metadataChanged` UEI exists.
  - Schema breakable until Phase 2 ships a consumer; document that any operator tool consuming `deltav-node-context` during Phase 1 must tolerate schema evolution.
- **Related retention change**: `deltav-timeseries` default retention is now 1 day (down from 7); override via `DELTAV_TIMESERIES_RETENTION_DAYS`

## Risks

### R1 — Metadata drift from non-event writes
**Likelihood:** low-to-moderate. `SnmpMetadataProvisioningAdapter` writes metadata as part of a scan that fires `nodeUpdated` on completion. REST API metadata writes (if built) should also fire `nodeUpdated`. Direct DAO writes would drift.
**Mitigation:** `IMPORT_SUCCESSFUL_UEI` catch-all + bootstrap-on-restart cover most windows. Document the gap.
**Acceptance:** if drift becomes a real issue, add a time-based "full republish" `@Scheduled` task at Phase 1.5, or invest in a proper `metadataChanged` UEI upstream.

### R2 — Restart republish amplification
**Likelihood:** certain; ~N records per restart where N = node count.
**Mitigation:** log compaction absorbs within 60 s; disk cost negligible even at 10k nodes × 10 restarts/day.
**Acceptance:** explicit; bundled with the sidecar redesign (`project_provisioning_adapters_as_sidecars.md`) for future smart-skip optimization.

### R3 — Circular in-process event flow
**Likelihood:** architectural, constant.
**Mitigation:** documented in memory. Revisit with sidecar evaluation.
**Acceptance:** matches delta-v's existing pattern; no blockers.

### R4 — Write amplification during imports
**Likelihood:** certain without mitigation.
**Mitigation:** per-nodeId debouncer with 250 ms default window collapses bursts; measurable via `deltav_node_context_debounce_coalesced_total`.
**Acceptance:** instrumented; tune at Phase 1b if measurements show ineffective coalescing.

### R5 — Schema drift between producer and consumer modules
**Likelihood:** would become certain as more daemons touch the topics.
**Mitigation:** Phase 1 extracts `.proto` to `core/deltav-kafka-contracts` — single source of truth. Eliminates the drift class before it can bite.
**Acceptance:** done as part of this PR.

### R6 — Scan-package gap (silent feature disable)
**Likelihood:** certain if not fixed explicitly (see #170 cascade).
**Mitigation:** `ProvisiondApplication.scanBasePackages` gets the new package added, and a real-main-class IT asserts all beans wire.
**Acceptance:** covered by the mandatory additional test.

### R7 — Missing `spring.kafka.bootstrap-servers`
**Likelihood:** certain without explicit config (see #170 cascade).
**Mitigation:** property added to `application.yml` in this PR.
**Acceptance:** called out in Section above.

### R8 — Oversized `NodeContext` for heavily-metadataed nodes
**Likelihood:** low (800 KB threshold, metadata would need to be enormous).
**Mitigation:** size-warning counter + WARN log; still publishes (doesn't drop). Same policy as Phase 0.
**Acceptance:** operator-side fix by trimming metadata.

### R9 — Bootstrap lag at large scale
**Likelihood:** moderate as deployments grow; at current hundreds-of-nodes scale, bootstrap is sub-second.
**Mitigation:** `NodeContextBootstrapRunner` is a plain `SmartLifecycle`; if blocking cost becomes real, flip to async via a config flag in Phase 1.5.
**Acceptance:** defer until measurement shows real pain.

## Success criteria

- [ ] All five test layers pass, plus the real-main-class IT
- [ ] Feature flag verified as a clean kill switch (Layer 3)
- [ ] `deltav_node_context_*` metrics exposed on `/actuator/prometheus`, including debouncer effectiveness meters
- [ ] `AdminClient.describeConfigs` confirms topic provisions with correct compaction config (Layer 4)
- [ ] Real Kafka compaction round-trip verified (Layer 4)
- [ ] E2E script ships and passes — provisions, updates, deletes a node through REST and observes correct Kafka records
- [ ] `deltav-kafka-contracts` module shipped; both daemon-boot modules migrated to depend on it; no duplicate `.proto` files
- [ ] `deltavNodeContextTopic` bean deleted from `TimeseriesKafkaPublisherConfiguration`, declared in `NodeContextProducerConfiguration`
- [ ] `deltav-timeseries` retention default 7 → 1 day
- [ ] Operator docs updated per Section above
- [ ] Design doc committed before implementation begins

## Explicit non-commitments

- **No smart-skip restart optimization.** Bundled with sidecar redesign per `project_provisioning_adapters_as_sidecars.md`.
- **No time-based republish scheduler.** Only added if metadata drift becomes observable.
- **No debounce self-tuning.** Window is configurable via env; we observe and adjust manually at Phase 1b.
- **No per-UEI flag granularity.** All-or-nothing kill switch for Phase 1.
- **No schema freeze.** Defers until Phase 2 consumer ships.
- **No consumer guarantee.** Phase 2a/b/c ordering is a separate decision.
