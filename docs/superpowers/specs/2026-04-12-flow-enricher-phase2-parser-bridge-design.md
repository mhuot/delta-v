# Flow Enricher Phase 2 — Server-Side UDP Parser Bridge

**Status:** Design approved, ready for implementation plan
**Date:** 2026-04-12
**Branch:** `feature/flow-enricher-phase2-parser-bridge`
**Related:** `docs/superpowers/specs/2026-04-12-minion-telemetry-receiver-design.md` (Phase 1, already shipped)

## Goals

1. The flow-enricher must consume raw UDP wire bytes emitted by the Phase 1 Minion listener and parse them server-side using horizon's `UdpParser` classes.
2. Once complete, the Minion stays a thin UDP-to-Kafka relay and can be redeployed without carrying any flow-parsing responsibility, consistent with the "Minion is sole network ingress, but never a processor" commandment.
3. Phase 1's dormant code (shipped in PR #148) becomes hot: UDP port 4729 moves from the `telemetryd` container to the `minion` container in `docker-compose.yml`, and `test-flows-e2e.sh` verifies that softflowd-generated Netflow v9 packets reach ClickHouse's `deltav.flows_raw` table via the Minion.

## Non-Goals

- Reimplementing horizon's `FlowMessage → Flow` mapping (we keep `AbstractFlowAdapter` as Stage 2).
- Migrating to Flink/Beam (already decided against in `project_no_newts.md`).
- Forwarding parser events to a real Kafka events topic (deferred; see Followups).
- Computing real exporter clock skew (Phase 1.5 hardcoded `0L`; Phase 2 keeps that hardcode).
- Removing the entirety of telemetryd's flow path (Phase 2 removes only the `Netflow-9-UDP-4729` listener block; full telemetryd deletion is tracked separately).
- sFlow end-to-end verification — sFlow is "best-effort" per the clarifying-question answer (no E2E test bed and no live exporter).

## Context

Phase 1 shipped a Minion-side UDP listener (`FlowUdpListener` + `FlowSinkModule`) that accepts Netflow v5/v9, IPFIX, and sFlow UDP datagrams on port 4729 and wraps the raw bytes into a `TelemetryMessageLog` protobuf, which is then marshalled into a Kafka Sink envelope and shipped on `OpenNMS.Sink.Telemetry-{protocol}`. That code is currently dormant: `docker-compose.yml` still routes port 4729 to telemetryd, so no traffic exercises the Minion listener in live runs.

The flow-enricher (Phase 1.5) already consumes those Kafka topics, but its per-protocol processors assume that each `TelemetryMessage.bytes` entry is a *pre-parsed* `FlowMessage` protobuf, not raw Netflow wire bytes. This mismatch was discovered during live Phase 1 validation: the adapter throws `InvalidProtocolBufferException: Protocol message contained an invalid tag (zero)` on every packet. Phase 2 closes that gap by moving Netflow template parsing into the flow-enricher itself.

## Architecture

### Two-stage bridge

Phase 2 inserts a new Stage 1 (raw UDP → `FlowMessage` protobuf) before Phase 1.5's existing Stage 2 (`FlowMessage` protobuf → `Flow` POJO). The data flow inside `AbstractProtocolMessageProcessor.process()` becomes:

```
TelemetryMessageLog (raw UDP bytes in entry.bytes)
       │
       ▼
STAGE 1: UdpParser + CapturingDispatcher      ← NEW
  for each raw entry:
    parser.parse(byteBuf, remoteAddr, localAddr).join()
    (parser internally: templates → FlowMessage → dispatcher)
  captured: List<TelemetryMessage POJO>, each .buffer == FlowMessage protobuf
       │
       ▼
synthesize TelemetryMessageLog where each entry.bytes is FlowMessage protobuf
       │
       ▼
STAGE 2: AbstractFlowAdapter + CapturingPipeline    ← UNCHANGED from Phase 1.5
  adapter.handleMessageLog(syntheticLog)
  captures flows via CapturingPipeline
       │
       ▼
return List<Flow> to FlowEnrichmentFunction
```

