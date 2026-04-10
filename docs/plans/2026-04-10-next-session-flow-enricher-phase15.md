# Next Session: Flow Enricher Phase 1.5 — Horizon Adapter Integration

> Copy everything below the line into the next Claude Code conversation.

---

## Context

PR #139 merged on 2026-04-09 (branch `feature/flow-enricher-phase1` → `develop`).
That PR delivered the **skeleton** of the new `flow-enricher` Spring Cloud
Stream service:

- Consumes from all four `OpenNMS.Sink.Telemetry-*` Kafka topics via a
  comma-separated multi-topic input binding (`DELTAV_FLOWS_SINK_TOPICS`)
- Publishes to the new `deltav-flows` Kafka topic (auto-created)
- Spring Boot 4.0.3 + Spring Cloud 2025.1.1 + Kafka binder, all proven
  end-to-end against the live delta-v Compose stack
- 24 unit tests green (TDD): `FlowLocalityCalculator`, `InterfaceMarkingCache`,
  `SinkMessageDeserializer`, `JdbcNodeInfoLookup` (with Caffeine cache)
- Standalone Docker image (does NOT share `daemon-base`), built via
  `do_flow_enricher_image()` in `opennms-container/delta-v/build.sh`

**What the skeleton does NOT do:** parse the per-flow records inside each
`TelemetryMessage` payload. It currently emits a near-empty `FlowDocument`
populated only with `timestamp`, `host`, `location`, and `exporter_node`.
The output `deltav-flows` topic is reachable, but contains no useful
flow data yet.

**Phase 1.5 goal:** Wire the horizon protocol adapters
(Netflow5/Netflow9/IPFIX/sFlow) into `FlowEnrichmentFunction` so each
incoming `TelemetryMessage` is parsed into one or more `Flow` objects,
enriched (locality, src/dst/exporter NodeInfo, interface marking), and
mapped to a fully-populated `FlowDocumentProtos.FlowDocument` on the
`deltav-flows` topic.

After this session lands, the deployed `flow-enricher` service will be
fully functional — community consumers (Prometheus writers, ClickHouse
sinks, the future Phase 2 `flow-aggregator`) will see real enriched flow
data on the `deltav-flows` contract topic.

## Key Files

- **PR #139 scope (the skeleton):** `core/flow-enricher/`
- **Existing wired beans (already constructor-injected, just unused):**
  - `core/flow-enricher/src/main/java/org/deltav/flows/enricher/FlowEnrichmentFunction.java`
  - `core/flow-enricher/src/main/java/org/deltav/flows/enricher/enrichment/JdbcNodeInfoLookup.java`
  - `core/flow-enricher/src/main/java/org/deltav/flows/enricher/enrichment/FlowLocalityCalculator.java`
  - `core/flow-enricher/src/main/java/org/deltav/flows/enricher/enrichment/InterfaceMarkingCache.java`
- **Public protobuf contract (do not break):**
  - `core/flow-enricher/src/main/proto/deltav-flows.proto` — field tag
    numbers are intentionally aligned with horizon's `FlowDocument` so the
    bytes are wire-compatible
- **Spec / prior plan:**
  - `docs/superpowers/specs/2026-04-09-flow-processor-design.md`
  - `docs/superpowers/plans/2026-04-09-flow-enricher-phase1.md`

## Technical Findings (already validated, save you time)

**1. Adapter constructor signature is consistent across protocols.**

```java
// All four adapters extend org.opennms.netmgt.telemetry.protocols.flows.AbstractFlowAdapter<P>
// Constructor (verified via javap on horizon 1.0.5 JARs):
public NetflowXAdapter(
    org.opennms.netmgt.telemetry.config.api.AdapterDefinition definition,
    com.codahale.metrics.MetricRegistry metricRegistry,
    org.opennms.netmgt.flows.processing.Pipeline pipeline);
```

The four adapter classes:
- `org.opennms.netmgt.telemetry.protocols.netflow.adapter.netflow5.Netflow5Adapter`
- `org.opennms.netmgt.telemetry.protocols.netflow.adapter.netflow9.Netflow9Adapter`
- `org.opennms.netmgt.telemetry.protocols.netflow.adapter.ipfix.IpfixAdapter`
- `org.opennms.netmgt.telemetry.protocols.sflow.adapter.SFlowAdapter`

