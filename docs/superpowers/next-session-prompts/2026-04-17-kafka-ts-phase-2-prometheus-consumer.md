# Next-Session Prompt — Kafka TS Phase 2: Prometheus Remote Write consumer

**Kick off brainstorming for the first `deltav-timeseries` consumer.** Phase 0 (Collectd producer, delta-v#169/#170) and Phase 1 (Provisiond node-context producer, delta-v#171) are both live on develop. The topics are ready; there is currently no consumer in delta-v.

Phase 2 ships the first consumer: a Spring Cloud Stream / Kafka Streams service that reads `deltav-timeseries`, enriches each record with node identity from a `deltav-node-context` GlobalKTable, transforms to Prometheus Remote Write protobuf, and POSTs to a configurable RW endpoint (Mimir / Cortex / VictoriaMetrics / Thanos Receive / vanilla Prometheus w/ RW).

---

## Open this prompt by invoking the brainstorming skill

```
/skill superpowers:brainstorming

Brainstorm the Phase 2 Prometheus Remote Write consumer design.

Context, constraints, and open questions are in:
docs/superpowers/next-session-prompts/2026-04-17-kafka-ts-phase-2-prometheus-consumer.md

Read the memory entries listed in the "Memory to load first" section below
before engaging. Then walk through the "Design questions to settle" section
and help me converge on a spec we can hand to an implementation session.
```

---

## Memory to load first

Read these memory files before engaging — they carry load-bearing context that is not in the codebase:

- `project_kafka_timeseries_pipeline` — overall pipeline map (Phase 0 / 1 / 2+ status)
- `project_kafka_timeseries_producer_next_session` — Phase 0 shipping details, wire format, runtime verification from 2026-04-16
- `project_node_context_phase1_done` — Phase 1 shipping details, topic configuration, schema caveats, E2E-discovered bugs
- `feedback_spring_boot_scan_package_trap` — mandatory real-main-class IT before we repeat the #170 discovery scar
- `feedback_spring_cloud_stream_splitter` — `use-native-encoding: true` gotcha for byte-array payloads
- `feedback_delta_v_full_reactor_verify` — full-reactor verify after cross-module changes, not just `-pl`
- `project_dropwizard_prometheus_bridge_pattern` — pattern for exposing horizon-lib Dropwizard meters at `/actuator/prometheus`
- `feedback_deltav_package_namespace` — new code lives under `org.deltav.*` with delta-v copyright header
- `feedback_never_pr_opennms` — PRs go to `pbrane/delta-v`, never `OpenNMS/opennms`

## Wire inputs (already in place)

**`deltav-timeseries`** (Phase 0):
- Key `{location}@{node_id}` UTF-8 bytes
- Value `TimeseriesBatch` protobuf from `org.deltav.timeseries.proto` (shared contract in `core/deltav-kafka-contracts`)
- Each batch carries one poll-cycle CollectionSet — multiple resources, each with attributes
- `cleanup.policy=delete`, `retention.ms=24h` (post-Phase-1 flip), 16 partitions, lz4 compression, idempotent producer

**`deltav-node-context`** (Phase 1):
- Key `{location}@{node_id}` UTF-8 bytes
- Value `NodeContext` protobuf (node_id, location, node_label, foreign_source/id, categories, metadata, interface_metadata, service_metadata, deleted, updated_at_ms)
- `cleanup.policy=compact`, `retention.ms=-1`, `min.compaction.lag.ms=60000`, `delete.retention.ms=24h`, 8 partitions
- Every provisiond restart republishes all nodes; log compaction converges within 60 s

## Design questions to settle

Walk through these in the brainstorming session. Each should end with a decided answer or an explicit "defer" with rationale.

### 1. Deployment shape

- **Standalone service** `prometheus-writer` (new `core/daemon-boot-prometheus-writer` Spring Boot module on the daemon-boot-collectd / flow-enricher pattern)? OR **embed in an existing service** (not recommended — couples consumer lifetime to a producer)?
- Docker Compose profile: `lite`, `passive`, `full`, or a new `metrics` profile?
- Resource footprint target (heap, CPU, partition parallelism)?

### 2. Kafka Streams vs plain Kafka consumer

- **Kafka Streams binder** gives GlobalKTable materialization out of the box for `deltav-node-context` enrichment — lookup by key is O(1), no network hop. Join is stream-to-globaltable. But: RocksDB state store on disk, slightly heavier operational footprint.
- **Plain SCS Kafka binder + in-memory cache** — maintain our own `ConcurrentHashMap<String, NodeContext>` kept current via a dedicated consumer thread on `deltav-node-context`. Simpler footprint, no RocksDB, but reinvents rehydration-on-restart logic.
- Recommendation in prior thinking: Kafka Streams GlobalKTable. Validate or overturn.

### 3. Prometheus Remote Write target(s)

- Is Phase 2 a **single hardcoded target** or a **pluggable list of targets**? (Grafana Mimir for dev, Thanos Receive for prod, VictoriaMetrics for edge?)
- Auth: bearer token, basic auth, mTLS, none? Where does the secret come from — env var, k8s secret mount, Spring Cloud Config?
- Default target for the lite profile docker-compose (so the E2E can verify end-to-end without an external service)? Options: stub HTTP sink container, or add Mimir / VictoriaMetrics as a compose service.

### 4. Metric naming + labels

- How does `mib2-interface-errors.ifInDiscards` become a Prometheus series? Possible convention:
  - `opennms_snmp_mib2_interface_errors_ifindiscards{location, node_id, node_label, foreign_source, instance, ifName, ...category labels...}`
  - Or flatter: `opennms_{collection_type}_{group}_{attribute}` with `instance` label carrying the resource key?
- Which `NodeContext` fields land as labels on every metric? Realistic candidates: `node_id`, `location`, `node_label`, `foreign_source`, `categories` (as multiple label values? joined string?). **Operator cost of labels matters** — Prometheus hates high-cardinality labels like `foreign_id` unless operator opts in.
- Sanitize metric names per Prometheus naming rules (snake_case, valid char set)?

### 5. Counter semantics

- SNMP `COUNTER32`/`COUNTER64` wrap; Prometheus counters monotonically increase and tolerate resets. Two choices:
  - Emit as `_total` Counter and trust Prometheus `rate()` to handle resets (standard). Breaks if we also need wrap-aware delta.
  - Emit as Gauge and compute deltas in a later consumer (avoids wrap issues but loses `rate()` semantics).
- `GAUGE` SNMP attributes → Prometheus Gauge, straightforward.

### 6. Node-context drift between read + publish

- A metric record references `node_id=5` at time T. The enricher looks up NodeContext at time T+ε. If node 5 was deleted between those (tombstone on `deltav-node-context`), enricher sees `deleted=true`. Drop the record? Emit with `deleted="true"` label? Best-effort enrich and let Prometheus handle orphan series?
- A metric arrives for `node_id=999` that has no NodeContext entry at all (bootstrap hasn't materialized yet on this consumer, or node was provisioned after this consumer's GlobalKTable last refreshed). Emit with "unknown" label fallback? Drop + counter? Buffer + retry?

### 7. Staleness + garbage collection

- Prometheus Remote Write has no explicit "clear this series." When a node is deleted (tombstone), its existing Prometheus series will just stop receiving samples and age out via the target's retention. Is that acceptable, or do we need to emit a staleness marker (`value = NaN` with `StaleNaN` bit)?
- Prometheus native staleness marker support in Remote Write: available in current spec. We should probably emit it on deletion to unblock alerting rules faster than retention.

### 8. Backpressure, batching, retry

- RW accepts Snappy-compressed protobuf batches up to ~2 MB. We need: local batching window (e.g. 1 s / 1000 samples / 1 MB, whichever hits first), retry on 5xx with exponential backoff, circuit break on persistent 4xx (malformed metric name poisons the queue if we don't).
- Poison-pill handling: skip + counter + WARN log? DLQ to a `deltav-timeseries-dlq` topic?
- Consumer pause / resume when RW target is down — does SCS Kafka binder give us this natively, or do we build it?

### 9. Observability

Same pattern as Phase 0/1:
- `deltav_prometheus_writer_records_consumed_total{location, producer}` — input rate
- `deltav_prometheus_writer_samples_sent_total{endpoint}` — fan-out (one CollectionSet → N Prometheus samples)
- `deltav_prometheus_writer_samples_failed_total{endpoint, reason}` — `enrichment_missing`, `remote_write_5xx`, `remote_write_4xx`, `network_error`, `serialization_error`
- `deltav_prometheus_writer_batch_size_bytes{endpoint}` — distribution
- `deltav_prometheus_writer_flush_duration_seconds{endpoint}` — latency to RW target
- `deltav_prometheus_writer_globaltable_size_gauge` — GlobalKTable materialization size
- `deltav_prometheus_writer_enrichment_missing_total` — node-context lookup miss rate

### 10. E2E coverage

Docker Compose `test-prometheus-writer-e2e.sh` modeled after `test-node-context-e2e.sh`:
- Start lite stack + Mimir (or a stub HTTP sink container)
- Wait for consumer readiness
- Assert `deltav_prometheus_writer_records_consumed_total > 0` after a minute (Collectd publishes to `deltav-timeseries` on its normal poll cadence)
- Assert `samples_sent_total > 0` and `samples_failed_total == 0`
- Query Mimir `/api/v1/query?query=up` or similar to confirm data landed
- Tear down

Remember the Phase 1 E2E scar: `grep` under `set -euo pipefail` exits 1 on no-match (success path for failure counters). Wrap in `{ ... || true; }`.

## Plan-level outputs from the brainstorming session

The session should produce:

1. **Design doc** at `docs/superpowers/specs/2026-04-17-kafka-ts-phase-2-prometheus-consumer-design.md` covering every decided question above.
2. **A decision log** on the key trade-offs (Kafka Streams vs plain consumer, target shape, naming convention) so future readers understand why we picked what we picked.
3. **A follow-up next-session prompt** that kicks off the *implementation plan* phase (structured by tasks, like the Phase 1 plan at `docs/superpowers/plans/2026-04-16-kafka-time-series-phase-1-node-context.md`).

## Guardrails for the design session

- **Don't skip the brainstorming skill.** Jumping straight to implementation on a multi-consumer design invites the Phase 1-style scar: plan template specifies wrong parm names, nobody caught it until code review.
- **Pin the wire format early.** If Phase 2 needs a `NodeContext` field that isn't there (hostname? IP primary? alarm severity?), decide now whether to (a) add fields to `NodeContext` under Phase 1's "schema still breakable" rule, (b) extend the `TimeseriesBatch` value, or (c) emit without that enrichment and defer. Schema changes once Phase 2 ships become breaking.
- **Plan for the second consumer.** Thresholder comes next; does our first consumer's design prejudge decisions the Thresholder will regret? The GlobalKTable materialization should be shareable across consumers.
- **Keep the E2E honest.** The Phase 1 E2E caught three bugs (commons-io runtime gap, LazyInitializationException, parm-name drift) that unit + integration tests missed. Plan for an equally thorough Phase 2 E2E before the PR opens.