The enrichment pipeline downstream of `process()` — node lookup, locality, interface marking, application classification, `Flow → FlowDocument` mapping — is completely unchanged.

### Why two-stage instead of direct bypass?

We considered bypassing the `AbstractFlowAdapter` entirely (Option B) and writing our own `FlowMessage → Flow` conversion. Rejected because:

- **Same per-flow cost.** The parser already serializes `FlowMessage` to bytes before dispatching (the POJO `TelemetryMessage.buffer` is a `java.nio.ByteBuffer` of serialized bytes, not an in-memory `FlowMessage` object). Both options call `FlowMessage.parseFrom()` exactly once per flow — there is no performance difference.
- **Code ownership and drift risk.** Option B would reimplement ~600 LOC of field-by-field `FlowMessage → Flow` mapping across three protocols, permanently divergent from horizon's battle-tested adapter code.
- **The synthetic-log wrap is cheap.** Wrapping the captured POJOs back into a `TelemetryMessageLog` builder is ~10 lines, runs once per batch, and decouples Stage 1 from Stage 2 cleanly.

### Parser state: singleton with ThreadLocal capture

Horizon's `UdpParser` subclasses hold per-exporter template state in an internal `UdpSessionManager`. This state **must persist across function invocations** — without the template cache, Netflow v9 and IPFIX data records cannot be decoded. Two approaches were considered:

- **α: fresh parser per call** — simple, but destroys the template cache on every call. Rejected (unusable for Netflow v9/IPFIX).
- **β: singleton parser with a ThreadLocal dispatcher** — the parser is a long-lived bean; a `ThreadLocalDispatcher` bean owns a `ThreadLocal<CapturingDispatcher>`; each `process()` call installs a fresh `CapturingDispatcher` into the ThreadLocal before `parse()` and clears it after (in `finally`). **This is the chosen approach.**

Option β works because Spring Cloud Stream's consumer thread pool is stable: each thread is serial for the duration of a function call, and SCS does not multiplex calls onto one thread. The ThreadLocal pattern is a mirror of the `CapturingPipeline` pattern Phase 1.5 already established — same idea, different layer.

**Critical invariant:** every `.parse()` call must be wrapped in try/finally that guarantees the ThreadLocal is cleared even on exception. A leak would silently poison the next call on the same thread, attributing flows from one exporter's packet to another exporter's result — a correctness bug that would not surface until production.

### Partition stickiness

Phase 1 already ships `FlowSinkModule.getRoutingKey()` returning `location@sourceAddress:sourcePort`, which pins each exporter to a single Kafka partition. Phase 2 adds the complementary consumer-side config:

```yaml
spring.cloud.stream.kafka.bindings.enrichFlows-in-0.consumer.configuration:
  partition.assignment.strategy: org.apache.kafka.clients.consumer.CooperativeStickyAssignor
```

This keeps a given exporter's traffic on the same flow-enricher replica across rebalances, preserving the template cache warmth.

## Component Inventory

### New files