All four live in JARs published as horizon 1.0.7 on the
`pbrane/delta-v-horizon` GitHub Packages registry (verified via
`gh api /users/pbrane/packages/maven/.../versions`).

**2. The `Pipeline` interface is the integration seam.**

```java
// org.opennms.netmgt.flows.processing.Pipeline (from kafka-persistence JAR)
public interface Pipeline {
    void process(List<Flow> flows, FlowSource source, ProcessingOptions opts)
        throws FlowException;
}
```

`AbstractFlowAdapter.handleMessageLog(TelemetryMessageLog)` calls
`parse()` → `convert()` → `pipeline.process(...)`. This means we can:

1. Implement a **`CapturingPipeline`** whose `process()` method just
   stashes the `List<Flow>` and `FlowSource` for later use
2. Pass that to each adapter's constructor
3. Call `adapter.handleMessageLog(messageLog)` to drive parsing
4. Pull the captured flows out and run them through our enrichment
   (`JdbcNodeInfoLookup`, `FlowLocalityCalculator`, `InterfaceMarkingCache`)
5. Map each enriched `Flow` to our `FlowDocumentProtos.FlowDocument`
6. Emit one Kafka record per flow (or batched, TBD)

This avoids needing horizon's `PipelineImpl`, `DocumentEnricherImpl`,
`InterfaceMarkerImpl`, or `FlowThresholdingImpl` — all of which carry
heavy Spring/Karaf wiring and DB-backed dependencies we don't want.

**3. The `Flow` interface from `org.opennms.features.flows.api` exposes
every field on `FlowDocument`** (verified via `javap`). The mapper from
`Flow` to `FlowDocumentProtos.FlowDocument` is straightforward — no
horizon helper needed. Field-by-field mapping with the same tag numbers
keeps the wire format compatible with any existing horizon flow tooling
operators may already have.

**4. `org.opennms.features.flows.kafka-persistence` ships a
`FlowDocumentBuilder.buildFlowDocument(integration.api.v1.flows.Flow)`**
that already does the conversion, **but** it produces horizon's
`org.opennms.netmgt.flows.persistence.model.FlowDocument` class — not
our `org.deltav.flows.proto.FlowDocumentProtos.FlowDocument`. Two
options:
- **Option A:** Pull in `kafka-persistence` JAR, call `buildFlowDocument`,
  then re-parse the bytes as our deltav class (wire-compatible)
- **Option B:** Write our own mapper in `org.deltav.flows.enricher`
  (~50 lines, fully unit-testable, no extra horizon dep)

Brainstorm during the next session — I lean toward **B** for the same
reason we picked the standalone Docker pattern: keep flow-enricher's
horizon JAR footprint as small as possible.

**5. Horizon adapter JARs will need the standard exclusion list.**

Same nightmare we hit with `daemon-boot-telemetryd`: ServiceMix bundles,
spring-dependencies, hibernate-core, pax-logging, opennms-model,
jackson-module-scala, etc. The pom.xml in PR #139 already establishes
this exclusion pattern for `org.opennms.core.ipc.sink.common` and
`org.opennms.features.telemetry.common` — copy that block for each new
adapter dep.

**6. The `OpenNMS.Sink.* → DeltaV.Sink.*` rename is still pending.**

Phase 1.5 does NOT need it. The `DELTAV_FLOWS_SINK_TOPICS` env var
already lets the rename PR land separately without touching
flow-enricher code. Don't bundle the rename into Phase 1.5.

## Suggested Approach

Brainstorm at the start of the session, but the obvious shape is:

1. **One protocol end-to-end first** (Netflow-9 is the highest-volume
   real-world protocol — start there)
2. **`CapturingPipeline`** TDD: implement + test that it captures
   `List<Flow>` from a fake adapter call
3. **`FlowToDocumentMapper`** TDD: trivial field-by-field mapper from
   `Flow` interface to `FlowDocumentProtos.FlowDocument.Builder`
4. **`Netflow9MessageProcessor`** (or similar): owns a `Netflow9Adapter`
   instance, drives `handleMessageLog`, pulls flows out of the
   `CapturingPipeline`, runs each through enrichers, returns
   `List<FlowDocumentProtos.FlowDocument>`
