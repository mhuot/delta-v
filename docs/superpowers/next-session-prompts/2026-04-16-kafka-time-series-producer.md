# Next Session — Kafka Time Series Producer (Implementation)

**Created:** 2026-04-16
**Spec:** `docs/superpowers/specs/2026-04-15-kafka-time-series-producer-design.md` (merged in delta-v#167)
**Status:** Spec approved. Implementation plan not yet written. Executor not yet started.

This is a session-handoff prompt for the next conversation that picks up the Kafka Time Series producer work. It mirrors the auto-memory note at `~/.claude/projects/-Users-david-development-src-opennms-delta-v/memory/project_kafka_timeseries_producer_next_session.md` so the prompt is discoverable from the repo (and survives across machines/contributors).

## How to start the session

1. Invoke the `writing-plans` skill on the approved spec at `docs/superpowers/specs/2026-04-15-kafka-time-series-producer-design.md`.
2. The plan should cover exact file paths, exact code, exact test cases, exact commands, frequent commits — same standard as the flow-enricher events.api cleanup plan (see `docs/superpowers/plans/2026-04-14-flow-enricher-events-api-cleanup.md` for reference shape).
3. After the plan is written and approved, execute via `subagent-driven-development` (recommended for this kind of mechanical, well-specified work) or `executing-plans`.

## What to build (from the spec)

### 1. Two protobuf schemas

Committed to `core/daemon-boot-collectd/src/main/proto/`:

- `deltav-timeseries.proto` — `TimeseriesBatch` (one record per CollectionSet poll, keyed `{location}@{nodeId}`)
- `deltav-node-context.proto` — `NodeContext` (compacted context topic, produced by provisiond in a FUTURE PR — schema committed now for contract completeness)

### 2. Four Java files

In `core/daemon-boot-collectd/src/main/java/org/deltav/collectd/timeseries/`:

- **`CollectionSetToProtobufTranslator.java`** — pure function, walks horizon `CollectionSet` visitor tree → `TimeseriesBatch` protobuf.
  - **Critical detail from reviewer feedback:** explicitly map all horizon `AttributeType` variants (Counter 64-bit, Gauge 32/64-bit, String including structured/JSON data) to protobuf `AttributeType` enum with tests for each. Do not silently drop string attributes.
- **`TimeseriesKafkaPublisher.java`** — orchestration: translate + serialize + partition-key + `StreamBridge.send()`. Size safety check at 800 KB (R7). Error isolation: never throws to caller.
- **`TimeseriesKafkaPersister.java`** — thin adapter implementing horizon `Persister` interface, delegates to publisher.
- **`TimeseriesKafkaPublisherConfiguration.java`** — `@Configuration` + `@ConditionalOnProperty("deltav.timeseries.enabled")`. Declares all beans + two `NewTopic` beans:
  - `deltav-timeseries`: 16 partitions / 7-day retention / lz4 / delete policy
  - `deltav-node-context`: 8 partitions / compacted / infinite retention

### 3. Spring Cloud Stream binding

In `core/daemon-boot-collectd/src/main/resources/application.yml`:

- Output binding `publishTimeseries-out-0` → `deltav-timeseries`
- `use-native-encoding: true`, `compression.type: lz4`, `acks: all`, `enable.idempotence: true`
- Feature flag `deltav.timeseries.enabled` defaults to `false`

### 4. Micrometer metrics on `/actuator/prometheus`

- `deltav_timeseries_batches_published_total`
- `deltav_timeseries_batches_failed_total`
- `deltav_timeseries_batch_size_bytes`
- `deltav_timeseries_batch_size_warning_total`
- `deltav_timeseries_resources_per_batch`
- `deltav_timeseries_publish_duration_seconds`

### 5. Five-layer test suite

- **Layer 1:** `CollectionSetToProtobufTranslatorTest` (~20 unit tests, sub-ms)
- **Layer 2:** `TimeseriesKafkaPublisherTest` (~14 unit tests, mocked StreamBridge)
- **Layer 3:** `TimeseriesPublisherStreamBinderIT` (~8 tests, SCS test binder, feature-flag kill-switch verification)
- **Layer 4:** `TimeseriesKafkaBrokerIT` (~6 tests, Testcontainers KafkaContainer, real broker partition/compression/idempotence checks, `NewTopic` verification)
- **Layer 5:** `opennms-container/delta-v/test-timeseries-e2e.sh` (Docker Compose golden-path smoke test, ~90s)

### 6. Operator documentation

In README or env-file docs: `DELTAV_TIMESERIES_ENABLED`, new Prometheus metrics, expected Kafka disk footprint, known limitations (Collectd single-replica, schema v1 subject to refinement during Phase 1).

## Architecture context (do not re-litigate)

These decisions are settled by the approved spec. The implementation plan and execution should not revisit them.

- Identity/metadata lives on the `deltav-node-context` compacted topic, **NOT** on metric records. Consumers join via Kafka Streams `GlobalKTable`. This was an explicit pivot during brainstorming — see the spec's "Architecture" section for the full rationale.
- **One Kafka record per CollectionSet** (one-record-per-poll, not per-sample or per-resource). Atomicity is load-bearing for the future Thresholder's multi-attribute rule evaluation.
- Collectd is a singleton service today (no horizontal scale). The publisher runs as an **additional persister** alongside the existing `InMemoryStorage` TSS path, gated by the feature flag.
- The Collectd scheduler/publisher split is a tracked followup (a memory `project_collectd_scheduler_publisher_split.md` should be written during implementation), **NOT** in scope for this PR.
- The provisiond change feed that populates `deltav-node-context` is a separate PR. Schema is committed in this PR for contract completeness.

## Critical memories to check before starting

- `feedback_feature_branches` — never commit directly to develop
- `feedback_pull_before_branching` — always git pull before creating feature branches
- `feedback_delta_v_full_reactor_verify` — always run full delta-v reactor after changes, not just `-pl` on the target module
- `feedback_deltav_package_namespace` — new delta-v code uses `org.deltav` packages + own copyright
- `feedback_use_jackson_not_jaxb` — Jackson XmlMapper, never JAXB, in Spring Boot daemons (not directly relevant to protobuf but good to remember)
- `project_no_newts` — no Newts/Cassandra in delta-v; this producer is the replacement persistence path

## What NOT to do in the next session

- Do NOT re-brainstorm the architecture. The spec is approved.
- Do NOT build any consumer service. Producer only.
- Do NOT implement the provisiond change feed. Schema only.
- Do NOT touch Pollerd or PerspectivePollerd. Collectd only.
- Do NOT attempt the Collectd scheduler/publisher split. That's a separate design phase.

## Provenance

Brainstorming session that produced the spec went through several rounds of architectural pushback. Key pivots captured in the merged PR description (#167):

1. Scope reduction from 4 producers to 1 (Collectd first, then Pollerd/PerspectivePollerd in a follow-up).
2. Metadata + GlobalKTable pattern (the original proposal baked identity labels into every metric record; user's Q3/Q3a pushed toward broadcast state instead).
3. Minion Zero Trust boundary explicitly enforced (Minion never reads the DB; SnmpCollector-on-Minion stays stateless with server-owned scheduling when it eventually lands).
4. Collectd scheduler/publisher split deferred to followup memory rather than bundled into this spec.