| Path | Purpose |
|---|---|
| `core/flow-enricher/src/main/java/org/deltav/flows/enricher/parser/CapturingDispatcher.java` | `AsyncDispatcher<TelemetryMessage>` that collects dispatched POJOs into a per-call list. Not thread-safe — fresh instance per `process()` call. |
| `core/flow-enricher/src/main/java/org/deltav/flows/enricher/parser/ThreadLocalDispatcher.java` | Singleton `AsyncDispatcher<TelemetryMessage>` that delegates to a `ThreadLocal<CapturingDispatcher>`. Installed into parsers at construction time; processors `install()` / `clear()` the per-call capturing dispatcher around each `parse()`. |
| `core/flow-enricher/src/main/java/org/deltav/flows/enricher/parser/NoOpDnsResolver.java` | `DnsResolver` that returns `CompletableFuture.completedFuture(Optional.empty())` for every lookup. Flow enrichment already does its own node lookup downstream. |
| `core/flow-enricher/src/main/java/org/deltav/flows/enricher/parser/LoggingEventForwarder.java` | `EventForwarder` that logs sent events at WARN and drops them. Kafka-backed forwarder deferred to followup. |
| `core/flow-enricher/src/main/java/org/deltav/flows/enricher/parser/StaticIdentity.java` | `Identity` bean backed by `@Value`-injected `deltav.flows.identity.location` and `deltav.flows.identity.system-id` properties. |
| `core/flow-enricher/src/main/java/org/deltav/flows/enricher/parser/FlowEnricherMicrometerBridge.java` | `@Bean`-style adapter registering the shared Dropwizard `MetricRegistry` as a Micrometer `MeterRegistry`. Spring Boot Actuator then exposes parser + adapter metrics via `/actuator/prometheus`. |
| `core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/Netflow5ParserBridgeIT.java` | Fixture-based integration test. |
| `core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/Netflow9ParserBridgeIT.java` | Fixture-based integration test. Critical path — verifies template+data packet pairing against real `UdpSessionManager`. |
| `core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/IpfixParserBridgeIT.java` | Fixture-based integration test. |
| `core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/SFlowParserBridgeIT.java` | Fixture-based integration test (best-effort per design decision). |
| `core/flow-enricher/src/test/resources/fixtures/netflow5_*.dat` | Captured wire bytes copied from horizon's test resources. |
| `core/flow-enricher/src/test/resources/fixtures/netflow9_*.dat` | Captured wire bytes copied from horizon's test resources. |
| `core/flow-enricher/src/test/resources/fixtures/ipfix_*.dat` | Captured wire bytes copied from horizon's test resources. |
| `core/flow-enricher/src/test/resources/fixtures/sflow_*.dat` | Captured wire bytes copied from horizon's test resources. |

### Modified files

| Path | Change |
|---|---|
| `core/flow-enricher/pom.xml` | Add `org.opennms.features.telemetry.protocols.netflow:*.parser`, `org.opennms.features.telemetry.protocols.sflow:*.parser`, and `org.opennms.features.telemetry:listeners` as compile deps. Mirror the exclusion list from existing `*.adapter.*` deps (`opennms-config`, `atomikos-*`, `eclipselink`, `jaxb-xjc`, `features.config.*`, `opennms-util`). Add `events-api` with `commons-logging` excluded. Add `io.micrometer:micrometer-registry-prometheus`. |
| `core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/AbstractProtocolMessageProcessor.java` | Rework `process()` to two-stage bridge. Adds `runParser()` helper with try/finally ThreadLocal hygiene. Subclasses inject a pre-built `UdpParser` via constructor (Shape 2); the base class uses it directly, keeping `createAdapter()` as the only abstract method for Stage 2. |
| `core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/Netflow5MessageProcessor.java` | Gains a `Netflow5UdpParser` constructor parameter **in addition to** the existing `AdapterDefinition` and `MetricRegistry` parameters (which remain, and still feed Stage 2 adapter construction). Stores the parser and exposes it to the base class via a getter the base class calls to obtain the Stage 1 parser. |
| `core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/Netflow9MessageProcessor.java` | Same pattern — gains `Netflow9UdpParser`, existing parameters unchanged. |
| `core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/IpfixMessageProcessor.java` | Same pattern — gains `IpfixUdpParser`. |
| `core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/SFlowMessageProcessor.java` | Same pattern — gains `SFlowUdpParser`. Best-effort — unit tests must pass; live E2E not guaranteed. |
| `core/flow-enricher/src/main/java/org/deltav/flows/enricher/FlowEnricherConfiguration.java` | Beans for `InformationElementDatabase`, `NoOpDnsResolver`, `LoggingEventForwarder`, `StaticIdentity`, `ScheduledExecutorService` (parser session cleanup), the four `*UdpParser` instances, `ThreadLocalDispatcher`, and `FlowEnricherMicrometerBridge`. Parser beans call `.start(scheduler)` in `@PostConstruct`, `.stop()` in `@PreDestroy`. |
| `core/flow-enricher/src/main/resources/application.yml` | Add `CooperativeStickyAssignor` partition strategy. Add `deltav.flows.identity.*` properties. Expose `prometheus` in `management.endpoints.web.exposure.include`. |
| `core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/Netflow9MessageProcessorTest.java` | Update for Shape 2 constructor. Add two ThreadLocal stress tests (exception-during-parse cleanup; sequential-calls non-contamination). |
| `core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/Netflow5MessageProcessorTest.java` | Update for Shape 2. |
| `core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/IpfixMessageProcessorTest.java` | Update for Shape 2. |
| `core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/SFlowMessageProcessorTest.java` | Update for Shape 2. |
| `opennms-container/delta-v/docker-compose.yml` | Move `4729:4729/udp` from `telemetryd` service to `minion` service. Change `flow-default-testnode-1.environment.NETFLOW_COLLECTOR` from `telemetryd:4729` to `minion:4729`. Change `flow-default-testnode-1.depends_on` from `telemetryd` to `minion`. |
| `opennms-container/delta-v/telemetryd-overlay/etc/telemetryd-configuration.xml` | Remove the `Netflow-9-UDP-4729` listener block. |
| `opennms-container/delta-v/test-flows-e2e.sh` | Add `minion` to `REQUIRED_SERVICES`. |
| `docs/superpowers/specs/2026-04-12-minion-telemetry-receiver-design.md` | Replace the "wire format identical" paragraph (now false) with the actual Phase 1/Phase 2 split. |