5. **Update `FlowEnrichmentFunction`** to dispatch to the right
   processor based on the source Kafka topic — then iterate flows and
   emit one Kafka record per FlowDocument (or batched — research what
   the Spring Cloud Stream `Function<byte[], byte[]>` signature lets you
   do; you may need `Function<byte[], List<byte[]>>` or
   `Function<Message<byte[]>, Flux<byte[]>>`)
6. **Repeat for Netflow-5, IPFIX, sFlow** — same pattern, ~30 LOC each
7. **Live integration test** against the running delta-v stack: send a
   real netflow packet from labbox or trigger Minion's
   netflow-listener-9 against the `snmp-agent` mock, then read from
   `deltav-flows` and confirm enriched protobuf messages arrive

**Critical brainstorm question:** how does Spring Cloud Stream
`Function<byte[], byte[]>` produce **multiple** output records from a
single input? The current skeleton emits one-out-per-one-in. A single
Sink message can contain many flow records — we need to emit N output
records per input. Likely answers: switch to
`Function<byte[], List<byte[]>>`, or to a reactive
`Function<Flux<byte[]>, Flux<byte[]>>`, or use the `StreamBridge` API.
This may need a small Spring Cloud Stream spike before the rest of the
plan is firm.

## Review Feedback to Incorporate

1. **Fan-out semantics** — verify whichever Spring Cloud Stream output
   pattern you pick (List, Flux, StreamBridge) honors backpressure
   against Kafka producer batching. Don't blindly emit 10K flows per
   single Sink message into a synchronous channel.

2. **Per-flow enrichment cost** — each flow runs three lookups
   (`lookupByIpAddress` for src, dst, exporter). With Caffeine caching
   that's mostly hot, but flows-per-second can be very high. Consider
   batching the per-message DB calls or using `lookupByIpAddresses(Set)`
   if it materially speeds up cold starts.

3. **`InterfaceMarkingCache`** is currently wired but unused. Phase 1.5
   should call `markIfNeeded(nodeId, ifIndex)` for both `inputSnmp` and
   `outputSnmp` of each flow whose exporter is a known node.

4. **`FlowSource`** from horizon is the (location, sourceAddress,
   contextKey) record the adapter passes to `Pipeline.process`.
   `JdbcNodeInfoLookup` should use `FlowSource.getSourceAddress()` for
   the exporter lookup — already what the skeleton does, but make
   sure the per-flow path keeps using it.

5. **Don't try to wire horizon's classification engine in this PR.**
   Classification depends on PostgreSQL `classification_rule` tables
   that may not be populated in delta-v. Phase 1.6 (or a separate PR)
   can add that. Phase 1.5 leaves `application` field unset.

## Prerequisites (Not Done Yet)

- **Sink topic prefix rename** (`OpenNMS.Sink.*` → `DeltaV.Sink.*`) —
  separate PR, not yet started. Phase 1.5 keeps using `OpenNMS.Sink.*`
  via `DELTAV_FLOWS_SINK_TOPICS`.
- **PR #139 merged** ✓ (2026-04-10) — `develop` is current

## Important Reminders

- **Never create PRs against OpenNMS/opennms** — always use
  `--repo pbrane/delta-v --base develop`
- **Always `git pull develop`** before creating the feature branch
- **Package policy:** `org.deltav.flows.enricher.*` for new code
- **Copyright:** BeaconStrategists for `org.deltav` packages
- **Boot 4.0 modules need explicit `junit-platform-launcher` test dep** —
  see `core/flow-enricher/pom.xml` for the pattern (memory:
  `feedback_boot4_junit_platform_launcher.md`)
- **The protobuf `.proto` file is the public community contract** —
  Phase 1.5 should NOT rename or renumber any existing field. New fields
  may be added at new tag numbers. Reserved tags (25, 44) must stay
  reserved.
- **Standalone Docker image** — do NOT add flow-enricher to the
  daemon-base shared-libs intersection. The architecture decision is
  documented in PR #139's commit log and `do_flow_enricher_image()`.
- **The full delta-v stack is already running** with `flow-enricher`
  in a healthy state — you can iterate on the running container with
  `docker compose --profile full up -d --build flow-enricher` after
  rebuilding the image. The four input partitions are already assigned
  to the consumer group.
