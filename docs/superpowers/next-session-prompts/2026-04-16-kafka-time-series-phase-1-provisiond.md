# Next Session — Kafka Time Series Pipeline: Phase 1 (Provisiond Change Feed)

**Created:** 2026-04-16
**Predecessor:** `docs/superpowers/next-session-prompts/2026-04-16-kafka-time-series-producer.md` (DONE — shipped in delta-v#169 + #170)
**Phase 0 state:** Collectd Kafka Time Series producer is live on develop. First live poll cycle publishes 4 protobuf records to `deltav-timeseries` with real SNMP metric data. `deltav_timeseries_batches_published_total` counter increments on every poll. Feature flag `DELTAV_TIMESERIES_ENABLED` remains a clean kill switch.

## Why Phase 1 exists

Phase 0 carries only numeric identity on the wire (`node_id`, `location`, `collection_package`, `producer`). Every consumer that wants human-meaningful labels (`node_label`, `foreign_source`, `foreign_id`, categories, the full OnmsMetaData surface) must join against the **`deltav-node-context`** compacted topic. That topic's schema is already committed (`core/daemon-boot-collectd/src/main/proto/deltav-node-context.proto`), the `NewTopic` bean already provisions it (8 partitions, log-compacted, 60s min-compaction-lag, 24h tombstone retention), but **nothing produces to it yet**. No consumer can be meaningfully built until Phase 1 ships.

Phase 1 is the provisiond change feed that writes to `deltav-node-context`.

## What to build in Phase 1

### 1. Provisiond producer for `deltav-node-context`

In `core/daemon-boot-provisiond/src/main/java/org/deltav/provisiond/nodecontext/` (or equivalent — confirm the module name and existing package layout before choosing):

- **`NodeContextProducer.java`** — singleton bean that on each provisioning event (node added, updated, deleted, metadata changed) builds a `NodeContext` protobuf and publishes via `StreamBridge.send("publishNodeContext-out-0", Message<byte[]>)` with key `{location}@{node_id}` UTF-8.
- **`NodeToProtobufTranslator.java`** — pure function, walks `OnmsNode` + `OnmsIpInterface` + `OnmsMetaData` surface → `NodeContext` protobuf. Deletion publishes a tombstone with `deleted=true`.
- **`NodeContextProducerConfiguration.java`** — `@Configuration` + `@ConditionalOnProperty("deltav.node-context.enabled")`, declares the producer + translator beans. The `NewTopic` bean should **move here from Collectd** (dual-ownership was deliberate for Phase 0; Phase 1 is the handoff — delete it from `TimeseriesKafkaPublisherConfiguration` and own it here).

### 2. Provisioning event wiring

Hook into provisiond's existing event stream (likely `ProvisioningAdapter` or the Hibernate entity listener path — verify the exact hook point before writing code). For each event:

- **Node created / updated / imported / metadata-changed** → publish a full `NodeContext` with `updated_at_ms = System.currentTimeMillis()`, `deleted = false`.
- **Node deleted** → publish a tombstone: `NodeContext { node_id, location, updated_at_ms, deleted = true }` — nothing else set. Compaction eventually garbage-collects the key after `delete.retention.ms = 24h`.
- **Bootstrap** — on startup, enumerate all current nodes and publish one `NodeContext` per node. This seeds new consumers (a `GlobalKTable` subscribes from offset 0 and materializes current state before processing any metric records).

### 3. Spring Cloud Stream binding

In `core/daemon-boot-provisiond/src/main/resources/application.yml` (pattern already set by Collectd):

- Output binding `publishNodeContext-out-0` → `deltav-node-context`
- `use-native-encoding: true`, `acks: all`, `enable.idempotence: true`
- `spring.kafka.bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:kafka:9092}` — **don't forget this**; Phase 0's #170 cascade started because this property was missing.

### 4. Component-scan sanity check

Before writing any code, confirm `ProvisiondApplication.scanBasePackages` includes (or will include) `org.deltav.provisiond.nodecontext`. See `feedback_spring_boot_scan_package_trap` memory — a scan-package gap silently disables an entire `@Configuration`.

### 5. Five-layer test suite (same pattern as Collectd)

- **Layer 1 (translator)** — `NodeToProtobufTranslatorTest`: exhaustive coverage of `OnmsNode` → `NodeContext` mapping, metadata surface, IP-address map keys, service-scope map keys (`{ip}/{service}`), tombstone shape, unicode in labels.
- **Layer 2 (producer)** — `NodeContextProducerTest`: mocked `StreamBridge` + `MeterRegistry`. Assert partition key, successful publish counter, failure counters (serialization, Kafka send), null-node guards.
- **Layer 3 (SCS test-binder IT)** — round-trip with `OutputDestination`, flag kill-switch verification.
- **Layer 4 (Testcontainers Kafka IT)** — `AdminClient.describeConfigs` confirms `deltav-node-context` provisions with `cleanup.policy=compact`, `min.compaction.lag.ms=60000`, `delete.retention.ms=86400000`. Real compaction round-trip (produce two records with same key, wait for log cleaner, verify only latest survives).
- **Layer 5 (E2E)** — `opennms-container/delta-v/test-node-context-e2e.sh`: docker compose up, provision one node via REST, assert a `NodeContext` record lands on `deltav-node-context` with matching `foreign_source`/`foreign_id`.

### 6. NewTopic bean handoff

Delete `deltavNodeContextTopic` bean from `TimeseriesKafkaPublisherConfiguration` (Collectd) in the same PR that introduces it in Provisiond. `KafkaAdmin.createTopics` is idempotent — if the topic already exists with the requested config, it's a no-op, so there's no race during the handoff.

### 7. Metrics on `/actuator/prometheus`

- `deltav_node_context_records_published_total{location,producer="provisiond",reason}`
- `deltav_node_context_records_failed_total{location,reason}`
- `deltav_node_context_record_size_bytes{location}` (distribution summary)
- `deltav_node_context_publish_duration_seconds{location}` (timer)
- `deltav_node_context_bootstrap_records_total{location}` (seed-publish counter — only increments during startup enumeration)

### 8. Operator documentation

README updates in `opennms-container/delta-v/README.md` — feature flag (`DELTAV_NODE_CONTEXT_ENABLED`), metrics, bootstrap behavior, known limitations.

## Architecture context (do not re-litigate)

These are settled by the Phase 0 spec (`docs/superpowers/specs/2026-04-15-kafka-time-series-producer-design.md`):

- **Keyed `{location}@{node_id}`** — same shape as `TimeseriesBatch` so consumers join directly.
- **Compacted topic** — latest record per key survives forever.
- **Tombstones are explicit records with `deleted=true`** (not null values). Consumers treat tombstoned keys as "not present."
- **Full metadata surface is carried verbatim.** No hand-picking of "useful" fields — producer writes the full `OnmsMetaData` map; consumers decide what to use.
- **Minion Zero Trust preserved.** Provisiond runs server-side with DB access; Minion is not involved.

## Phase 2 and beyond — queued followups

After Phase 1 ships, the next choices are:

### Phase 2a — first consumer service (Prometheus Remote Write)

Simplest consumer to validate the GlobalKTable join pattern end-to-end. Reads `deltav-timeseries`, joins against `deltav-node-context` GlobalKTable, emits Prometheus Remote Write protocol to an external Prometheus. This is the "we have a working pipeline" milestone — once it's in place, operators can see Collectd metrics in Grafana with full identity labels.

### Phase 2b — Pollerd + PerspectivePollerd producers

Extend the Collectd producer pattern to the other two response-time producers. Wire format is already producer-agnostic; this is mechanical. Same `FanoutPersister` isolation pattern.

### Phase 2c — Thresholder consumer

Standard-deviation thresholding as an SCS consumer. Replaces inline `ThresholdingVisitor` (currently already amputated from Collectd per `CollectdDaemonConfiguration.java`). See `project_thresholder_brainstorm` memory.

### Cleanup items (can be bundled into any Phase 1/2 PR, or done standalone)

- **Fix the 3 pre-existing Delta-V Collectd inner-persister bugs** that `FanoutPersister` now swallows with WARN logs:
  1. `UnexpectedRollbackException` from `MetaTagDataLoader` (read-only transaction nested under rollback-only outer txn)
  2. NPE in `TimeseriesPersistOperationBuilder.setAttributeValue` (initialization order)
  3. `NoSuchMethodError: ResourceTypeUtils.getResourcePathWithRepository` (jar-hell vs horizon `AbstractPersister`)

  These are unrelated to the Kafka producer but each causes real SNMP data loss in the InMemoryStorage path. Operators see WARN-spam in logs currently.

- **ServiceParameters identity populate** — small Collectd patch to emit non-empty `location` + `node-id` keys into `ServiceParameters` so `TimeseriesKafkaPersister` records carry real identity instead of defaulting to `nodeId=0`/`location=""`. Until this lands, consumers must always join against `deltav-node-context` to resolve identity (GlobalKTable bootstrap becomes load-bearing rather than optional).

- **Collectd scheduler/publisher split** (R5 from the Phase 0 spec) — larger architectural item tracked in `project_collectd_scheduler_publisher_split.md`. Not needed for Phase 1 or 2 but unlocks horizontal scale + restart resilience.

## Critical memories to check before starting

- `feedback_spring_boot_scan_package_trap` — mandatory read; explains the #169→#170 cascade. Phase 1 must not recreate the same trap.
- `feedback_feature_branches` — feature branch + PR, never direct to develop.
- `feedback_pull_before_branching` — `git pull` before creating the branch.
- `feedback_delta_v_full_reactor_verify` — full delta-v reactor build after the horizon version bump or any cross-module change.
- `feedback_deltav_package_namespace` — `org.deltav.*` packages + Delta-V copyright on new code.
- `feedback_use_jackson_not_jaxb` — Jackson XmlMapper for XML config (not relevant here, but good to remember if any JAXB surfaces).
- `project_kafka_timeseries_producer_next_session` — Phase 0 outcomes + E2E evidence + limitations.
- `project_collectd_scheduler_publisher_split` — tracks cleanup items that can bundle into Phase 1/2.

## How to start the next session

1. Invoke `superpowers:brainstorming` on the Phase 1 scope (provisiond change feed specifically — the "what to build" above is a scaffold, not a spec). Confirm producer-side design decisions: bootstrap ordering, tombstone emission, event-hook injection point.
2. When brainstorming settles, invoke `superpowers:writing-plans` on the resulting spec.
3. Execute via `superpowers:subagent-driven-development` (Phase 0 used this successfully — same cadence).

## What NOT to do in the next session

- Do NOT modify the `TimeseriesBatch` or `NodeContext` schemas. Both are frozen wire contracts once Phase 2 (any consumer) ships. Only forward-compatible changes allowed.
- Do NOT build consumer services in the same PR as Phase 1. Keep the producer scope clean; let the GlobalKTable pattern prove itself in a separate phase.
- Do NOT re-enable or rewrite the horizon `TimeseriesPersister`'s read-only transaction path as part of this work — that's a Delta-V cleanup item, not a Kafka pipeline item.
- Do NOT skip the real-main-class IT. `@SpringBootTest(classes = ProvisiondApplication.class)` with the flag on + assert `ctx.getBean(NodeContextProducer.class)` is a required test after `feedback_spring_boot_scan_package_trap`.

## Provenance

Phase 0 shipped in delta-v#169 (producer + 40 tests) and delta-v#170 (4 post-merge integration fixes — scan-package, spring.kafka.bootstrap-servers, storage-strategy classpath, FanoutPersister isolation). Runtime verification on 2026-04-16 confirmed 4 protobuf records landed on `deltav-timeseries` per labbox SNMP poll cycle with real `mib2-interface-errors` + `mib2-X-interfaces` metric data. `DELTAV_TIMESERIES_ENABLED=false` kill switch preserved.