## Data Flow (Per-Datagram)

```
1. Exporter (softflowd) → UDP datagram → minion:4729
2. Minion FlowUdpListener accepts datagram
3. Minion wraps raw bytes in TelemetryMessageLog:
     location, systemId, sourceAddress, sourcePort, message[0].bytes=rawUDP
4. Minion FlowSinkModule marshals → Kafka Sink envelope → producer
     topic = OpenNMS.Sink.Telemetry-Netflow-9
     routing key = location@sourceAddress:sourcePort (exporter stickiness)
──────────────────────────────  cluster hop  ──────────────────────────────
5. flow-enricher Spring Cloud Stream consumer (CooperativeStickyAssignor)
     receives Message<byte[]> on enrichFlows-in-0
6. FlowEnrichmentFunction.processMessage:
     extracts moduleId from kafka_receivedTopic header
     dispatches to Netflow9MessageProcessor
7. AbstractProtocolMessageProcessor.process(TelemetryMessageLog):
     a. fresh CapturingDispatcher for this call
     b. threadLocalDispatcher.install(captureForThisCall)
     c. for each entry, wrapped in per-entry try/catch (DEBUG log on failure):
          ByteBuf buf = Unpooled.wrappedBuffer(entry.bytes)
          parser.parse(buf, exporterAddr, localAddr).join()
     d. threadLocalDispatcher.clear() in outer finally
     e. synthesize TelemetryMessageLog from captured POJOs
     f. Stage 2: new CapturingPipeline + new Netflow9Adapter
     g. adapter.handleMessageLog(syntheticLog)
     h. return pipeline.getCapturedFlows()
8. FlowEnrichmentFunction enriches each flow (node lookup, locality, marking)
9. FlowToDocumentMapper → serialized FlowDocument bytes
10. Spring Cloud Stream splitter emits List<byte[]> to deltav-flows topic
```

## Parser Lifecycle

- **Startup.** `FlowEnricherConfiguration` creates one shared `ScheduledExecutorService` (single-thread, named `flow-parser-session-cleanup`). Each of the four parser beans calls `parser.start(scheduler)` in `@PostConstruct`. Horizon's `start()` registers a 60-second housekeeping task that evicts stale templates.
- **Shutdown.** `@PreDestroy` calls `parser.stop()` on each parser, then the scheduler shuts down via bean dependency ordering (parsers depend on the scheduler, so they stop first).
- **`InformationElementDatabase`.** Constructed once at startup. Reads embedded CSV resources (IPFIX IANA registry + Netflow9 vendor fields). Approximately 150ms at container start; no lazy init.

## Threading Model

Spring Cloud Stream `enrichFlows-in-0` binding stays at `concurrency=1` for Phase 2. This simplifies the ThreadLocal invariant (only one thread ever installs/clears the capturing dispatcher) and eliminates a whole class of debugging risk if something goes wrong. Raising concurrency later is a one-line config change — the parsers themselves are internally thread-safe (ConcurrentHashMaps in `UdpSessionManager`).

## Error Handling

### Error boundaries

Two independent failure sources, each with its own try/catch:

```java
// Stage 1: parser errors
try {
    parsedLog = runParser(rawLog);
} catch (RuntimeException e) {
    LOG.warn("Parser {} failed on message log ({} entries, sourceAddr={}): {}",
            parser.getClass().getSimpleName(), rawLog.getMessageCount(),
            rawLog.getSourceAddress(), e.getMessage(), e);
    return List.of();
}

// Stage 2: adapter errors (unchanged from Phase 1.5)
try {
    adapter.handleMessageLog(parsedLog);
} catch (RuntimeException e) {
    LOG.warn("Horizon adapter {} failed on parsed log: {}",
            adapter.getClass().getSimpleName(), e.getMessage(), e);
    return List.of();
}
```

### Per-entry error scope

Inside `runParser()`, per-entry failures are logged at DEBUG (expected under load — unknown template IDs) and do not fail the whole batch. This matters when Minion aggregation is eventually turned on (currently `AggregationPolicy` is `null`, so one Kafka message == one datagram == one entry, making per-entry and per-batch equivalent).

### ThreadLocal hygiene (critical)

Every call to `parser.parse()` must be wrapped in try/finally that guarantees `threadLocalDispatcher.clear()` runs. A stale ThreadLocal silently attributes flows from one exporter to another exporter's result on the next call on the same thread — a correctness bug that would not surface in test or staging without explicit coverage. This is exactly what the two targeted ThreadLocal stress tests (see Testing Strategy) exist to verify.

### Parser completion model (assumption to verify)

Horizon parsers are expected to complete their `CompletableFuture` **synchronously** within the calling thread, because the only async behavior is the dispatcher, and our capturing dispatcher is synchronous. If this assumption breaks at runtime, `.join()` becomes a latency hazard. The unit tests must include an assertion that the future returned by `.parse()` is already complete before `.join()` is called.

### Failure modes explicitly NOT handled

- Parser OOM from huge template expansion (out of scope; would need bounded session manager)
- Clock-skew alerting (logged as WARN; no real alert path)
- Per-exporter rate limiting (delegated to Kafka partition balance)
- Chunked Sink messages (Phase 1.5 `SinkMessageDeserializer` already drops them with WARN)

## Observability

### Exposed metrics

Spring Boot Actuator's `/actuator/prometheus` endpoint exposes:

- **Parser metrics** (new): `flow_parser_packets_total`, `flow_parser_parse_errors_total`, `flow_parser_template_cache_size`, `flow_parser_flows_emitted_total` — tagged by protocol and exporter.
- **Adapter metrics** (previously created but unpublished): whatever horizon's `AbstractFlowAdapter` constructor registers on the shared `MetricRegistry`.
- **JVM / GC / thread pool**: Spring Boot default Micrometer binders (verify during E2E).

### Micrometer bridge

One bean in `FlowEnricherConfiguration`:

```java
@Bean
DropwizardMeterRegistry flowEnricherDropwizardBridge(
        MetricRegistry dropwizardRegistry, Clock clock) {
    DropwizardConfig config = new DropwizardConfig() {
        @Override public String prefix() { return "flow_enricher"; }
        @Override public String get(String key) { return null; }
    };
    return new DropwizardMeterRegistry(config, dropwizardRegistry,
            HierarchicalNameMapper.DEFAULT, clock) {
        @Override protected Double nullGaugeValue() { return Double.NaN; }
    };
}
```

Plus `io.micrometer:micrometer-registry-prometheus` in the POM and `prometheus` in the Actuator exposure list.

### Log levels

| Event | Level |
|---|---|
| Parser emits event via `LoggingEventForwarder` | WARN |
| Per-entry parser failure inside `runParser` loop | DEBUG |
| Per-batch parser failure (Stage 1 aborts) | WARN |
| Stage 2 adapter failure | WARN (unchanged) |
| `SinkMessageDeserializer` drop | WARN (unchanged) |
| Normal flow-to-document success | no log |

## Testing Strategy

### Unit tests (4 classes updated)

Update existing `Netflow5/9/Ipfix/SFlowMessageProcessorTest` for Shape 2 constructor. Each uses a `FakeUdpParser` stub that emits canned `FlowMessage` protobuf bytes, asserting the synthetic-log wrap and Stage 2 adapter call produce the expected `Flow` list.

### Targeted ThreadLocal stress tests (in `Netflow9MessageProcessorTest`)

Two targeted tests directly stress the ThreadLocal hygiene risk:

1. **`parserExceptionDoesNotLeakThreadLocal`** — parser throws during `parse()`, test asserts the exception is caught, result is empty, and `ThreadLocalDispatcher.current()` is `null` afterward. Directly verifies the try/finally invariant.
2. **`sequentialCallsDoNotCrossContaminate`** — two sequential `process()` calls on the same thread with different exporters and different fake parser outputs. Asserts flows from call A do not appear in call B's result. Catches the "stale ThreadLocal leaked to next call" bug class.

These live in the Netflow9 test class because the pattern is parser-agnostic; replicating across all four protocols is unnecessary.

### Fixture-based integration tests (4 new classes)

Real `Netflow5/9/Ipfix/SFlowUdpParser` instances, real `InformationElementDatabase`, real `CapturingDispatcher`. Feed captured wire-bytes fixtures sourced from horizon's test resources:

| Protocol | Horizon source path |
|---|---|
| Netflow v5 | `features/telemetry/protocols/netflow/parser/src/test/resources/flows/netflow5_*.dat` |
| Netflow v9 | `features/telemetry/protocols/netflow/parser/src/test/resources/flows/netflow9_*.dat` |
| IPFIX | `features/telemetry/protocols/netflow/parser/src/test/resources/flows/ipfix_*.dat` |
| sFlow | `features/telemetry/protocols/sflow/parser/src/test/resources/flows/sflow_*.dat` |

Fixture files are copied into `core/flow-enricher/src/test/resources/fixtures/` (not pulled at test time — horizon test jars are not on our classpath, and in-repo fixtures are debuggable). License is AGPL-3.0 on both sides; no attribution concerns.

The Netflow v9 IT is the **critical path** — it must include a template packet followed by a data packet from the same simulated exporter to exercise the per-exporter template cache and the `UdpSessionManager` state management.

### E2E verification (manual, not CI)

After PR is on the branch and Docker Compose is flipped:

```bash
./build.sh deltav
cd opennms-container/delta-v
docker compose up -d
./test-flows-e2e.sh
```

Success criteria:

1. `deltav.flows_raw` ClickHouse table grows from 0 to >0 rows within 60 seconds of softflowd starting.
2. All 4 dimension materialized views (`flows_by_*`) populate.
3. `deltav.flows_raw` rows have non-null `exporter_ip`, `src_ip`, `dst_ip`, `protocol` — confirms enrichment survived the rework.
4. Log grep on flow-enricher for parser WARN is minimal (< 1% of packets).
5. `/actuator/prometheus` on flow-enricher returns non-zero `flow_parser_packets_total`.

Successful E2E output is captured as a `test: E2E verification — N flows in Ms` commit in the PR, matching the Phase 1 pattern.

## PR Plan

**Single PR**, branch `feature/flow-enricher-phase2-parser-bridge`, targeting `develop`. Originally considered splitting into `phase2a` / `phase2b` but reversed that decision during brainstorming: PR-2a cannot be truly dormant because it changes the semantic of the existing `TelemetryMessageLog` contract. Either we accept broken develop between merges or add a temporary feature flag, and neither is better than landing atomically.

Commit sequence (target ~10 commits):

1. `feat(flow-enricher): add POM deps for horizon UDP parser modules`
2. `feat(flow-enricher): add parser collaborators (DnsResolver, EventForwarder, Identity)`
3. `feat(flow-enricher): add ThreadLocalDispatcher and CapturingDispatcher`
4. `feat(flow-enricher): rework AbstractProtocolMessageProcessor for two-stage parser bridge`
5. `feat(flow-enricher): Spring wiring for parser beans and Micrometer bridge`
6. `test(flow-enricher): fixture integration tests for all four parser bridges`
7. `refactor(deltav): flip docker-compose port 4729 from telemetryd to minion`
8. `refactor(telemetryd): remove Netflow-9-UDP-4729 listener block`
9. `test(flows): add minion to REQUIRED_SERVICES in test-flows-e2e.sh`
10. `docs(flow-enricher): update minion-telemetry-receiver spec with Phase 2 wire format`
11. (optional) `test: E2E verification — N flows in Ms`

Each commit should be independently reviewable. POM changes and collaborators land before the processor rework, so reviewers can approve dependency hygiene without understanding the full bridge pattern.

## Rollback Plan

If PR lands and E2E fails in a way that can't be quickly fixed:

1. **Revert PR on develop** — straightforward `git revert` of the merge commit. Phase 1.5 telemetryd path comes back in one step.
2. **Rebuild and redeploy** — standard `./build.sh deltav` + `docker compose up -d`.

Rollback risk is low because (a) single-PR merge means one revert, not a chain, (b) the telemetryd-based path is known-working and its configuration is preserved in git history, (c) no database schema changes are involved — only code and container configuration.

## Followups (Not in This PR)

These are tracked as separate memory entries, not worked on in Phase 2:

1. **Kafka-backed `EventForwarder`** — replace `LoggingEventForwarder` with a real Kafka producer that writes parser events (clock skew, unknown templates) to a delta-v events topic for alerting. Triggered when the first real production exporter surfaces an operational incident we want visibility on.
2. **Raise Spring Cloud Stream consumer concurrency** — currently pinned at `concurrency=1` for ThreadLocal simplicity. Parser internals are thread-safe, so raising this is a one-line config change — do it once the Phase 2 path has ~1 week of live validation behind it.
3. **Real clock-skew computation** — Phase 1.5 and Phase 2 both hardcode `0L` as the clock-correction argument to `FlowToDocumentMapper`. Extract per-exporter skew from parser output when accuracy of `deltaSwitched` / `firstSwitched` timestamps matters for a downstream consumer.
4. **Minion aggregation** — `FlowSinkModule.getAggregationPolicy()` returns `null` today, so each datagram is one Kafka message. Horizon batches up to 1000 messages per 500ms keyed by exporter. Consider enabling this if Kafka producer load becomes a concern.
5. **sFlow live verification** — Phase 2 ships sFlow as best-effort (unit tests only, no E2E). If sFlow exporters actually enter the stack, stand up an sFlow-generating test container analogous to softflowd and update `test-flows-e2e.sh`.
6. **Telemetryd decommissioning** — Phase 2 removes only the Netflow-9-UDP-4729 listener block. Full telemetryd deletion is a separate followup (memory: `project_telemetryd_pure_bridge.md`).

## Open Questions

None at design time. All clarifying questions were resolved during brainstorming:
- Architecture fork → two-stage bridge (Option A)
- sFlow scope → best-effort parity
- Test strategy → unit + fixture integration + manual E2E verification
- Observability → Micrometer bridge published via Actuator
- PR staging → single PR
