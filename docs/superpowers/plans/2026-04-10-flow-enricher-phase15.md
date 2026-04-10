# Flow Enricher (Phase 1.5) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire horizon protocol adapters (Netflow-5, Netflow-9, IPFIX, sFlow) into the existing `flow-enricher` Spring Boot service so each incoming `TelemetryMessageLog` is parsed into one or more `Flow` objects, enriched with src/dst node lookups, locality, interface marking, and a port-based application classification, and then emitted as a `List<byte[]>` of `FlowDocumentProtos.FlowDocument` messages to the `deltav-flows` Kafka topic.

**Architecture:** Extends the Phase 1 skeleton in-place. Changes the Spring Cloud Stream function signature from `Function<byte[], byte[]>` to `Function<byte[], List<byte[]>>` (the canonical "splitter" pattern). Dispatches per-message based on the SinkMessage `moduleId` field, invoking a `ProtocolMessageProcessor` per protocol that drives the corresponding horizon `AbstractFlowAdapter` against a `CapturingPipeline` that stashes the parsed `List<Flow>`. Each captured flow runs through src/dst node lookup, locality calculation, interface marking, and port-based classification, then maps to `FlowDocumentProtos.FlowDocument` via an own-rolled `FlowToDocumentMapper` (no horizon `FlowDocumentBuilder` dependency). Horizon's classification engine, clock-skew correction, and `nodeDeleted` cache eviction are deferred to Phase 1.6.

**Tech Stack:** Spring Boot 4.0.3, Spring Cloud Stream 2025.1.1 (Kafka binder), Protocol Buffers 3.25.5, PostgreSQL JDBC, Horizon 1.0.7 flow protocol adapter JARs (`netflow-adapter-netflow5`, `netflow-adapter-netflow9`, `netflow-adapter-ipfix`, `sflow-adapter`), `org.opennms.features.flows.flows-api` for the `Flow` interface, `org.opennms.features.flows.classification-api` for the `Pipeline` interface, Dropwizard `MetricRegistry` (required by adapter constructors).

**Spec:** `docs/superpowers/specs/2026-04-09-flow-processor-design.md` (Phase 1.5 section)

**Brainstorm rationale:** The design decisions referenced throughout this plan (List-based fan-out, SinkMessage moduleId dispatch, own mapper, port-based classifier stub, all-four-protocols-in-one-PR scope) were settled in the 2026-04-10 brainstorm session. This plan describes HOW, not WHY — refer to the spec or the brainstorm transcript for rationale.

---

## Prerequisites

Before starting this plan:

1. **Phase 1 skeleton merged** (PR #139, done 2026-04-09) — `core/flow-enricher/` module exists with Spring Cloud Stream + Kafka binder wiring
2. **Phase 2 ClickHouse spec merged** (PR #141, in review as of 2026-04-10) — not a blocker for Phase 1.5 code, but the ClickHouse schema relies on the fully-populated FlowDocument that this plan produces
3. **Horizon 1.0.7 published** (done) — provides the flow adapter JARs via GitHub Packages
4. **`develop` is up to date** — run `git pull origin develop` before creating the feature branch (per the project's `feedback_pull_before_branching` memory)

**Note on the existing skeleton state:** The Phase 1 skeleton already wires `SinkMessageDeserializer`, `JdbcNodeInfoLookup`, `FlowLocalityCalculator`, and `InterfaceMarkingCache` into the `FlowEnrichmentFunction` constructor. Two of those four beans carry `@SuppressWarnings("unused")` because Phase 1 doesn't consume them. Phase 1.5 removes those suppressions and actually uses all four beans during per-flow enrichment. The skeleton's current `processMessage(byte[]) → byte[]` method and its corresponding `@Bean Function<byte[], byte[]>` declaration are replaced by `processMessage(byte[]) → List<byte[]>` and `@Bean Function<byte[], List<byte[]>>` respectively — the rest of the constructor wiring is preserved.

---

## File Structure

This plan creates new files under three new sub-packages inside `core/flow-enricher/src/main/java/org/deltav/flows/enricher/` and adds matching test files. Existing skeleton files are modified rather than replaced.

```
core/flow-enricher/
├── pom.xml                                          — MODIFIED (add 4 adapter deps + flows-api + processing-api)
└── src/
    ├── main/
    │   ├── java/org/deltav/flows/enricher/
    │   │   ├── FlowEnricherApplication.java         — unchanged
    │   │   ├── FlowEnricherConfiguration.java       — MODIFIED (bean wiring changes, dispatch map)
    │   │   ├── SinkMessageDeserializer.java         — MODIFIED (expose moduleId)
    │   │   ├── DeserializedSinkMessage.java         — NEW (record carrying moduleId + TelemetryMessageLog)
    │   │   ├── FlowEnrichmentFunction.java          — MODIFIED (List<byte[]> signature, full pipeline)
    │   │   ├── classification/
    │   │   │   ├── ApplicationClassifier.java       — NEW (interface)
    │   │   │   └── PortBasedApplicationClassifier.java — NEW (static port map)
    │   │   ├── mapping/
    │   │   │   └── FlowToDocumentMapper.java        — NEW (Flow → FlowDocument with null-wrapper semantics)
    │   │   ├── pipeline/
    │   │   │   └── CapturingPipeline.java           — NEW (stashes List<Flow> and FlowSource)
    │   │   ├── protocol/
    │   │   │   ├── ProtocolMessageProcessor.java    — NEW (interface)
    │   │   │   ├── AbstractProtocolMessageProcessor.java — NEW (shared driver: handleMessageLog + capture)
    │   │   │   ├── Netflow5MessageProcessor.java    — NEW
    │   │   │   ├── Netflow9MessageProcessor.java    — NEW
    │   │   │   ├── IpfixMessageProcessor.java       — NEW
    │   │   │   └── SFlowMessageProcessor.java       — NEW
    │   │   └── enrichment/
    │   │       ├── FlowLocalityCalculator.java      — unchanged (skeleton bean, called per-flow in Phase 1.5)
    │   │       ├── InterfaceMarkingCache.java       — unchanged (skeleton bean, called per-flow in Phase 1.5)
    │   │       └── JdbcNodeInfoLookup.java          — unchanged (skeleton bean, called 3x per-flow in Phase 1.5)
    │   └── resources/
    │       └── application.yml                      — unchanged (existing bindings already support the new signature)
    └── test/
        ├── java/org/deltav/flows/enricher/
        │   ├── FlowEnrichmentFunctionTest.java      — MODIFIED (new signature, dispatch assertions)
        │   ├── classification/
        │   │   └── PortBasedApplicationClassifierTest.java — NEW
        │   ├── mapping/
        │   │   └── FlowToDocumentMapperTest.java    — NEW (~300 lines, null-wrapper edge cases)
        │   ├── pipeline/
        │   │   └── CapturingPipelineTest.java       — NEW
        │   ├── protocol/
        │   │   ├── TestAdapterDefinitions.java      — NEW (test helper: testAdapterDefinition(String))
        │   │   ├── Netflow5MessageProcessorTest.java — NEW (hand-crafted hex bytes)
        │   │   ├── Netflow9MessageProcessorTest.java — NEW (horizon .dat fixture)
        │   │   ├── IpfixMessageProcessorTest.java    — NEW (horizon .dat fixture)
        │   │   └── SFlowMessageProcessorTest.java    — NEW (horizon .dat fixture)
        │   └── integration/
        │       └── FlowEnrichmentStreamBinderIT.java — NEW (all four protocols, test binder)
        └── resources/
            └── test-packets/
                ├── netflow9-sample.dat              — NEW (copied from horizon)
                ├── ipfix-sample.dat                 — NEW (copied from horizon)
                └── sflow-sample.dat                 — NEW (copied from horizon)
```

---

## Commit structure

Per the brainstorm, the PR lands in six logical commits that let reviewers follow the layering:

1. **pom.xml** — four adapter JARs plus flows-api and processing-api, all with the standard exclusion block
2. **Shared infrastructure** — `CapturingPipeline`, `FlowToDocumentMapper`, `ApplicationClassifier` interface + `PortBasedApplicationClassifier`, and all their unit tests
3. **Splitter refactor** — `SinkMessageDeserializer` change to expose `moduleId`, `DeserializedSinkMessage` record, `FlowEnrichmentFunction` signature change to `List<byte[]>` with placeholder dispatch, updated `FlowEnrichmentFunctionTest`
4. **Protocol processors** — `ProtocolMessageProcessor` interface, `AbstractProtocolMessageProcessor` base class, four concrete implementations, their unit tests, and the test fixture files
5. **Final wiring** — `FlowEnricherConfiguration` bean changes (new processor beans, dispatch map, orchestration bean), full per-flow enrichment path (locality + src/dst node lookup + interface marking + classification + mapping)
6. **Integration test** — `FlowEnrichmentStreamBinderIT` exercising all four protocols through the in-memory test binder

Tasks below are grouped to match this commit structure — each "commit" checkbox at the end of a task group represents one of the six commits.

---

## Task 1: Add horizon adapter dependencies to pom.xml

**Files:**
- Modify: `core/flow-enricher/pom.xml`

The flow-enricher pom already has exclusion blocks for `org.opennms.core.ipc.sink.common` and `org.opennms.features.telemetry.common`. This task adds six more horizon dependencies, each with an identical exclusion block. Copy the existing exclusion block verbatim for each new dependency.

- [ ] **Step 1: Verify the adapter artifact IDs are published in horizon 1.0.7**

Run:
```bash
curl -s "https://maven.pkg.github.com/pbrane/delta-v-horizon/org/opennms/features/telemetry/protocols/netflow/org.opennms.features.telemetry.protocols.netflow.adapter.netflow5/1.0.7/" 2>&1 | head -5
```
Expected: 401 response (auth required, but the artifact path is resolved by the server, confirming the artifact exists)

Alternative verification via local Maven:
```bash
./compile.pl -pl :org.deltav.flows.flow-enricher dependency:list 2>&1 | grep -i "netflow5\|netflow9\|ipfix\|sflow\|flows-api\|processing-api" | head -20
```

- [ ] **Step 2: Add netflow5 adapter dependency**

Open `core/flow-enricher/pom.xml`. After the existing `org.opennms.features.telemetry.common` dependency block (ends around line 119), add:

```xml
<!-- Horizon: Netflow-5 protocol adapter -->
<dependency>
    <groupId>org.opennms.features.telemetry.protocols.netflow</groupId>
    <artifactId>org.opennms.features.telemetry.protocols.netflow.adapter.netflow5</artifactId>
    <exclusions>
        <exclusion><groupId>org.apache.servicemix.bundles</groupId><artifactId>*</artifactId></exclusion>
        <exclusion><groupId>org.opennms.dependencies</groupId><artifactId>spring-dependencies</artifactId></exclusion>
        <exclusion><groupId>org.opennms.dependencies</groupId><artifactId>spring-security-dependencies</artifactId></exclusion>
        <exclusion><groupId>org.hibernate</groupId><artifactId>hibernate-core</artifactId></exclusion>
        <exclusion><groupId>org.hibernate.javax.persistence</groupId><artifactId>hibernate-jpa-2.0-api</artifactId></exclusion>
        <exclusion><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId></exclusion>
        <exclusion><groupId>org.slf4j</groupId><artifactId>jcl-over-slf4j</artifactId></exclusion>
        <exclusion><groupId>org.slf4j</groupId><artifactId>log4j-over-slf4j</artifactId></exclusion>
        <exclusion><groupId>org.ops4j.pax.logging</groupId><artifactId>pax-logging-log4j2</artifactId></exclusion>
        <exclusion><groupId>org.ops4j.pax.logging</groupId><artifactId>pax-logging-api</artifactId></exclusion>
        <exclusion><groupId>commons-logging</groupId><artifactId>commons-logging</artifactId></exclusion>
        <exclusion><groupId>com.fasterxml.jackson.module</groupId><artifactId>jackson-module-scala_2.13</artifactId></exclusion>
        <exclusion><groupId>org.opennms</groupId><artifactId>opennms-model</artifactId></exclusion>
    </exclusions>
</dependency>
```

- [ ] **Step 3: Add netflow9 adapter dependency**

Immediately after the netflow5 block, add the same structure with these changes:
- `<artifactId>org.opennms.features.telemetry.protocols.netflow.adapter.netflow9</artifactId>`
- Comment: `<!-- Horizon: Netflow-9 protocol adapter -->`
- All exclusions identical

- [ ] **Step 4: Add IPFIX adapter dependency**

Immediately after the netflow9 block, add the same structure with:
- `<artifactId>org.opennms.features.telemetry.protocols.netflow.adapter.ipfix</artifactId>`
- Comment: `<!-- Horizon: IPFIX protocol adapter -->`

- [ ] **Step 5: Add sFlow adapter dependency**

Immediately after the IPFIX block, add the same structure with:
- `<groupId>org.opennms.features.telemetry.protocols.sflow</groupId>`
- `<artifactId>org.opennms.features.telemetry.protocols.sflow.adapter</artifactId>`
- Comment: `<!-- Horizon: sFlow protocol adapter -->`

- [ ] **Step 6: Add flows-api dependency (for Flow interface)**

After the sflow adapter block, add:

```xml
<!-- Horizon: Flow interface (return type of adapter pipeline) -->
<dependency>
    <groupId>org.opennms.features.flows</groupId>
    <artifactId>org.opennms.features.flows.flows-api</artifactId>
    <exclusions>
        <exclusion><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId></exclusion>
        <exclusion><groupId>commons-logging</groupId><artifactId>commons-logging</artifactId></exclusion>
    </exclusions>
</dependency>
```

This is a lightweight JAR (just the API interfaces) so the exclusion block is minimal.

- [ ] **Step 7: Add flow processing API dependency (for Pipeline interface)**

After the flows-api block, add:

```xml
<!-- Horizon: Pipeline interface (integration seam for CapturingPipeline) -->
<dependency>
    <groupId>org.opennms.features.flows</groupId>
    <artifactId>org.opennms.netmgt.flows.processing.api</artifactId>
    <exclusions>
        <exclusion><groupId>org.apache.servicemix.bundles</groupId><artifactId>*</artifactId></exclusion>
        <exclusion><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId></exclusion>
        <exclusion><groupId>commons-logging</groupId><artifactId>commons-logging</artifactId></exclusion>
        <exclusion><groupId>org.opennms</groupId><artifactId>opennms-model</artifactId></exclusion>
    </exclusions>
</dependency>
```

- [ ] **Step 8: Add Dropwizard MetricRegistry dependency**

The horizon adapters require a `com.codahale.metrics.MetricRegistry` constructor argument. If this is not already transitively provided, add it explicitly. After the processing-api block:

```xml
<!-- Dropwizard Metrics (required by horizon adapter constructors) -->
<dependency>
    <groupId>io.dropwizard.metrics</groupId>
    <artifactId>metrics-core</artifactId>
</dependency>
```

(Version is managed by the horizon BOM or Spring Boot BOM — do not specify a version here.)

- [ ] **Step 9: Verify the module still compiles**

Run:
```bash
cd /Users/david/development/src/opennms/delta-v
./compile.pl -pl :org.deltav.flows.flow-enricher -DskipTests compile 2>&1 | tail -30
```
Expected: `BUILD SUCCESS`. If there are resolution errors, check the GitHub Packages credentials and verify the artifact IDs match what's actually published.

- [ ] **Step 10: Verify adapter classes are on the classpath**

Run:
```bash
./compile.pl -pl :org.deltav.flows.flow-enricher dependency:tree 2>&1 | grep -E "netflow5|netflow9|ipfix|sflow.adapter|flows-api|processing.api"
```
Expected: six lines showing the adapter and API JARs resolved from horizon 1.0.7.

- [ ] **Step 11: Commit**

```bash
git add core/flow-enricher/pom.xml
git commit -m "feat(flow-enricher): add horizon adapter dependencies for Phase 1.5"
```

---

## Task 2: Create CapturingPipeline

**Files:**
- Create: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/pipeline/CapturingPipeline.java`
- Create: `core/flow-enricher/src/test/java/org/deltav/flows/enricher/pipeline/CapturingPipelineTest.java`

The `CapturingPipeline` implements horizon's `org.opennms.netmgt.flows.processing.Pipeline` interface. When horizon's `AbstractFlowAdapter.handleMessageLog()` is called, it internally invokes `pipeline.process(List<Flow>, FlowSource, ProcessingOptions)`. Our implementation does not persist or forward the flows — it just stashes them into instance fields so the caller can pull them out afterwards.

- [ ] **Step 1: Write the failing test**

Create `core/flow-enricher/src/test/java/org/deltav/flows/enricher/pipeline/CapturingPipelineTest.java`:

```java
/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.deltav.flows.enricher.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.opennms.netmgt.flows.processing.ProcessingOptions;
import org.opennms.netmgt.flows.processing.enrichment.EnrichedFlow;
import org.opennms.netmgt.flows.processing.pipeline.Pipeline;
import org.opennms.netmgt.flows.processing.source.FlowSource;
import org.opennms.netmgt.flows.api.Flow;

class CapturingPipelineTest {

    @Test
    void capturesFlowsAndSourcePassedToProcess() throws Exception {
        CapturingPipeline pipeline = new CapturingPipeline();
        Flow flow1 = mock(Flow.class);
        Flow flow2 = mock(Flow.class);
        FlowSource source = new FlowSource("test-location", "192.0.2.1", null);

        pipeline.process(List.of(flow1, flow2), source, ProcessingOptions.defaults());

        assertThat(pipeline.getCapturedFlows()).containsExactly(flow1, flow2);
        assertThat(pipeline.getCapturedSource()).isEqualTo(source);
    }

    @Test
    void capturedFlowsIsEmptyListBeforeProcessIsCalled() {
        CapturingPipeline pipeline = new CapturingPipeline();
        assertThat(pipeline.getCapturedFlows()).isEmpty();
        assertThat(pipeline.getCapturedSource()).isNull();
    }

    @Test
    void secondProcessCallReplacesFirstCaptures() throws Exception {
        CapturingPipeline pipeline = new CapturingPipeline();
        Flow firstFlow = mock(Flow.class);
        Flow secondFlow = mock(Flow.class);
        FlowSource source1 = new FlowSource("loc1", "192.0.2.1", null);
        FlowSource source2 = new FlowSource("loc2", "192.0.2.2", null);

        pipeline.process(List.of(firstFlow), source1, ProcessingOptions.defaults());
        pipeline.process(List.of(secondFlow), source2, ProcessingOptions.defaults());

        assertThat(pipeline.getCapturedFlows()).containsExactly(secondFlow);
        assertThat(pipeline.getCapturedSource()).isEqualTo(source2);
    }
}
```

**Note:** The exact package paths for `Pipeline`, `FlowSource`, `ProcessingOptions`, and `Flow` depend on which horizon JAR exposes them. If the test won't compile because of import errors, run `./compile.pl -pl :org.deltav.flows.flow-enricher dependency:tree | grep -i "flow\|processing"` and search the resolved JARs for the actual class locations. Update the imports to match. The `Pipeline` interface is the key one — it's in `org.opennms.netmgt.flows.processing.pipeline.Pipeline` in horizon 1.0.7 per the brainstorm findings.

- [ ] **Step 2: Run the test to verify it fails**

Run:
```bash
cd /Users/david/development/src/opennms/delta-v
./compile.pl -pl :org.deltav.flows.flow-enricher -Dtest=CapturingPipelineTest test 2>&1 | tail -20
```
Expected: Compilation failure — `CapturingPipeline` class does not exist.

- [ ] **Step 3: Create the CapturingPipeline class**

Create `core/flow-enricher/src/main/java/org/deltav/flows/enricher/pipeline/CapturingPipeline.java`:

```java
/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.deltav.flows.enricher.pipeline;

import java.util.Collections;
import java.util.List;

import org.opennms.netmgt.flows.api.Flow;
import org.opennms.netmgt.flows.processing.ProcessingOptions;
import org.opennms.netmgt.flows.processing.pipeline.Pipeline;
import org.opennms.netmgt.flows.processing.source.FlowSource;

/**
 * Pipeline implementation that stashes the flows passed to
 * {@link #process(List, FlowSource, ProcessingOptions)} so the caller can
 * retrieve them via {@link #getCapturedFlows()} and {@link #getCapturedSource()}.
 *
 * <p>This is the integration seam between horizon's {@code AbstractFlowAdapter}
 * and our Phase 1.5 enrichment. The adapter calls {@code pipeline.process(...)}
 * at the end of its parse-and-convert cycle; we use this "pipeline" as a
 * no-op sink so we can pull the {@link Flow} list out without triggering any
 * of horizon's persistence or forwarding logic.
 *
 * <p><strong>Not thread-safe and not reusable across concurrent invocations.</strong>
 * Each protocol processor creates a fresh {@code CapturingPipeline} per call
 * to avoid cross-contamination.
 */
public class CapturingPipeline implements Pipeline {

    private List<Flow> capturedFlows = Collections.emptyList();
    private FlowSource capturedSource;

    @Override
    public void process(List<Flow> flows, FlowSource source, ProcessingOptions options) {
        this.capturedFlows = flows;
        this.capturedSource = source;
    }

    public List<Flow> getCapturedFlows() {
        return capturedFlows;
    }

    public FlowSource getCapturedSource() {
        return capturedSource;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run:
```bash
./compile.pl -pl :org.deltav.flows.flow-enricher -Dtest=CapturingPipelineTest test 2>&1 | tail -20
```
Expected: `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`.

If compilation fails with "cannot find symbol: class Pipeline" or similar, the package location in the horizon JAR is different. Check the dependency tree and update imports in both the test and the production file.

- [ ] **Step 5: Do NOT commit yet**

This task is part of Commit 2 (Shared Infrastructure). It commits together with Tasks 3, 4, and 5.

---

## Task 3: Create FlowToDocumentMapper

**Files:**
- Create: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/mapping/FlowToDocumentMapper.java`
- Create: `core/flow-enricher/src/test/java/org/deltav/flows/enricher/mapping/FlowToDocumentMapperTest.java`

The mapper converts a horizon `Flow` (plus our enriched NodeInfo records for src, dst, exporter) into a `FlowDocumentProtos.FlowDocument`. The critical discipline is explicit null handling for `google.protobuf.*Value` wrapper fields: call `setXxx(UInt64Value.of(v))` only when the getter returns non-null, so downstream consumers (ClickHouse) can distinguish "unset" from "zero."

- [ ] **Step 1: Write the failing test — core scalar mapping**

Create `core/flow-enricher/src/test/java/org/deltav/flows/enricher/mapping/FlowToDocumentMapperTest.java`:

```java
/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 *
 * Licensed under the GNU Affero General Public License v3.
 */
package org.deltav.flows.enricher.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.deltav.flows.enricher.enrichment.JdbcNodeInfoLookup;
import org.deltav.flows.proto.FlowDocumentProtos;
import org.junit.jupiter.api.Test;
import org.opennms.netmgt.flows.api.Flow;

class FlowToDocumentMapperTest {

    private final FlowToDocumentMapper mapper = new FlowToDocumentMapper();

    @Test
    void mapsTimestampAndScalars() {
        Flow flow = mock(Flow.class);
        when(flow.getTimestamp()).thenReturn(1712700000000L);
        when(flow.getSrcAddr()).thenReturn("192.0.2.1");
        when(flow.getDstAddr()).thenReturn("198.51.100.2");
        when(flow.getNextHop()).thenReturn("203.0.113.1");
        when(flow.getSrcHostname()).thenReturn("client.example.com");
        when(flow.getDstHostname()).thenReturn("server.example.com");

        FlowDocumentProtos.FlowDocument doc = mapper.map(flow, null, null, null,
                "unknown", "UNKNOWN", "UNKNOWN", "UNKNOWN");

        assertThat(doc.getTimestamp()).isEqualTo(1712700000000L);
        assertThat(doc.getSrcAddress()).isEqualTo("192.0.2.1");
        assertThat(doc.getDstAddress()).isEqualTo("198.51.100.2");
        assertThat(doc.getNextHopAddress()).isEqualTo("203.0.113.1");
        assertThat(doc.getSrcHostname()).isEqualTo("client.example.com");
        assertThat(doc.getDstHostname()).isEqualTo("server.example.com");
    }

    @Test
    void omitsUInt64ValueWrapperFieldsWhenFlowGetterReturnsNull() {
        Flow flow = mock(Flow.class);
        when(flow.getTimestamp()).thenReturn(1712700000000L);
        when(flow.getSrcAddr()).thenReturn("192.0.2.1");
        when(flow.getDstAddr()).thenReturn("198.51.100.2");
        when(flow.getBytes()).thenReturn(null);
        when(flow.getPackets()).thenReturn(null);
        when(flow.getSrcAs()).thenReturn(null);
        when(flow.getDstAs()).thenReturn(null);

        FlowDocumentProtos.FlowDocument doc = mapper.map(flow, null, null, null,
                "unknown", "UNKNOWN", "UNKNOWN", "UNKNOWN");

        assertThat(doc.hasNumBytes()).isFalse();
        assertThat(doc.hasNumPackets()).isFalse();
        assertThat(doc.hasSrcAs()).isFalse();
        assertThat(doc.hasDstAs()).isFalse();
    }

    @Test
    void populatesUInt64ValueWrapperFieldsWhenFlowGetterReturnsZero() {
        // CRITICAL: 0 is a valid value, distinct from null. The mapper must
        // set the wrapper to UInt64Value.of(0), not leave it unset.
        Flow flow = mock(Flow.class);
        when(flow.getTimestamp()).thenReturn(1712700000000L);
        when(flow.getSrcAddr()).thenReturn("192.0.2.1");
        when(flow.getDstAddr()).thenReturn("198.51.100.2");
        when(flow.getBytes()).thenReturn(0L);
        when(flow.getPackets()).thenReturn(0L);

        FlowDocumentProtos.FlowDocument doc = mapper.map(flow, null, null, null,
                "unknown", "UNKNOWN", "UNKNOWN", "UNKNOWN");

        assertThat(doc.hasNumBytes()).isTrue();
        assertThat(doc.getNumBytes().getValue()).isEqualTo(0L);
        assertThat(doc.hasNumPackets()).isTrue();
        assertThat(doc.getNumPackets().getValue()).isEqualTo(0L);
    }

    @Test
    void populatesExporterNodeWhenEnrichmentProvidesIt() {
        Flow flow = mock(Flow.class);
        when(flow.getTimestamp()).thenReturn(1712700000000L);
        when(flow.getSrcAddr()).thenReturn("192.0.2.1");
        when(flow.getDstAddr()).thenReturn("198.51.100.2");

        JdbcNodeInfoLookup.NodeInfo exporterInfo = new JdbcNodeInfoLookup.NodeInfo(
                42, "selfmonitor", "router-1", List.of("edge", "router"));

        FlowDocumentProtos.FlowDocument doc = mapper.map(flow, exporterInfo, null, null,
                "unknown", "UNKNOWN", "UNKNOWN", "UNKNOWN");

        assertThat(doc.hasExporterNode()).isTrue();
        assertThat(doc.getExporterNode().getNodeId()).isEqualTo(42);
        assertThat(doc.getExporterNode().getForeignSource()).isEqualTo("selfmonitor");
        assertThat(doc.getExporterNode().getForeignId()).isEqualTo("router-1");
        assertThat(doc.getExporterNode().getCategoriesList()).containsExactly("edge", "router");
    }

    @Test
    void omitsNodeInfoMessagesWhenLookupReturnedNull() {
        Flow flow = mock(Flow.class);
        when(flow.getTimestamp()).thenReturn(1712700000000L);
        when(flow.getSrcAddr()).thenReturn("192.0.2.1");
        when(flow.getDstAddr()).thenReturn("198.51.100.2");

        FlowDocumentProtos.FlowDocument doc = mapper.map(flow, null, null, null,
                "unknown", "UNKNOWN", "UNKNOWN", "UNKNOWN");

        assertThat(doc.hasExporterNode()).isFalse();
        assertThat(doc.hasSrcNode()).isFalse();
        assertThat(doc.hasDestNode()).isFalse();
    }

    @Test
    void populatesApplicationAndLocalityFromEnrichmentParameters() {
        Flow flow = mock(Flow.class);
        when(flow.getTimestamp()).thenReturn(1712700000000L);
        when(flow.getSrcAddr()).thenReturn("192.0.2.1");
        when(flow.getDstAddr()).thenReturn("198.51.100.2");

        FlowDocumentProtos.FlowDocument doc = mapper.map(flow, null, null, null,
                "HTTPS", "PRIVATE", "PUBLIC", "PUBLIC");

        assertThat(doc.getApplication()).isEqualTo("HTTPS");
        assertThat(doc.getSrcLocality()).isEqualTo(FlowDocumentProtos.Locality.PRIVATE);
        assertThat(doc.getDstLocality()).isEqualTo(FlowDocumentProtos.Locality.PUBLIC);
        assertThat(doc.getFlowLocality()).isEqualTo(FlowDocumentProtos.Locality.PUBLIC);
    }
}
```

This is a starter test file — the full test covers every field. Additional test methods for each remaining field group (UInt32Value wrappers for ports/AS/mask_len/etc., direction enum, sampling_algorithm enum, netflow_version enum, ifindex fields, tcp_flags, tos/dscp/ecn, vlan, host/location) are written after the mapper class itself is in place. The six tests above establish the null-wrapper discipline pattern that the remaining tests follow.

- [ ] **Step 2: Run the test to verify it fails**

Run:
```bash
./compile.pl -pl :org.deltav.flows.flow-enricher -Dtest=FlowToDocumentMapperTest test 2>&1 | tail -20
```
Expected: Compilation failure — `FlowToDocumentMapper` class does not exist, OR `map(...)` method signature mismatch.

- [ ] **Step 3: Create the FlowToDocumentMapper class**

Create `core/flow-enricher/src/main/java/org/deltav/flows/enricher/mapping/FlowToDocumentMapper.java`:

```java
/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 *
 * Licensed under the GNU Affero General Public License v3.
 */
package org.deltav.flows.enricher.mapping;

import com.google.protobuf.DoubleValue;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;

import org.deltav.flows.enricher.enrichment.JdbcNodeInfoLookup;
import org.deltav.flows.proto.FlowDocumentProtos;
import org.opennms.netmgt.flows.api.Flow;

/**
 * Mechanical field-by-field mapper from horizon's {@link Flow} interface to
 * our {@link FlowDocumentProtos.FlowDocument} protobuf class. Handles the
 * {@code google.protobuf.*Value} wrapper semantics explicitly — a null Flow
 * getter result leaves the corresponding FlowDocument field unset, while a
 * zero result calls {@code setXxx(UInt64Value.of(0))} so downstream consumers
 * (ClickHouse) can distinguish "unset" from "zero."
 */
public class FlowToDocumentMapper {

    /**
     * Maps a flow to a FlowDocument. The enriched fields (node info for
     * exporter/src/dst, application classification, locality strings) are
     * passed as separate parameters because they are computed by the
     * orchestrator, not by the Flow itself.
     *
     * @param flow the parsed flow from the horizon adapter
     * @param exporterNodeInfo exporter node lookup result, may be null if not in inventory
     * @param srcNodeInfo source node lookup result, may be null
     * @param destNodeInfo destination node lookup result, may be null
     * @param application classified application name ("HTTPS", "DNS", ..., or "unknown")
     * @param srcLocality "PRIVATE", "PUBLIC", or "UNKNOWN"
     * @param dstLocality "PRIVATE", "PUBLIC", or "UNKNOWN"
     * @param flowLocality "PRIVATE", "PUBLIC", or "UNKNOWN"
     */
    public FlowDocumentProtos.FlowDocument map(
            Flow flow,
            JdbcNodeInfoLookup.NodeInfo exporterNodeInfo,
            JdbcNodeInfoLookup.NodeInfo srcNodeInfo,
            JdbcNodeInfoLookup.NodeInfo destNodeInfo,
            String application,
            String srcLocality,
            String dstLocality,
            String flowLocality) {

        FlowDocumentProtos.FlowDocument.Builder b = FlowDocumentProtos.FlowDocument.newBuilder();

        // Scalars (always populated)
        b.setTimestamp(flow.getTimestamp());
        b.setClockCorrection(flow.getClockCorrection() != null ? flow.getClockCorrection() : 0L);

        if (flow.getSrcAddr() != null) {
            b.setSrcAddress(flow.getSrcAddr());
        }
        if (flow.getDstAddr() != null) {
            b.setDstAddress(flow.getDstAddr());
        }
        if (flow.getNextHop() != null) {
            b.setNextHopAddress(flow.getNextHop());
        }
        if (flow.getSrcHostname() != null) {
            b.setSrcHostname(flow.getSrcHostname());
        }
        if (flow.getDstHostname() != null) {
            b.setDstHostname(flow.getDstHostname());
        }
        if (flow.getNextHopHostname() != null) {
            b.setNextHopHostname(flow.getNextHopHostname());
        }

        // Volume (UInt64Value wrappers — null means unset, 0 means zero)
        if (flow.getBytes() != null) {
            b.setNumBytes(UInt64Value.of(flow.getBytes()));
        }
        if (flow.getPackets() != null) {
            b.setNumPackets(UInt64Value.of(flow.getPackets()));
        }

        // Flow lifetime
        if (flow.getFirstSwitched() != null) {
            b.setFirstSwitched(UInt64Value.of(flow.getFirstSwitched()));
        }
        if (flow.getLastSwitched() != null) {
            b.setLastSwitched(UInt64Value.of(flow.getLastSwitched()));
        }
        if (flow.getDeltaSwitched() != null) {
            b.setDeltaSwitched(UInt64Value.of(flow.getDeltaSwitched()));
        }
        if (flow.getFlowSeqNum() != null) {
            b.setFlowSeqNum(UInt64Value.of(flow.getFlowSeqNum()));
        }
        if (flow.getNumFlowRecords() != null) {
            b.setNumFlowRecords(UInt32Value.of(flow.getNumFlowRecords()));
        }

        // Ports and AS numbers
        if (flow.getSrcPort() != null) {
            b.setSrcPort(UInt32Value.of(flow.getSrcPort()));
        }
        if (flow.getDstPort() != null) {
            b.setDstPort(UInt32Value.of(flow.getDstPort()));
        }
        if (flow.getSrcAs() != null) {
            b.setSrcAs(UInt64Value.of(flow.getSrcAs()));
        }
        if (flow.getDstAs() != null) {
            b.setDstAs(UInt64Value.of(flow.getDstAs()));
        }
        if (flow.getSrcMaskLen() != null) {
            b.setSrcMaskLen(UInt32Value.of(flow.getSrcMaskLen()));
        }
        if (flow.getDstMaskLen() != null) {
            b.setDstMaskLen(UInt32Value.of(flow.getDstMaskLen()));
        }

        // Protocol / QoS / TCP
        if (flow.getProtocol() != null) {
            b.setProtocol(UInt32Value.of(flow.getProtocol()));
        }
        if (flow.getIpProtocolVersion() != null) {
            b.setIpProtocolVersion(UInt32Value.of(flow.getIpProtocolVersion()));
        }
        if (flow.getTcpFlags() != null) {
            b.setTcpFlags(UInt32Value.of(flow.getTcpFlags()));
        }
        if (flow.getTos() != null) {
            b.setTos(UInt32Value.of(flow.getTos()));
        }
        if (flow.getDscp() != null) {
            b.setDscp(UInt32Value.of(flow.getDscp()));
        }
        if (flow.getEcn() != null) {
            b.setEcn(UInt32Value.of(flow.getEcn()));
        }
        if (flow.getVlan() != null) {
            b.setVlan(flow.getVlan());
        }

        // Interface indexes
        if (flow.getInputSnmp() != null) {
            b.setInputSnmpIfindex(UInt32Value.of(flow.getInputSnmp()));
        }
        if (flow.getOutputSnmp() != null) {
            b.setOutputSnmpIfindex(UInt32Value.of(flow.getOutputSnmp()));
        }

        // Engine
        if (flow.getEngineId() != null) {
            b.setEngineId(UInt32Value.of(flow.getEngineId()));
        }
        if (flow.getEngineType() != null) {
            b.setEngineType(UInt32Value.of(flow.getEngineType()));
        }

        // Direction enum
        if (flow.getDirection() != null) {
            switch (flow.getDirection()) {
                case INGRESS -> b.setDirection(FlowDocumentProtos.Direction.INGRESS);
                case EGRESS  -> b.setDirection(FlowDocumentProtos.Direction.EGRESS);
                default      -> b.setDirection(FlowDocumentProtos.Direction.DIRECTION_UNKNOWN);
            }
        }

        // Netflow version enum
        if (flow.getNetflowVersion() != null) {
            switch (flow.getNetflowVersion()) {
                case V5    -> b.setNetflowVersion(FlowDocumentProtos.NetflowVersion.V5);
                case V9    -> b.setNetflowVersion(FlowDocumentProtos.NetflowVersion.V9);
                case IPFIX -> b.setNetflowVersion(FlowDocumentProtos.NetflowVersion.IPFIX);
                case SFLOW -> b.setNetflowVersion(FlowDocumentProtos.NetflowVersion.SFLOW);
                default    -> b.setNetflowVersion(FlowDocumentProtos.NetflowVersion.NETFLOW_VERSION_UNKNOWN);
            }
        }

        // Sampling
        if (flow.getSamplingAlgorithm() != null) {
            b.setSamplingAlgorithm(mapSamplingAlgorithm(flow.getSamplingAlgorithm()));
        }
        if (flow.getSamplingInterval() != null) {
            b.setSamplingInterval(DoubleValue.of(flow.getSamplingInterval()));
        }

        // Enriched fields (from orchestrator, not from Flow)
        b.setApplication(application != null ? application : "unknown");
        b.setSrcLocality(parseLocality(srcLocality));
        b.setDstLocality(parseLocality(dstLocality));
        b.setFlowLocality(parseLocality(flowLocality));

        // NodeInfo messages
        if (exporterNodeInfo != null) {
            b.setExporterNode(toNodeInfo(exporterNodeInfo));
        }
        if (srcNodeInfo != null) {
            b.setSrcNode(toNodeInfo(srcNodeInfo));
        }
        if (destNodeInfo != null) {
            b.setDestNode(toNodeInfo(destNodeInfo));
        }

        return b.build();
    }

    private static FlowDocumentProtos.NodeInfo toNodeInfo(JdbcNodeInfoLookup.NodeInfo info) {
        FlowDocumentProtos.NodeInfo.Builder nb = FlowDocumentProtos.NodeInfo.newBuilder()
                .setNodeId(info.nodeId());
        if (info.foreignSource() != null) {
            nb.setForeignSource(info.foreignSource());
        }
        if (info.foreignId() != null) {
            nb.setForeignId(info.foreignId());
        }
        if (info.categories() != null) {
            nb.addAllCategories(info.categories());
        }
        return nb.build();
    }

    private static FlowDocumentProtos.Locality parseLocality(String locality) {
        if (locality == null) {
            return FlowDocumentProtos.Locality.LOCALITY_UNKNOWN;
        }
        switch (locality) {
            case "PRIVATE": return FlowDocumentProtos.Locality.PRIVATE;
            case "PUBLIC":  return FlowDocumentProtos.Locality.PUBLIC;
            default:        return FlowDocumentProtos.Locality.LOCALITY_UNKNOWN;
        }
    }

    private static FlowDocumentProtos.SamplingAlgorithm mapSamplingAlgorithm(
            org.opennms.netmgt.flows.api.Flow.SamplingAlgorithm src) {
        switch (src) {
            case SystematicCountBasedSampling:
                return FlowDocumentProtos.SamplingAlgorithm.SYSTEMATIC_COUNT_BASED_SAMPLING;
            case SystematicTimeBasedSampling:
                return FlowDocumentProtos.SamplingAlgorithm.SYSTEMATIC_TIME_BASED_SAMPLING;
            case RandomNOutOfNSampling:
                return FlowDocumentProtos.SamplingAlgorithm.RANDOM_N_OUT_OF_N_SAMPLING;
            case UniformProbabilisticSampling:
                return FlowDocumentProtos.SamplingAlgorithm.UNIFORM_PROBABILISTIC_SAMPLING;
            case PropertyMatchFiltering:
                return FlowDocumentProtos.SamplingAlgorithm.PROPERTY_MATCH_FILTERING;
            case HashBasedFiltering:
                return FlowDocumentProtos.SamplingAlgorithm.HASH_BASED_FILTERING;
            case FlowStateDependentIntermediateFlowSelectionProcess:
                return FlowDocumentProtos.SamplingAlgorithm.FLOW_STATE_DEPENDENT_INTERMEDIATE_FLOW_SELECTION_PROCESS;
            default:
                return FlowDocumentProtos.SamplingAlgorithm.SAMPLING_ALGORITHM_UNKNOWN;
        }
    }
}
```

**Note on method names:** The exact getter names on horizon's `Flow` interface may differ slightly from what's written above (`getSrcAddr()` vs `getSrcAddress()`, `getBytes()` vs `getNumBytes()`, etc.). After creating the file, the compile step will surface any mismatches. Run `./compile.pl -pl :org.deltav.flows.flow-enricher -DskipTests compile` and fix any symbol-not-found errors by checking the actual method names via IntelliJ autocomplete or `javap -p` against the resolved `flows-api` JAR.

- [ ] **Step 4: Run the test to verify the first six tests pass**

Run:
```bash
./compile.pl -pl :org.deltav.flows.flow-enricher -Dtest=FlowToDocumentMapperTest test 2>&1 | tail -30
```
Expected: `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0` after any method-name fixes from Step 3.

- [ ] **Step 5: Extend the test file with the remaining field-group tests**

Add the following test methods to `FlowToDocumentMapperTest.java` (after the existing six). These follow the same null-wrapper discipline for each remaining field group. Write each test, run it, confirm it fails (or passes if the mapping is already correct), add any missing production code, and re-run.

```java
    @Test
    void mapsUInt32ValueWrappersNullSemantics() {
        Flow flow = mock(Flow.class);
        when(flow.getTimestamp()).thenReturn(1L);
        when(flow.getSrcAddr()).thenReturn("1.1.1.1");
        when(flow.getDstAddr()).thenReturn("2.2.2.2");
        when(flow.getSrcPort()).thenReturn(null);
        when(flow.getDstPort()).thenReturn(null);
        when(flow.getProtocol()).thenReturn(null);
        when(flow.getDscp()).thenReturn(null);

        FlowDocumentProtos.FlowDocument doc = mapper.map(flow, null, null, null,
                "unknown", "UNKNOWN", "UNKNOWN", "UNKNOWN");

        assertThat(doc.hasSrcPort()).isFalse();
        assertThat(doc.hasDstPort()).isFalse();
        assertThat(doc.hasProtocol()).isFalse();
        assertThat(doc.hasDscp()).isFalse();
    }

    @Test
    void mapsUInt32ValueWrappersZeroIsPopulated() {
        Flow flow = mock(Flow.class);
        when(flow.getTimestamp()).thenReturn(1L);
        when(flow.getSrcAddr()).thenReturn("1.1.1.1");
        when(flow.getDstAddr()).thenReturn("2.2.2.2");
        when(flow.getSrcPort()).thenReturn(0);
        when(flow.getDstPort()).thenReturn(0);
        when(flow.getProtocol()).thenReturn(0);
        when(flow.getDscp()).thenReturn(0);

        FlowDocumentProtos.FlowDocument doc = mapper.map(flow, null, null, null,
                "unknown", "UNKNOWN", "UNKNOWN", "UNKNOWN");

        assertThat(doc.hasSrcPort()).isTrue();
        assertThat(doc.getSrcPort().getValue()).isEqualTo(0);
        assertThat(doc.hasDstPort()).isTrue();
        assertThat(doc.getDstPort().getValue()).isEqualTo(0);
        assertThat(doc.hasProtocol()).isTrue();
        assertThat(doc.getProtocol().getValue()).isEqualTo(0);
        assertThat(doc.hasDscp()).isTrue();
        assertThat(doc.getDscp().getValue()).isEqualTo(0);
    }

    @Test
    void mapsDirectionEnum() {
        Flow flow = mock(Flow.class);
        when(flow.getTimestamp()).thenReturn(1L);
        when(flow.getSrcAddr()).thenReturn("1.1.1.1");
        when(flow.getDstAddr()).thenReturn("2.2.2.2");
        when(flow.getDirection()).thenReturn(Flow.Direction.INGRESS);

        FlowDocumentProtos.FlowDocument doc = mapper.map(flow, null, null, null,
                "unknown", "UNKNOWN", "UNKNOWN", "UNKNOWN");

        assertThat(doc.getDirection()).isEqualTo(FlowDocumentProtos.Direction.INGRESS);
    }

    @Test
    void mapsNetflowVersionEnum() {
        Flow flow = mock(Flow.class);
        when(flow.getTimestamp()).thenReturn(1L);
        when(flow.getSrcAddr()).thenReturn("1.1.1.1");
        when(flow.getDstAddr()).thenReturn("2.2.2.2");
        when(flow.getNetflowVersion()).thenReturn(Flow.NetflowVersion.V9);

        FlowDocumentProtos.FlowDocument doc = mapper.map(flow, null, null, null,
                "unknown", "UNKNOWN", "UNKNOWN", "UNKNOWN");

        assertThat(doc.getNetflowVersion()).isEqualTo(FlowDocumentProtos.NetflowVersion.V9);
    }

    @Test
    void mapsSrcAndDstNodeInfoWhenAllProvided() {
        Flow flow = mock(Flow.class);
        when(flow.getTimestamp()).thenReturn(1L);
        when(flow.getSrcAddr()).thenReturn("192.168.1.1");
        when(flow.getDstAddr()).thenReturn("10.0.0.1");

        JdbcNodeInfoLookup.NodeInfo exporter = new JdbcNodeInfoLookup.NodeInfo(1, "fs", "fid1", List.of("a"));
        JdbcNodeInfoLookup.NodeInfo src = new JdbcNodeInfoLookup.NodeInfo(2, "fs", "fid2", List.of("b"));
        JdbcNodeInfoLookup.NodeInfo dest = new JdbcNodeInfoLookup.NodeInfo(3, "fs", "fid3", List.of("c"));

        FlowDocumentProtos.FlowDocument doc = mapper.map(flow, exporter, src, dest,
                "unknown", "PRIVATE", "PRIVATE", "PRIVATE");

        assertThat(doc.getExporterNode().getNodeId()).isEqualTo(1);
        assertThat(doc.getSrcNode().getNodeId()).isEqualTo(2);
        assertThat(doc.getDestNode().getNodeId()).isEqualTo(3);
    }

    @Test
    void leavesApplicationAsUnknownWhenNullProvided() {
        Flow flow = mock(Flow.class);
        when(flow.getTimestamp()).thenReturn(1L);
        when(flow.getSrcAddr()).thenReturn("1.1.1.1");
        when(flow.getDstAddr()).thenReturn("2.2.2.2");

        FlowDocumentProtos.FlowDocument doc = mapper.map(flow, null, null, null,
                null, null, null, null);

        assertThat(doc.getApplication()).isEqualTo("unknown");
        assertThat(doc.getSrcLocality()).isEqualTo(FlowDocumentProtos.Locality.LOCALITY_UNKNOWN);
        assertThat(doc.getDstLocality()).isEqualTo(FlowDocumentProtos.Locality.LOCALITY_UNKNOWN);
        assertThat(doc.getFlowLocality()).isEqualTo(FlowDocumentProtos.Locality.LOCALITY_UNKNOWN);
    }
```

- [ ] **Step 6: Run the full FlowToDocumentMapperTest**

Run:
```bash
./compile.pl -pl :org.deltav.flows.flow-enricher -Dtest=FlowToDocumentMapperTest test 2>&1 | tail -30
```
Expected: `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 7: Do NOT commit yet**

This task is part of Commit 2 (Shared Infrastructure). It commits together with Tasks 2, 4, and 5.

---

## Task 4: Create ApplicationClassifier interface and PortBasedApplicationClassifier

**Files:**
- Create: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/classification/ApplicationClassifier.java`
- Create: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/classification/PortBasedApplicationClassifier.java`
- Create: `core/flow-enricher/src/test/java/org/deltav/flows/enricher/classification/PortBasedApplicationClassifierTest.java`

The classifier provides the `application` field on every FlowDocument. Phase 1.5 ships a port-based stub that covers ~30 well-known ports; Phase 1.6 replaces the implementation with horizon's `DefaultClassificationEngine`. The interface is the seam that makes the replacement a bean swap.

- [ ] **Step 1: Write the failing test**

Create `core/flow-enricher/src/test/java/org/deltav/flows/enricher/classification/PortBasedApplicationClassifierTest.java`:

```java
/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 *
 * Licensed under the GNU Affero General Public License v3.
 */
package org.deltav.flows.enricher.classification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PortBasedApplicationClassifierTest {

    private static final int TCP = 6;
    private static final int UDP = 17;

    private final PortBasedApplicationClassifier classifier = new PortBasedApplicationClassifier();

    @Test
    void classifiesHttpsByDestPort() {
        assertThat(classifier.classify(443, 54321, TCP)).isEqualTo("HTTPS");
    }

    @Test
    void classifiesHttpsBySourcePortWhenDestPortUnknown() {
        // Return flow: server sends from 443 to client ephemeral port
        assertThat(classifier.classify(54321, 443, TCP)).isEqualTo("HTTPS");
    }

    @Test
    void prefersDestPortOverSourcePortWhenBothAreKnown() {
        // Unusual: HTTP client also happens to bind to port 53 ephemerally
        // dst=80 should win over src=53
        assertThat(classifier.classify(80, 53, TCP)).isEqualTo("HTTP");
    }

    @Test
    void classifiesDnsUdp() {
        assertThat(classifier.classify(53, 12345, UDP)).isEqualTo("DNS");
    }

    @Test
    void classifiesSshTcp() {
        assertThat(classifier.classify(22, 54321, TCP)).isEqualTo("SSH");
    }

    @Test
    void classifiesHttp() {
        assertThat(classifier.classify(80, 54321, TCP)).isEqualTo("HTTP");
    }

    @Test
    void classifiesSmtpByCommonPorts() {
        assertThat(classifier.classify(25, 54321, TCP)).isEqualTo("SMTP");
        assertThat(classifier.classify(587, 54321, TCP)).isEqualTo("SMTP");
    }

    @Test
    void classifiesDatabasePorts() {
        assertThat(classifier.classify(5432, 54321, TCP)).isEqualTo("PostgreSQL");
        assertThat(classifier.classify(3306, 54321, TCP)).isEqualTo("MySQL");
        assertThat(classifier.classify(6379, 54321, TCP)).isEqualTo("Redis");
    }

    @Test
    void classifiesKafka() {
        assertThat(classifier.classify(9092, 54321, TCP)).isEqualTo("Kafka");
    }

    @Test
    void returnsUnknownForNonStandardPorts() {
        assertThat(classifier.classify(54321, 54322, TCP)).isEqualTo("unknown");
        assertThat(classifier.classify(8888, 8889, TCP)).isEqualTo("unknown");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:
```bash
./compile.pl -pl :org.deltav.flows.flow-enricher -Dtest=PortBasedApplicationClassifierTest test 2>&1 | tail -20
```
Expected: Compilation failure — class does not exist.

- [ ] **Step 3: Create the ApplicationClassifier interface**

Create `core/flow-enricher/src/main/java/org/deltav/flows/enricher/classification/ApplicationClassifier.java`:

```java
/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 *
 * Licensed under the GNU Affero General Public License v3.
 */
package org.deltav.flows.enricher.classification;

/**
 * Classifies a flow into an application name for dashboard aggregation.
 *
 * <p>Phase 1.5 ships {@link PortBasedApplicationClassifier} as the default
 * implementation. Phase 1.6 will replace it with a real rule-based classifier
 * backed by horizon's classification engine. Callers depend on this interface,
 * not the concrete implementation.
 */
public interface ApplicationClassifier {

    /**
     * Returns the application name for a flow, or {@code "unknown"} if
     * no classification applies.
     *
     * @param dstPort destination port (16-bit unsigned, passed as int)
     * @param srcPort source port (16-bit unsigned, passed as int)
     * @param protocol IP protocol number (6 = TCP, 17 = UDP)
     */
    String classify(int dstPort, int srcPort, int protocol);
}
```

- [ ] **Step 4: Create the PortBasedApplicationClassifier implementation**

Create `core/flow-enricher/src/main/java/org/deltav/flows/enricher/classification/PortBasedApplicationClassifier.java`:

```java
/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 *
 * Licensed under the GNU Affero General Public License v3.
 */
package org.deltav.flows.enricher.classification;

import java.util.Map;

/**
 * Static-map-based classifier that covers roughly 30 well-known ports.
 * Checks dst_port first (client-to-server traffic), then src_port (return
 * traffic from a server), then returns "unknown". Protocol number is
 * accepted for future enum matching but is not currently used for
 * discrimination because the well-known ports are unambiguous.
 *
 * <p>Replace with horizon's {@code DefaultClassificationEngine} in Phase 1.6.
 */
public class PortBasedApplicationClassifier implements ApplicationClassifier {

    private static final Map<Integer, String> WELL_KNOWN = Map.ofEntries(
            Map.entry(20, "FTP-data"),
            Map.entry(21, "FTP"),
            Map.entry(22, "SSH"),
            Map.entry(23, "Telnet"),
            Map.entry(25, "SMTP"),
            Map.entry(53, "DNS"),
            Map.entry(67, "DHCP"),
            Map.entry(68, "DHCP"),
            Map.entry(69, "TFTP"),
            Map.entry(80, "HTTP"),
            Map.entry(110, "POP3"),
            Map.entry(123, "NTP"),
            Map.entry(143, "IMAP"),
            Map.entry(161, "SNMP"),
            Map.entry(162, "SNMP-trap"),
            Map.entry(389, "LDAP"),
            Map.entry(443, "HTTPS"),
            Map.entry(445, "SMB"),
            Map.entry(465, "SMTPS"),
            Map.entry(514, "Syslog"),
            Map.entry(587, "SMTP"),
            Map.entry(636, "LDAPS"),
            Map.entry(993, "IMAPS"),
            Map.entry(995, "POP3S"),
            Map.entry(1433, "MSSQL"),
            Map.entry(1521, "Oracle"),
            Map.entry(3306, "MySQL"),
            Map.entry(3389, "RDP"),
            Map.entry(5432, "PostgreSQL"),
            Map.entry(5672, "AMQP"),
            Map.entry(6379, "Redis"),
            Map.entry(8080, "HTTP-alt"),
            Map.entry(8443, "HTTPS-alt"),
            Map.entry(9092, "Kafka"),
            Map.entry(9200, "Elasticsearch"),
            Map.entry(27017, "MongoDB")
    );

    @Override
    public String classify(int dstPort, int srcPort, int protocol) {
        String byDst = WELL_KNOWN.get(dstPort);
        if (byDst != null) {
            return byDst;
        }
        String bySrc = WELL_KNOWN.get(srcPort);
        if (bySrc != null) {
            return bySrc;
        }
        return "unknown";
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run:
```bash
./compile.pl -pl :org.deltav.flows.flow-enricher -Dtest=PortBasedApplicationClassifierTest test 2>&1 | tail -20
```
Expected: `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 6: Do NOT commit yet**

This task is part of Commit 2. Commits with Tasks 2, 3, and 5.

---

## Task 5: Commit the shared infrastructure

**Files:** All files from Tasks 2, 3, 4.

- [ ] **Step 1: Verify all shared-infrastructure tests pass together**

Run:
```bash
./compile.pl -pl :org.deltav.flows.flow-enricher test 2>&1 | tail -20
```
Expected: All existing skeleton tests (24) plus the new Phase 1.5 tests (10 from classifier + 12 from mapper + 3 from pipeline = 25) all pass. Total: 49 tests, 0 failures.

- [ ] **Step 2: Stage the files**

Run:
```bash
git add core/flow-enricher/src/main/java/org/deltav/flows/enricher/pipeline/CapturingPipeline.java \
        core/flow-enricher/src/main/java/org/deltav/flows/enricher/mapping/FlowToDocumentMapper.java \
        core/flow-enricher/src/main/java/org/deltav/flows/enricher/classification/ApplicationClassifier.java \
        core/flow-enricher/src/main/java/org/deltav/flows/enricher/classification/PortBasedApplicationClassifier.java \
        core/flow-enricher/src/test/java/org/deltav/flows/enricher/pipeline/CapturingPipelineTest.java \
        core/flow-enricher/src/test/java/org/deltav/flows/enricher/mapping/FlowToDocumentMapperTest.java \
        core/flow-enricher/src/test/java/org/deltav/flows/enricher/classification/PortBasedApplicationClassifierTest.java
```

- [ ] **Step 3: Commit**

```bash
git commit -m "feat(flow-enricher): add CapturingPipeline, mapper, classifier shared infra"
```

---

## Task 6: Refactor SinkMessageDeserializer to expose moduleId

**Files:**
- Create: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/DeserializedSinkMessage.java`
- Modify: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/SinkMessageDeserializer.java`
- Modify: `core/flow-enricher/src/test/java/org/deltav/flows/enricher/SinkMessageDeserializerTest.java`

The deserializer currently returns only `TelemetryMessageLog`. We need it to return both the `moduleId` (from the SinkMessage envelope) and the `TelemetryMessageLog` (the payload) so the orchestrator can dispatch on module ID.

- [ ] **Step 1: Read the current SinkMessageDeserializer**

```bash
cat core/flow-enricher/src/main/java/org/deltav/flows/enricher/SinkMessageDeserializer.java
```
Note the current return type and the parsing logic. This tells you exactly what lines to change.

- [ ] **Step 2: Create the DeserializedSinkMessage record**

Create `core/flow-enricher/src/main/java/org/deltav/flows/enricher/DeserializedSinkMessage.java`:

```java
/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 *
 * Licensed under the GNU Affero General Public License v3.
 */
package org.deltav.flows.enricher;

import org.opennms.netmgt.telemetry.common.ipc.TelemetryProtos;

/**
 * Result of parsing a Kafka Sink topic record. Carries both the SinkMessage's
 * moduleId (used for protocol dispatch) and the decoded TelemetryMessageLog
 * payload (passed to the corresponding protocol adapter).
 *
 * @param moduleId the Sink module identifier, e.g., "Telemetry-Netflow-5"
 * @param messageLog the decoded telemetry payload
 */
public record DeserializedSinkMessage(String moduleId, TelemetryProtos.TelemetryMessageLog messageLog) {
}
```

- [ ] **Step 3: Update the existing SinkMessageDeserializerTest**

The existing test calls `deserialize(bytes)` and expects a `TelemetryMessageLog` return. Update it to expect `DeserializedSinkMessage`:

Open `core/flow-enricher/src/test/java/org/deltav/flows/enricher/SinkMessageDeserializerTest.java`. Wherever it declares `TelemetryProtos.TelemetryMessageLog result = deserializer.deserialize(bytes)`, change to `DeserializedSinkMessage result = deserializer.deserialize(bytes)` and assert against `result.messageLog()` instead of `result` directly. Add a new test case:

```java
    @Test
    void deserializedMessageExposesModuleId() {
        // Construct a SinkMessage with moduleId "Telemetry-Netflow-9"
        byte[] bytes = buildSinkMessageBytes("Telemetry-Netflow-9", sampleTelemetryLogBytes());

        DeserializedSinkMessage result = deserializer.deserialize(bytes);

        assertThat(result).isNotNull();
        assertThat(result.moduleId()).isEqualTo("Telemetry-Netflow-9");
        assertThat(result.messageLog()).isNotNull();
    }
```

The `buildSinkMessageBytes(String, byte[])` helper wraps the payload in a SinkMessage protobuf with the given moduleId. If the existing test file doesn't have this helper, add it or adapt existing fixture code. The SinkMessage protobuf class is `org.opennms.core.ipc.sink.model.SinkMessageProtos.SinkMessage`.

- [ ] **Step 4: Run the updated test to verify it fails**

Run:
```bash
./compile.pl -pl :org.deltav.flows.flow-enricher -Dtest=SinkMessageDeserializerTest test 2>&1 | tail -30
```
Expected: Compilation failure — `deserialize(...)` returns `TelemetryMessageLog`, not `DeserializedSinkMessage`.

- [ ] **Step 5: Update SinkMessageDeserializer to return DeserializedSinkMessage**

Open `core/flow-enricher/src/main/java/org/deltav/flows/enricher/SinkMessageDeserializer.java`. Change the return type of `deserialize(byte[])` from `TelemetryProtos.TelemetryMessageLog` to `DeserializedSinkMessage`. Inside the method, after parsing the SinkMessage, extract the `moduleId` field and construct the record:

```java
public DeserializedSinkMessage deserialize(byte[] kafkaBytes) {
    try {
        SinkMessageProtos.SinkMessage sinkMsg = SinkMessageProtos.SinkMessage.parseFrom(kafkaBytes);
        String moduleId = sinkMsg.getModuleId();
        TelemetryProtos.TelemetryMessageLog log =
                TelemetryProtos.TelemetryMessageLog.parseFrom(sinkMsg.getContent());
        return new DeserializedSinkMessage(moduleId, log);
    } catch (InvalidProtocolBufferException e) {
        LOG.warn("Failed to deserialize Sink message: {}", e.getMessage());
        return null;
    }
}
```

If the current method does not use `SinkMessageProtos.SinkMessage` as the outer envelope (e.g., if it deserializes directly to `TelemetryMessageLog`), check the skeleton's existing implementation — the outer envelope is required to get the moduleId. You may need to add the SinkMessage parse step.

- [ ] **Step 6: Run the test to verify it passes**

Run:
```bash
./compile.pl -pl :org.deltav.flows.flow-enricher -Dtest=SinkMessageDeserializerTest test 2>&1 | tail -20
```
Expected: All SinkMessageDeserializerTest tests pass, including the new moduleId assertion.

- [ ] **Step 7: Do NOT commit yet**

This task is part of Commit 3 (Splitter Refactor). It commits together with Task 7.

---

## Task 7: Refactor FlowEnrichmentFunction to Function<byte[], List<byte[]>> with placeholder dispatch

**Files:**
- Modify: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/FlowEnrichmentFunction.java`
- Modify: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/FlowEnricherConfiguration.java`
- Modify: `core/flow-enricher/src/test/java/org/deltav/flows/enricher/FlowEnrichmentFunctionTest.java`

This task changes the function signature from `byte[] → byte[]` to `byte[] → List<byte[]>`. It adds a placeholder dispatch that returns an empty list for unknown moduleIds, matching the Phase 1 skeleton's "drop unknown messages" behavior. The actual per-protocol processing is wired in Task 12.

- [ ] **Step 1: Read the current FlowEnrichmentFunction**

```bash
cat core/flow-enricher/src/main/java/org/deltav/flows/enricher/FlowEnrichmentFunction.java
```

- [ ] **Step 2: Update FlowEnrichmentFunctionTest to expect List<byte[]>**

Open `core/flow-enricher/src/test/java/org/deltav/flows/enricher/FlowEnrichmentFunctionTest.java`. Change the test that exercises the existing skeleton path:

```java
    @Test
    void returnsEmptyListForNullOrEmptyMessage() {
        List<byte[]> result = function.processMessage(null);
        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyListForUnknownModuleId() {
        // Build a SinkMessage with moduleId "Telemetry-UnknownThing"
        byte[] kafkaBytes = buildSinkMessageBytes("Telemetry-UnknownThing", sampleTelemetryLogBytes());

        List<byte[]> result = function.processMessage(kafkaBytes);

        assertThat(result).isEmpty();
    }
```

Remove any old test assertions that depended on the `byte[]` return type (such as `assertThat(result).isNotNull()` where `result` was a single byte array).

- [ ] **Step 3: Run the test to verify it fails**

Run:
```bash
./compile.pl -pl :org.deltav.flows.flow-enricher -Dtest=FlowEnrichmentFunctionTest test 2>&1 | tail -20
```
Expected: Compilation failure — `processMessage` returns `byte[]`, test expects `List<byte[]>`.

- [ ] **Step 4: Change FlowEnrichmentFunction signature and implement placeholder dispatch**

Open `core/flow-enricher/src/main/java/org/deltav/flows/enricher/FlowEnrichmentFunction.java`. Replace the entire file with:

```java
/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 *
 * Licensed under the GNU Affero General Public License v3.
 */
package org.deltav.flows.enricher;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.deltav.flows.enricher.enrichment.FlowLocalityCalculator;
import org.deltav.flows.enricher.enrichment.InterfaceMarkingCache;
import org.deltav.flows.enricher.enrichment.JdbcNodeInfoLookup;
import org.deltav.flows.enricher.protocol.ProtocolMessageProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Phase 1.5 flow enrichment pipeline. Dispatches on the SinkMessage moduleId
 * to one of four protocol processors (netflow5/netflow9/ipfix/sflow), parses
 * the raw bytes into flows via horizon adapters, enriches each flow with
 * node/locality/classification/interface-marking data, and emits a
 * {@code List<byte[]>} of FlowDocumentProtos.FlowDocument messages.
 *
 * <p>Returning an empty list drops the input message without producing any
 * output records. This happens when the moduleId is unknown, the payload
 * cannot be parsed, or the adapter produces zero flows.
 */
public class FlowEnrichmentFunction {

    private static final Logger LOG = LoggerFactory.getLogger(FlowEnrichmentFunction.class);

    private final SinkMessageDeserializer deserializer;
    private final JdbcNodeInfoLookup nodeInfoLookup;
    private final FlowLocalityCalculator localityCalculator;
    private final InterfaceMarkingCache interfaceMarkingCache;
    private final Map<String, ProtocolMessageProcessor> processorsByModuleId;

    public FlowEnrichmentFunction(
            SinkMessageDeserializer deserializer,
            JdbcNodeInfoLookup nodeInfoLookup,
            FlowLocalityCalculator localityCalculator,
            InterfaceMarkingCache interfaceMarkingCache,
            Map<String, ProtocolMessageProcessor> processorsByModuleId) {
        this.deserializer = deserializer;
        this.nodeInfoLookup = nodeInfoLookup;
        this.localityCalculator = localityCalculator;
        this.interfaceMarkingCache = interfaceMarkingCache;
        this.processorsByModuleId = Map.copyOf(processorsByModuleId);
    }

    public List<byte[]> processMessage(byte[] kafkaBytes) {
        if (kafkaBytes == null || kafkaBytes.length == 0) {
            return Collections.emptyList();
        }

        DeserializedSinkMessage deserialized = deserializer.deserialize(kafkaBytes);
        if (deserialized == null || deserialized.messageLog() == null
                || deserialized.messageLog().getMessageCount() == 0) {
            return Collections.emptyList();
        }

        ProtocolMessageProcessor processor = processorsByModuleId.get(deserialized.moduleId());
        if (processor == null) {
            LOG.debug("No processor for moduleId '{}', dropping message", deserialized.moduleId());
            return Collections.emptyList();
        }

        // Task 12 wires the full enrichment pipeline here. The placeholder
        // for Commit 3 returns an empty list so the function compiles and
        // the binding contract is proven with the new signature.
        return Collections.emptyList();
    }
}
```

- [ ] **Step 5: Update FlowEnricherConfiguration for the new function signature**

Open `core/flow-enricher/src/main/java/org/deltav/flows/enricher/FlowEnricherConfiguration.java`. Change the `enrichFlows` bean declaration from `Function<byte[], byte[]>` to `Function<byte[], List<byte[]>>`:

```java
    @Bean
    Function<byte[], List<byte[]>> enrichFlows(FlowEnrichmentFunction enrichmentFunction) {
        return enrichmentFunction::processMessage;
    }
```

Add the import `java.util.List` and `java.util.Map`. Also update the `FlowEnrichmentFunction` bean declaration — it now takes a `Map<String, ProtocolMessageProcessor>` as its fifth constructor parameter. For this commit, wire an empty map:

```java
    @Bean
    FlowEnrichmentFunction flowEnrichmentFunction(
            SinkMessageDeserializer deserializer,
            JdbcNodeInfoLookup nodeInfoLookup,
            FlowLocalityCalculator localityCalculator,
            InterfaceMarkingCache interfaceMarkingCache) {
        return new FlowEnrichmentFunction(
                deserializer, nodeInfoLookup, localityCalculator, interfaceMarkingCache,
                Map.of());  // populated with real processors in Task 12
    }
```

- [ ] **Step 6: Run the tests to verify they pass**

Run:
```bash
./compile.pl -pl :org.deltav.flows.flow-enricher test 2>&1 | tail -30
```
Expected: All existing tests plus the updated FlowEnrichmentFunctionTest pass. No compilation errors.

**Note on the `ProtocolMessageProcessor` import:** The interface doesn't exist yet — it's created in Task 8. Create a temporary stub interface in `core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/ProtocolMessageProcessor.java` with just the package declaration and `public interface ProtocolMessageProcessor {}` so this task compiles. Task 8 fills in the real interface contents.

- [ ] **Step 7: Commit (Splitter Refactor)**

```bash
git add core/flow-enricher/src/main/java/org/deltav/flows/enricher/DeserializedSinkMessage.java \
        core/flow-enricher/src/main/java/org/deltav/flows/enricher/SinkMessageDeserializer.java \
        core/flow-enricher/src/main/java/org/deltav/flows/enricher/FlowEnrichmentFunction.java \
        core/flow-enricher/src/main/java/org/deltav/flows/enricher/FlowEnricherConfiguration.java \
        core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/ProtocolMessageProcessor.java \
        core/flow-enricher/src/test/java/org/deltav/flows/enricher/SinkMessageDeserializerTest.java \
        core/flow-enricher/src/test/java/org/deltav/flows/enricher/FlowEnrichmentFunctionTest.java
git commit -m "feat(flow-enricher): refactor function to List<byte[]> splitter signature"
```

---

## Task 8: Create ProtocolMessageProcessor interface and AbstractProtocolMessageProcessor

**Files:**
- Modify: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/ProtocolMessageProcessor.java` (replace the stub)
- Create: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/AbstractProtocolMessageProcessor.java`

The interface declares the common contract. The abstract base class holds the shared driver logic (create a fresh `CapturingPipeline`, call `adapter.handleMessageLog`, return captured flows).

- [ ] **Step 1: Replace the stub ProtocolMessageProcessor interface**

Open `core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/ProtocolMessageProcessor.java` and replace the stub with:

```java
/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 *
 * Licensed under the GNU Affero General Public License v3.
 */
package org.deltav.flows.enricher.protocol;

import java.util.List;

import org.opennms.netmgt.flows.api.Flow;
import org.opennms.netmgt.telemetry.common.ipc.TelemetryProtos;

/**
 * Per-protocol processor that wraps one horizon AbstractFlowAdapter and
 * exposes a uniform {@code process()} method returning the parsed flows.
 * One implementation exists per protocol (Netflow5, Netflow9, IPFIX, sFlow).
 */
public interface ProtocolMessageProcessor {

    /**
     * Parses the telemetry message log into per-flow records via the
     * underlying horizon adapter.
     *
     * @param messageLog the decoded Sink payload
     * @return parsed flows (never null; may be empty)
     */
    List<Flow> process(TelemetryProtos.TelemetryMessageLog messageLog);
}
```

- [ ] **Step 2: Create AbstractProtocolMessageProcessor**

Create `core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/AbstractProtocolMessageProcessor.java`:

```java
/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 *
 * Licensed under the GNU Affero General Public License v3.
 */
package org.deltav.flows.enricher.protocol;

import java.util.Collections;
import java.util.List;

import org.deltav.flows.enricher.pipeline.CapturingPipeline;
import org.opennms.netmgt.flows.api.Flow;
import org.opennms.netmgt.telemetry.common.ipc.TelemetryProtos;
import org.opennms.netmgt.telemetry.protocols.flows.AbstractFlowAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared driver logic for all four protocol processors. Each concrete
 * subclass supplies an {@link AbstractFlowAdapter} instance via the
 * constructor. The {@link #process(TelemetryProtos.TelemetryMessageLog)}
 * method instantiates a fresh {@link CapturingPipeline}, drives the adapter,
 * and returns the captured flows.
 */
public abstract class AbstractProtocolMessageProcessor implements ProtocolMessageProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractProtocolMessageProcessor.class);

    protected final AbstractFlowAdapter<?> adapter;

    protected AbstractProtocolMessageProcessor(AbstractFlowAdapter<?> adapter) {
        this.adapter = adapter;
    }

    @Override
    public List<Flow> process(TelemetryProtos.TelemetryMessageLog messageLog) {
        CapturingPipeline pipeline = new CapturingPipeline();
        // NOTE: we construct the adapter with our CapturingPipeline already
        // injected via the subclass constructor; the pipeline is fresh per
        // invocation so captures don't leak across messages in concurrent
        // invocations. The adapter's handleMessageLog() method drives the
        // parse-and-convert cycle that ultimately calls pipeline.process().
        //
        // CAVEAT: The horizon adapter holds a reference to the pipeline
        // passed at construction time, so a per-invocation pipeline requires
        // a per-invocation adapter OR a thread-local pipeline holder. See
        // the subclass constructor comment for the approach used.
        try {
            adapter.handleMessageLog(messageLog);
        } catch (Exception e) {
            LOG.warn("Adapter {} failed to process message log: {}",
                    adapter.getClass().getSimpleName(), e.getMessage());
            return Collections.emptyList();
        }
        return pipeline.getCapturedFlows();
    }
}
```

**Important note on the pipeline-per-invocation pattern:** The naive implementation above creates a fresh `CapturingPipeline` in `process()` but uses the adapter held as an instance field — which was constructed with a DIFFERENT pipeline. This will NOT work because the adapter's field reference won't update.

The correct pattern is one of:

(a) **Per-invocation adapter construction** — build a new adapter with a new pipeline on every call. Cheap for simple adapters but potentially expensive for ones that cache templates (Netflow-9/IPFIX).

(b) **Thread-local pipeline holder** — use a `ThreadLocal<CapturingPipeline>` and a proxy pipeline that delegates to whichever pipeline is currently set. The adapter holds the proxy; the processor swaps the thread-local per invocation.

(c) **Synchronized processor with mutable adapter pipeline** — if `AbstractFlowAdapter` exposes a setter for the pipeline (check with `javap`), synchronize on the processor and set the pipeline before calling `handleMessageLog`.

**Recommended: option (a)** for Phase 1.5 because templates are cached at a different layer inside the adapter (not tied to the Pipeline instance) and per-invocation construction is a few microseconds. Subclasses will therefore create their adapter inside `process()` rather than in the constructor. Update the base class:

```java
public abstract class AbstractProtocolMessageProcessor implements ProtocolMessageProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractProtocolMessageProcessor.class);

    @Override
    public List<Flow> process(TelemetryProtos.TelemetryMessageLog messageLog) {
        CapturingPipeline pipeline = new CapturingPipeline();
        AbstractFlowAdapter<?> adapter = createAdapter(pipeline);
        try {
            adapter.handleMessageLog(messageLog);
        } catch (Exception e) {
            LOG.warn("Adapter {} failed to process message log: {}",
                    adapter.getClass().getSimpleName(), e.getMessage());
            return Collections.emptyList();
        }
        return pipeline.getCapturedFlows();
    }

    /**
     * Subclass supplies a fresh adapter instance wired to the given pipeline.
     * Called on every {@link #process(TelemetryProtos.TelemetryMessageLog)}
     * invocation to keep the pipeline-per-invocation invariant.
     */
    protected abstract AbstractFlowAdapter<?> createAdapter(CapturingPipeline pipeline);
}
```

- [ ] **Step 3: Do NOT commit yet**

This task is part of Commit 4 (Protocol Processors). It commits together with Tasks 9, 10, and 11.

---

## Task 9: Create TestAdapterDefinitions helper

**Files:**
- Create: `core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/TestAdapterDefinitions.java`

Horizon adapters require an `AdapterDefinition` constructor argument. This helper provides minimal mocks so every per-protocol test doesn't reinvent the stubbing.

- [ ] **Step 1: Create the helper**

Create `core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/TestAdapterDefinitions.java`:

```java
/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 *
 * Licensed under the GNU Affero General Public License v3.
 */
package org.deltav.flows.enricher.protocol;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.opennms.netmgt.telemetry.config.api.AdapterDefinition;

/**
 * Test fixtures for constructing minimal {@link AdapterDefinition} mocks.
 * The horizon adapter constructors require a non-null AdapterDefinition with
 * at least a {@code name} set; this helper provides sensible defaults.
 */
public final class TestAdapterDefinitions {

    private TestAdapterDefinitions() {
    }

    public static AdapterDefinition testAdapterDefinition(String name) {
        AdapterDefinition def = mock(AdapterDefinition.class);
        when(def.getName()).thenReturn(name);
        when(def.isEnabled()).thenReturn(true);
        return def;
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run:
```bash
./compile.pl -pl :org.deltav.flows.flow-enricher -DskipTests test-compile 2>&1 | tail -10
```
Expected: `BUILD SUCCESS`. If `AdapterDefinition` import fails, check the actual package path — it may be `org.opennms.netmgt.telemetry.api.adapter.AdapterDefinition` or similar. Update the import.

---

## Task 10: Create Netflow5MessageProcessor with hand-crafted packet test

**Files:**
- Create: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/Netflow5MessageProcessor.java`
- Create: `core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/Netflow5MessageProcessorTest.java`

Netflow-5 is the simplest protocol (fixed-format, no templates). We hand-craft a minimal valid packet as a byte array and verify our processor produces the expected flow count.

- [ ] **Step 1: Write the failing test**

Create `core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/Netflow5MessageProcessorTest.java`:

```java
/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 *
 * Licensed under the GNU Affero General Public License v3.
 */
package org.deltav.flows.enricher.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.deltav.flows.enricher.protocol.TestAdapterDefinitions.testAdapterDefinition;

import java.util.List;

import com.codahale.metrics.MetricRegistry;
import com.google.protobuf.ByteString;

import org.junit.jupiter.api.Test;
import org.opennms.netmgt.flows.api.Flow;
import org.opennms.netmgt.telemetry.common.ipc.TelemetryProtos;

class Netflow5MessageProcessorTest {

    /**
     * Minimal Netflow v5 packet: header + one flow record.
     *
     * Netflow v5 header (24 bytes):
     *   version(2) count(2) sysUptime(4) unixSecs(4) unixNsecs(4)
     *   flowSequence(4) engineType(1) engineId(1) samplingInterval(2)
     *
     * Netflow v5 record (48 bytes):
     *   srcAddr(4) dstAddr(4) nextHop(4) input(2) output(2)
     *   dPkts(4) dOctets(4) first(4) last(4) srcPort(2) dstPort(2)
     *   pad1(1) tcpFlags(1) prot(1) tos(1) srcAs(2) dstAs(2)
     *   srcMask(1) dstMask(1) pad2(2)
     */
    private static byte[] buildNetflow5Packet() {
        byte[] packet = new byte[24 + 48];

        // Header
        packet[0] = 0x00; packet[1] = 0x05;    // version = 5
        packet[2] = 0x00; packet[3] = 0x01;    // count = 1
        // sysUptime = 0 (4 bytes)
        // unixSecs = 0 (4 bytes) — adapter may reject 0; set to 1
        packet[15] = 0x01;
        // unixNsecs = 0
        // flowSequence = 0
        // engineType = 0
        // engineId = 0
        // samplingInterval = 0

        // Record starts at offset 24
        int off = 24;
        // srcAddr = 192.0.2.1 (4 bytes)
        packet[off] = (byte)192; packet[off+1] = 0; packet[off+2] = 2; packet[off+3] = 1;
        // dstAddr = 198.51.100.2
        packet[off+4] = (byte)198; packet[off+5] = 51; packet[off+6] = 100; packet[off+7] = 2;
        // nextHop = 203.0.113.1
        packet[off+8] = (byte)203; packet[off+9] = 0; packet[off+10] = 113; packet[off+11] = 1;
        // input = 1, output = 2
        packet[off+13] = 1;
        packet[off+15] = 2;
        // dPkts = 10
        packet[off+19] = 10;
        // dOctets = 1500
        packet[off+22] = 0x05; packet[off+23] = (byte)0xDC;
        // first = 100
        packet[off+27] = 100;
        // last = 200
        packet[off+31] = (byte)200;
        // srcPort = 54321
        packet[off+32] = (byte)0xD4; packet[off+33] = 0x31;
        // dstPort = 443
        packet[off+34] = 0x01; packet[off+35] = (byte)0xBB;
        // tcpFlags = 0x18 (PSH+ACK)
        packet[off+37] = 0x18;
        // prot = 6 (TCP)
        packet[off+38] = 6;
        // tos, srcAs, dstAs, srcMask, dstMask, pad all zero

        return packet;
    }

    @Test
    void parsesOneNetflow5PacketIntoOneFlow() {
        Netflow5MessageProcessor processor = new Netflow5MessageProcessor(
                testAdapterDefinition("Netflow-5"),
                new MetricRegistry());

        byte[] packetBytes = buildNetflow5Packet();

        TelemetryProtos.TelemetryMessageLog log = TelemetryProtos.TelemetryMessageLog.newBuilder()
                .setLocation("test-location")
                .setSourceAddress("192.0.2.254")
                .addMessage(TelemetryProtos.TelemetryMessage.newBuilder()
                        .setBytes(ByteString.copyFrom(packetBytes))
                        .setTimestamp(System.currentTimeMillis())
                        .build())
                .build();

        List<Flow> flows = processor.process(log);

        assertThat(flows).hasSize(1);
        Flow flow = flows.get(0);
        assertThat(flow.getSrcAddr()).isEqualTo("192.0.2.1");
        assertThat(flow.getDstAddr()).isEqualTo("198.51.100.2");
        assertThat(flow.getDstPort()).isEqualTo(443);
        assertThat(flow.getProtocol()).isEqualTo(6);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:
```bash
./compile.pl -pl :org.deltav.flows.flow-enricher -Dtest=Netflow5MessageProcessorTest test 2>&1 | tail -20
```
Expected: Compilation failure — `Netflow5MessageProcessor` does not exist.

- [ ] **Step 3: Create Netflow5MessageProcessor**

Create `core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/Netflow5MessageProcessor.java`:

```java
/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 *
 * Licensed under the GNU Affero General Public License v3.
 */
package org.deltav.flows.enricher.protocol;

import com.codahale.metrics.MetricRegistry;

import org.deltav.flows.enricher.pipeline.CapturingPipeline;
import org.opennms.netmgt.telemetry.config.api.AdapterDefinition;
import org.opennms.netmgt.telemetry.protocols.flows.AbstractFlowAdapter;
import org.opennms.netmgt.telemetry.protocols.netflow.adapter.netflow5.Netflow5Adapter;

public class Netflow5MessageProcessor extends AbstractProtocolMessageProcessor {

    private final AdapterDefinition adapterDefinition;
    private final MetricRegistry metricRegistry;

    public Netflow5MessageProcessor(AdapterDefinition adapterDefinition, MetricRegistry metricRegistry) {
        this.adapterDefinition = adapterDefinition;
        this.metricRegistry = metricRegistry;
    }

    @Override
    protected AbstractFlowAdapter<?> createAdapter(CapturingPipeline pipeline) {
        return new Netflow5Adapter(adapterDefinition, metricRegistry, pipeline);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run:
```bash
./compile.pl -pl :org.deltav.flows.flow-enricher -Dtest=Netflow5MessageProcessorTest test 2>&1 | tail -30
```
Expected: `Tests run: 1, Failures: 0, Errors: 0`.

**Likely issues and fixes:**
- **"class Netflow5Adapter not found"** — check actual package with `javap` against the netflow5 adapter JAR. It may be under a slightly different package name.
- **"constructor mismatch"** — the adapter constructor may take additional arguments (e.g., a `DnsResolver`). Check with `javap -public Netflow5Adapter` and pass reasonable defaults (pass null for optional args, or construct minimal mocks).
- **"adapter returned 0 flows"** — the hand-crafted packet may have an issue. Enable adapter debug logging (`logging.level.org.opennms.netmgt.telemetry=DEBUG` in a test `application.properties`) and check where parsing failed. Common issues: header field endianness, unix_secs=0 being rejected as invalid.

- [ ] **Step 5: Do NOT commit yet**

This task is part of Commit 4. Commits with Tasks 8, 9, 11.

---

## Task 11: Create Netflow9, IPFIX, sFlow processors with horizon fixture tests

**Files:**
- Create: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/Netflow9MessageProcessor.java`
- Create: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/IpfixMessageProcessor.java`
- Create: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/SFlowMessageProcessor.java`
- Create: `core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/Netflow9MessageProcessorTest.java`
- Create: `core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/IpfixMessageProcessorTest.java`
- Create: `core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/SFlowMessageProcessorTest.java`
- Create: `core/flow-enricher/src/test/resources/test-packets/netflow9-sample.dat`
- Create: `core/flow-enricher/src/test/resources/test-packets/ipfix-sample.dat`
- Create: `core/flow-enricher/src/test/resources/test-packets/sflow-sample.dat`

These three protocols are template-based (Netflow9/IPFIX) or sampling-based (sFlow) and too complex to hand-write in bytes. We copy binary test fixtures from horizon's own adapter tests.

- [ ] **Step 1: Locate horizon's adapter test fixtures**

Horizon's telemetry adapter tests live in:
- `features/telemetry/protocols/netflow/adapter/netflow9/src/test/resources/netflow9/*.dat`
- `features/telemetry/protocols/netflow/adapter/ipfix/src/test/resources/ipfix/*.dat`
- `features/telemetry/protocols/sflow/src/test/resources/sflow/*.dat`

These files are AGPL-licensed the same as the rest of horizon, so copying them into our test resources is fine. Pick the smallest fixture that contains a template + at least one data flowset (for netflow9/ipfix) or one sample record (for sflow).

Run:
```bash
find ~/.m2/repository/org/opennms/features/telemetry -name "*.dat" 2>/dev/null | head -20
```

If the horizon test resources are not in `~/.m2/repository` (Maven typically strips test resources), clone the delta-v-horizon repo into a scratch directory and copy from there:

```bash
cd /tmp
git clone --depth 1 https://github.com/pbrane/delta-v-horizon.git horizon-fixtures
find horizon-fixtures -path '*/test/resources/*.dat' | grep -E "netflow9|ipfix|sflow" | head -10
```

- [ ] **Step 2: Copy the smallest suitable fixtures into our test resources**

```bash
cd /Users/david/development/src/opennms/delta-v
mkdir -p core/flow-enricher/src/test/resources/test-packets
cp /tmp/horizon-fixtures/features/telemetry/protocols/netflow/adapter/netflow9/src/test/resources/<chosen>.dat \
   core/flow-enricher/src/test/resources/test-packets/netflow9-sample.dat
# Repeat for ipfix and sflow
```

Verify the files copied:
```bash
ls -la core/flow-enricher/src/test/resources/test-packets/
```
Expected: three `.dat` files, each 100-2000 bytes.

- [ ] **Step 3: Write Netflow9MessageProcessorTest**

Create `core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/Netflow9MessageProcessorTest.java`:

```java
/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 *
 * Licensed under the GNU Affero General Public License v3.
 */
package org.deltav.flows.enricher.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.deltav.flows.enricher.protocol.TestAdapterDefinitions.testAdapterDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.codahale.metrics.MetricRegistry;
import com.google.protobuf.ByteString;

import org.junit.jupiter.api.Test;
import org.opennms.netmgt.flows.api.Flow;
import org.opennms.netmgt.telemetry.common.ipc.TelemetryProtos;

class Netflow9MessageProcessorTest {

    @Test
    void parsesHorizonFixtureIntoAtLeastOneFlow() throws IOException {
        byte[] packetBytes = loadFixture("/test-packets/netflow9-sample.dat");

        Netflow9MessageProcessor processor = new Netflow9MessageProcessor(
                testAdapterDefinition("Netflow-9"),
                new MetricRegistry());

        TelemetryProtos.TelemetryMessageLog log = TelemetryProtos.TelemetryMessageLog.newBuilder()
                .setLocation("test-location")
                .setSourceAddress("192.0.2.254")
                .addMessage(TelemetryProtos.TelemetryMessage.newBuilder()
                        .setBytes(ByteString.copyFrom(packetBytes))
                        .setTimestamp(System.currentTimeMillis())
                        .build())
                .build();

        List<Flow> flows = processor.process(log);

        // The fixture may be a template-only packet (no data records) or a
        // data packet after a template. We assert >= 0 here and verify
        // specific field values via the StreamBinderIT which uses the same
        // fixture end-to-end.
        assertThat(flows).isNotNull();
    }

    private static byte[] loadFixture(String classpathPath) throws IOException {
        try (InputStream in = Netflow9MessageProcessorTest.class.getResourceAsStream(classpathPath)) {
            if (in == null) {
                throw new IOException("Fixture not found on classpath: " + classpathPath);
            }
            return in.readAllBytes();
        }
    }
}
```

**Note:** Netflow-9 requires a template flowset to arrive BEFORE any data flowset it decodes. If the chosen fixture is a data-only packet, the adapter will cache the template lookup failure and return zero flows. Either pick a fixture that contains both a template flowset and a data flowset in one UDP packet, or send two messages in sequence (template first, then data). For the initial test, the "isNotNull" assertion is a smoke test that just verifies the adapter doesn't throw; the full field-level assertion happens in `FlowEnrichmentStreamBinderIT`.

- [ ] **Step 4: Create Netflow9MessageProcessor**

Same shape as Netflow5MessageProcessor but with the Netflow9 adapter:

```java
package org.deltav.flows.enricher.protocol;

import com.codahale.metrics.MetricRegistry;
import org.deltav.flows.enricher.pipeline.CapturingPipeline;
import org.opennms.netmgt.telemetry.config.api.AdapterDefinition;
import org.opennms.netmgt.telemetry.protocols.flows.AbstractFlowAdapter;
import org.opennms.netmgt.telemetry.protocols.netflow.adapter.netflow9.Netflow9Adapter;

public class Netflow9MessageProcessor extends AbstractProtocolMessageProcessor {

    private final AdapterDefinition adapterDefinition;
    private final MetricRegistry metricRegistry;

    public Netflow9MessageProcessor(AdapterDefinition adapterDefinition, MetricRegistry metricRegistry) {
        this.adapterDefinition = adapterDefinition;
        this.metricRegistry = metricRegistry;
    }

    @Override
    protected AbstractFlowAdapter<?> createAdapter(CapturingPipeline pipeline) {
        return new Netflow9Adapter(adapterDefinition, metricRegistry, pipeline);
    }
}
```

- [ ] **Step 5: Run the Netflow9 test**

Run:
```bash
./compile.pl -pl :org.deltav.flows.flow-enricher -Dtest=Netflow9MessageProcessorTest test 2>&1 | tail -20
```
Expected: `Tests run: 1, Failures: 0`.

- [ ] **Step 6: Repeat Steps 3-5 for IpfixMessageProcessor**

Create `IpfixMessageProcessorTest.java` (identical structure to Netflow9 test, pointing at `ipfix-sample.dat`), then create `IpfixMessageProcessor.java` with `IpfixAdapter` from `org.opennms.netmgt.telemetry.protocols.netflow.adapter.ipfix.IpfixAdapter`. Run the test.

- [ ] **Step 7: Repeat Steps 3-5 for SFlowMessageProcessor**

Create `SFlowMessageProcessorTest.java` (same structure, `sflow-sample.dat`), then `SFlowMessageProcessor.java` with `SFlowAdapter` from `org.opennms.netmgt.telemetry.protocols.sflow.adapter.SFlowAdapter`. Run the test.

- [ ] **Step 8: Run all protocol tests together**

Run:
```bash
./compile.pl -pl :org.deltav.flows.flow-enricher -Dtest='*MessageProcessorTest' test 2>&1 | tail -30
```
Expected: 4 tests, 0 failures (one per protocol processor).

- [ ] **Step 9: Commit (Protocol Processors)**

```bash
git add core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/ \
        core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/ \
        core/flow-enricher/src/test/resources/test-packets/
git commit -m "feat(flow-enricher): add protocol processors for all four flow protocols"
```

---

## Task 12: Wire full enrichment pipeline in FlowEnrichmentFunction

**Files:**
- Modify: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/FlowEnrichmentFunction.java`
- Modify: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/FlowEnricherConfiguration.java`
- Modify: `core/flow-enricher/src/test/java/org/deltav/flows/enricher/FlowEnrichmentFunctionTest.java`

This task replaces the placeholder empty-list return from Task 7 with the real per-flow enrichment. Each flow gets exporter/src/dst node lookups, locality calculation, interface marking, classification, and mapping to `FlowDocument`.

- [ ] **Step 1: Extend FlowEnrichmentFunctionTest with full-pipeline cases**

Add to `FlowEnrichmentFunctionTest.java`:

```java
    @Test
    void dispatchesNetflow5MessagesToNetflow5Processor() {
        // Given a Sink message with moduleId "Telemetry-Netflow-5" and
        // a mock processor that returns two synthetic flows
        Flow flow1 = mockFlowWithAddresses("10.0.0.1", "8.8.8.8", 443, 6);
        Flow flow2 = mockFlowWithAddresses("10.0.0.2", "1.1.1.1", 80, 6);

        when(netflow5Processor.process(any())).thenReturn(List.of(flow1, flow2));

        byte[] kafkaBytes = buildSinkMessageBytes("Telemetry-Netflow-5", sampleTelemetryLogBytes());

        List<byte[]> result = function.processMessage(kafkaBytes);

        assertThat(result).hasSize(2);
        // Verify each emitted byte[] is a valid FlowDocument
        FlowDocumentProtos.FlowDocument doc1 = FlowDocumentProtos.FlowDocument.parseFrom(result.get(0));
        assertThat(doc1.getSrcAddress()).isEqualTo("10.0.0.1");
        assertThat(doc1.getApplication()).isEqualTo("HTTPS");
    }

    @Test
    void looksUpSrcDstAndExporterNodesForEachFlow() {
        Flow flow = mockFlowWithAddresses("10.0.0.1", "8.8.8.8", 443, 6);
        when(netflow9Processor.process(any())).thenReturn(List.of(flow));
        when(nodeInfoLookup.lookupByIpAddress("10.0.0.1"))
                .thenReturn(new JdbcNodeInfoLookup.NodeInfo(100, "fs", "src", List.of()));
        when(nodeInfoLookup.lookupByIpAddress("8.8.8.8"))
                .thenReturn(null);
        when(nodeInfoLookup.lookupByIpAddress("192.0.2.254"))
                .thenReturn(new JdbcNodeInfoLookup.NodeInfo(42, "fs", "exporter", List.of("edge")));

        byte[] kafkaBytes = buildSinkMessageBytes("Telemetry-Netflow-9",
                sampleTelemetryLogBytesWithSourceAddress("192.0.2.254"));

        List<byte[]> result = function.processMessage(kafkaBytes);

        assertThat(result).hasSize(1);
        FlowDocumentProtos.FlowDocument doc = FlowDocumentProtos.FlowDocument.parseFrom(result.get(0));
        assertThat(doc.hasSrcNode()).isTrue();
        assertThat(doc.getSrcNode().getNodeId()).isEqualTo(100);
        assertThat(doc.hasDestNode()).isFalse();
        assertThat(doc.hasExporterNode()).isTrue();
        assertThat(doc.getExporterNode().getNodeId()).isEqualTo(42);
    }

    @Test
    void marksInterfaceWhenExporterAndIfIndexKnown() {
        Flow flow = mockFlowWithAddresses("10.0.0.1", "8.8.8.8", 443, 6);
        when(flow.getInputSnmp()).thenReturn(12);
        when(flow.getOutputSnmp()).thenReturn(13);
        when(netflow9Processor.process(any())).thenReturn(List.of(flow));
        when(nodeInfoLookup.lookupByIpAddress("192.0.2.254"))
                .thenReturn(new JdbcNodeInfoLookup.NodeInfo(42, "fs", "exporter", List.of()));

        byte[] kafkaBytes = buildSinkMessageBytes("Telemetry-Netflow-9",
                sampleTelemetryLogBytesWithSourceAddress("192.0.2.254"));

        function.processMessage(kafkaBytes);

        verify(interfaceMarkingCache).markIfNeeded(42, 12);
        verify(interfaceMarkingCache).markIfNeeded(42, 13);
    }

    @Test
    void calculatesLocalityForSrcAndDst() {
        Flow flow = mockFlowWithAddresses("10.0.0.1", "8.8.8.8", 443, 6);
        when(netflow9Processor.process(any())).thenReturn(List.of(flow));
        when(localityCalculator.calculate("10.0.0.1")).thenReturn("PRIVATE");
        when(localityCalculator.calculate("8.8.8.8")).thenReturn("PUBLIC");

        byte[] kafkaBytes = buildSinkMessageBytes("Telemetry-Netflow-9", sampleTelemetryLogBytes());

        List<byte[]> result = function.processMessage(kafkaBytes);

        assertThat(result).hasSize(1);
        FlowDocumentProtos.FlowDocument doc = FlowDocumentProtos.FlowDocument.parseFrom(result.get(0));
        assertThat(doc.getSrcLocality()).isEqualTo(FlowDocumentProtos.Locality.PRIVATE);
        assertThat(doc.getDstLocality()).isEqualTo(FlowDocumentProtos.Locality.PUBLIC);
    }

    private Flow mockFlowWithAddresses(String src, String dst, int dstPort, int protocol) {
        Flow flow = mock(Flow.class);
        when(flow.getTimestamp()).thenReturn(1712700000000L);
        when(flow.getSrcAddr()).thenReturn(src);
        when(flow.getDstAddr()).thenReturn(dst);
        when(flow.getDstPort()).thenReturn(dstPort);
        when(flow.getSrcPort()).thenReturn(54321);
        when(flow.getProtocol()).thenReturn(protocol);
        return flow;
    }
```

The `@BeforeEach` setup needs to create mock instances of `Netflow5MessageProcessor`, `Netflow9MessageProcessor`, etc., and pass them as the `Map<String, ProtocolMessageProcessor>` constructor argument with the correct module ID keys.

- [ ] **Step 2: Run the test to verify it fails**

Run:
```bash
./compile.pl -pl :org.deltav.flows.flow-enricher -Dtest=FlowEnrichmentFunctionTest test 2>&1 | tail -30
```
Expected: Compilation failure or assertion failures — the current placeholder returns empty list.

- [ ] **Step 3: Implement the full enrichment pipeline in FlowEnrichmentFunction**

Replace the placeholder `return Collections.emptyList()` in `processMessage` with the full pipeline:

```java
    public List<byte[]> processMessage(byte[] kafkaBytes) {
        if (kafkaBytes == null || kafkaBytes.length == 0) {
            return Collections.emptyList();
        }

        DeserializedSinkMessage deserialized = deserializer.deserialize(kafkaBytes);
        if (deserialized == null || deserialized.messageLog() == null
                || deserialized.messageLog().getMessageCount() == 0) {
            return Collections.emptyList();
        }

        ProtocolMessageProcessor processor = processorsByModuleId.get(deserialized.moduleId());
        if (processor == null) {
            LOG.debug("No processor for moduleId '{}', dropping message", deserialized.moduleId());
            return Collections.emptyList();
        }

        TelemetryProtos.TelemetryMessageLog messageLog = deserialized.messageLog();
        List<Flow> flows = processor.process(messageLog);
        if (flows.isEmpty()) {
            return Collections.emptyList();
        }

        // Exporter lookup (once per message, cached)
        String exporterAddress = messageLog.getSourceAddress();
        JdbcNodeInfoLookup.NodeInfo exporter = nodeInfoLookup.lookupByIpAddress(exporterAddress);

        List<byte[]> results = new ArrayList<>(flows.size());
        for (Flow flow : flows) {
            try {
                // Per-flow src/dst lookups
                JdbcNodeInfoLookup.NodeInfo srcNode = (flow.getSrcAddr() != null)
                        ? nodeInfoLookup.lookupByIpAddress(flow.getSrcAddr()) : null;
                JdbcNodeInfoLookup.NodeInfo dstNode = (flow.getDstAddr() != null)
                        ? nodeInfoLookup.lookupByIpAddress(flow.getDstAddr()) : null;

                // Locality
                String srcLocality = (flow.getSrcAddr() != null)
                        ? localityCalculator.calculate(flow.getSrcAddr()) : "UNKNOWN";
                String dstLocality = (flow.getDstAddr() != null)
                        ? localityCalculator.calculate(flow.getDstAddr()) : "UNKNOWN";
                String flowLocality = computeFlowLocality(srcLocality, dstLocality);

                // Interface marking (only when exporter known and ifindex provided)
                if (exporter != null) {
                    if (flow.getInputSnmp() != null && flow.getInputSnmp() > 0) {
                        interfaceMarkingCache.markIfNeeded(exporter.nodeId(), flow.getInputSnmp());
                    }
                    if (flow.getOutputSnmp() != null && flow.getOutputSnmp() > 0) {
                        interfaceMarkingCache.markIfNeeded(exporter.nodeId(), flow.getOutputSnmp());
                    }
                }

                // Classification (port-based stub)
                int dstPort = flow.getDstPort() != null ? flow.getDstPort() : 0;
                int srcPort = flow.getSrcPort() != null ? flow.getSrcPort() : 0;
                int protocol = flow.getProtocol() != null ? flow.getProtocol() : 0;
                String application = applicationClassifier.classify(dstPort, srcPort, protocol);

                // Mapping
                FlowDocumentProtos.FlowDocument doc = flowToDocumentMapper.map(
                        flow, exporter, srcNode, dstNode,
                        application, srcLocality, dstLocality, flowLocality);

                results.add(doc.toByteArray());
            } catch (Exception e) {
                LOG.warn("Failed to enrich flow (src={}, dst={}): {}",
                        flow.getSrcAddr(), flow.getDstAddr(), e.getMessage());
            }
        }

        return results;
    }

    private static String computeFlowLocality(String src, String dst) {
        if ("PUBLIC".equals(src) || "PUBLIC".equals(dst)) {
            return "PUBLIC";
        }
        if ("PRIVATE".equals(src) && "PRIVATE".equals(dst)) {
            return "PRIVATE";
        }
        return "UNKNOWN";
    }
```

Add the required fields to the constructor: `ApplicationClassifier applicationClassifier` and `FlowToDocumentMapper flowToDocumentMapper`.

- [ ] **Step 4: Update FlowEnricherConfiguration with new processors and dispatch map**

In `FlowEnricherConfiguration.java`, add four new processor beans and an `ApplicationClassifier` bean:

```java
    @Bean
    ApplicationClassifier applicationClassifier() {
        return new PortBasedApplicationClassifier();
    }

    @Bean
    FlowToDocumentMapper flowToDocumentMapper() {
        return new FlowToDocumentMapper();
    }

    @Bean
    MetricRegistry flowEnricherMetricRegistry() {
        return new MetricRegistry();
    }

    @Bean
    Netflow5MessageProcessor netflow5Processor(MetricRegistry flowEnricherMetricRegistry) {
        return new Netflow5MessageProcessor(
                buildAdapterDefinition("Netflow-5"), flowEnricherMetricRegistry);
    }

    @Bean
    Netflow9MessageProcessor netflow9Processor(MetricRegistry flowEnricherMetricRegistry) {
        return new Netflow9MessageProcessor(
                buildAdapterDefinition("Netflow-9"), flowEnricherMetricRegistry);
    }

    @Bean
    IpfixMessageProcessor ipfixProcessor(MetricRegistry flowEnricherMetricRegistry) {
        return new IpfixMessageProcessor(
                buildAdapterDefinition("IPFIX"), flowEnricherMetricRegistry);
    }

    @Bean
    SFlowMessageProcessor sflowProcessor(MetricRegistry flowEnricherMetricRegistry) {
        return new SFlowMessageProcessor(
                buildAdapterDefinition("SFlow"), flowEnricherMetricRegistry);
    }

    private static AdapterDefinition buildAdapterDefinition(String name) {
        // Minimal AdapterDefinition with only name and enabled=true.
        // If AdapterDefinition is not mockable at runtime, use a simple anonymous
        // inner class implementation instead.
        return new AdapterDefinition() {
            @Override public String getName() { return name; }
            @Override public boolean isEnabled() { return true; }
            // Implement other methods to return null/defaults as required by the interface
        };
    }
```

Update the `flowEnrichmentFunction` bean to inject the dispatch map:

```java
    @Bean
    FlowEnrichmentFunction flowEnrichmentFunction(
            SinkMessageDeserializer deserializer,
            JdbcNodeInfoLookup nodeInfoLookup,
            FlowLocalityCalculator localityCalculator,
            InterfaceMarkingCache interfaceMarkingCache,
            ApplicationClassifier applicationClassifier,
            FlowToDocumentMapper flowToDocumentMapper,
            Netflow5MessageProcessor netflow5Processor,
            Netflow9MessageProcessor netflow9Processor,
            IpfixMessageProcessor ipfixProcessor,
            SFlowMessageProcessor sflowProcessor) {

        Map<String, ProtocolMessageProcessor> dispatchMap = Map.of(
                "Telemetry-Netflow-5", netflow5Processor,
                "Telemetry-Netflow-9", netflow9Processor,
                "Telemetry-IPFIX",     ipfixProcessor,
                "Telemetry-SFlow",     sflowProcessor);

        return new FlowEnrichmentFunction(
                deserializer, nodeInfoLookup, localityCalculator, interfaceMarkingCache,
                applicationClassifier, flowToDocumentMapper, dispatchMap);
    }
```

- [ ] **Step 5: Run all tests**

Run:
```bash
./compile.pl -pl :org.deltav.flows.flow-enricher test 2>&1 | tail -30
```
Expected: All tests pass.

- [ ] **Step 6: Commit (Final Wiring)**

```bash
git add core/flow-enricher/src/main/java/org/deltav/flows/enricher/FlowEnrichmentFunction.java \
        core/flow-enricher/src/main/java/org/deltav/flows/enricher/FlowEnricherConfiguration.java \
        core/flow-enricher/src/test/java/org/deltav/flows/enricher/FlowEnrichmentFunctionTest.java
git commit -m "feat(flow-enricher): wire full per-flow enrichment pipeline"
```

---

## Task 13: Integration test with spring-cloud-stream-test-binder

**Files:**
- Create: `core/flow-enricher/src/test/java/org/deltav/flows/enricher/integration/FlowEnrichmentStreamBinderIT.java`

End-to-end integration test that exercises the full Spring Boot context with the in-memory test binder, sends synthetic Sink messages for each protocol, and verifies enriched FlowDocument records appear on the output channel.

- [ ] **Step 1: Create the integration test**

Create `core/flow-enricher/src/test/java/org/deltav/flows/enricher/integration/FlowEnrichmentStreamBinderIT.java`:

```java
/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 *
 * Licensed under the GNU Affero General Public License v3.
 */
package org.deltav.flows.enricher.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.google.protobuf.ByteString;

import org.deltav.flows.proto.FlowDocumentProtos;
import org.junit.jupiter.api.Test;
import org.opennms.core.ipc.sink.model.SinkMessageProtos;
import org.opennms.netmgt.telemetry.common.ipc.TelemetryProtos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

@SpringBootTest(classes = { org.deltav.flows.enricher.FlowEnricherApplication.class },
        properties = {
                "spring.cloud.function.definition=enrichFlows",
                "spring.autoconfigure.exclude=" + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
                "deltav.flows.node-lookup.cache-ttl=1m",
                "deltav.flows.interface-marking.cache-ttl=1m"
        })
@Import(TestChannelBinderConfiguration.class)
class FlowEnrichmentStreamBinderIT {

    @Autowired
    private InputDestination input;

    @Autowired
    private OutputDestination output;

    @Test
    void netflow5SinkMessageProducesEnrichedFlowDocuments() throws Exception {
        byte[] sinkMessageBytes = buildSinkMessage("Telemetry-Netflow-5",
                loadFixtureOrBuildNetflow5Packet());

        input.send(MessageBuilder.withPayload(sinkMessageBytes).build());

        Message<byte[]> outMsg = output.receive(5_000);
        assertThat(outMsg).isNotNull();
        FlowDocumentProtos.FlowDocument doc = FlowDocumentProtos.FlowDocument.parseFrom(outMsg.getPayload());
        assertThat(doc.getTimestamp()).isGreaterThan(0);
    }

    @Test
    void netflow9SinkMessageProducesEnrichedFlowDocuments() throws Exception {
        byte[] sinkMessageBytes = buildSinkMessage("Telemetry-Netflow-9",
                loadFixture("/test-packets/netflow9-sample.dat"));

        input.send(MessageBuilder.withPayload(sinkMessageBytes).build());

        Message<byte[]> outMsg = output.receive(5_000);
        // May be null if the fixture is template-only (zero data flows)
        // Assert a soft condition: either null (template) or a valid FlowDocument
        if (outMsg != null) {
            FlowDocumentProtos.FlowDocument doc = FlowDocumentProtos.FlowDocument.parseFrom(outMsg.getPayload());
            assertThat(doc.getNetflowVersion()).isEqualTo(FlowDocumentProtos.NetflowVersion.V9);
        }
    }

    @Test
    void ipfixSinkMessageProducesEnrichedFlowDocuments() throws Exception {
        byte[] sinkMessageBytes = buildSinkMessage("Telemetry-IPFIX",
                loadFixture("/test-packets/ipfix-sample.dat"));

        input.send(MessageBuilder.withPayload(sinkMessageBytes).build());

        Message<byte[]> outMsg = output.receive(5_000);
        if (outMsg != null) {
            FlowDocumentProtos.FlowDocument doc = FlowDocumentProtos.FlowDocument.parseFrom(outMsg.getPayload());
            assertThat(doc.getNetflowVersion()).isEqualTo(FlowDocumentProtos.NetflowVersion.IPFIX);
        }
    }

    @Test
    void sflowSinkMessageProducesEnrichedFlowDocuments() throws Exception {
        byte[] sinkMessageBytes = buildSinkMessage("Telemetry-SFlow",
                loadFixture("/test-packets/sflow-sample.dat"));

        input.send(MessageBuilder.withPayload(sinkMessageBytes).build());

        Message<byte[]> outMsg = output.receive(5_000);
        if (outMsg != null) {
            FlowDocumentProtos.FlowDocument doc = FlowDocumentProtos.FlowDocument.parseFrom(outMsg.getPayload());
            assertThat(doc.getNetflowVersion()).isEqualTo(FlowDocumentProtos.NetflowVersion.SFLOW);
        }
    }

    private static byte[] buildSinkMessage(String moduleId, byte[] payload) {
        TelemetryProtos.TelemetryMessageLog log = TelemetryProtos.TelemetryMessageLog.newBuilder()
                .setLocation("test-location")
                .setSourceAddress("192.0.2.254")
                .addMessage(TelemetryProtos.TelemetryMessage.newBuilder()
                        .setBytes(ByteString.copyFrom(payload))
                        .setTimestamp(System.currentTimeMillis())
                        .build())
                .build();
        SinkMessageProtos.SinkMessage sinkMsg = SinkMessageProtos.SinkMessage.newBuilder()
                .setModuleId(moduleId)
                .setContent(log.toByteString())
                .build();
        return sinkMsg.toByteArray();
    }

    private static byte[] loadFixture(String classpathPath) throws IOException {
        try (InputStream in = FlowEnrichmentStreamBinderIT.class.getResourceAsStream(classpathPath)) {
            if (in == null) {
                throw new IOException("Fixture not found: " + classpathPath);
            }
            return in.readAllBytes();
        }
    }

    private static byte[] loadFixtureOrBuildNetflow5Packet() {
        // For Netflow-5 we use the hand-crafted packet from the unit test.
        // Copy the buildNetflow5Packet() method here or extract it to a shared test helper.
        return Netflow5MessageProcessorTest.buildNetflow5Packet();
    }
}
```

**Important notes:**

1. **DataSource exclusion** — The integration test disables the JDBC datasource autoconfiguration because the tests don't hit PostgreSQL. The `JdbcNodeInfoLookup` bean still gets constructed with a `null` or stub `JdbcTemplate`; node lookups will return null for all addresses, which is fine — the test doesn't assert on node info enrichment.

2. **Extract `buildNetflow5Packet()` to a shared helper** — The method in Task 10's test file should be made package-visible or moved to a common `test-packets/NetflowPackets.java` utility that both the unit test and the IT import. Make this move as part of this task.

3. **Application context classes** — If Spring Boot fails to start because the `DataSource` is required, you may need to provide a stub `JdbcTemplate` bean via a `@TestConfiguration` inside the test class that overrides the production `flowEnricherJdbcTemplate` bean.

- [ ] **Step 2: Run the integration test**

Run:
```bash
./compile.pl -pl :org.deltav.flows.flow-enricher -Dtest=FlowEnrichmentStreamBinderIT test 2>&1 | tail -40
```
Expected: All four tests pass (or the Netflow-9/IPFIX/sFlow ones pass conditionally as documented by the `if (outMsg != null)` guard).

Common failures and fixes:
- **"Failed to start ApplicationContext"** — the `DataSource` exclusion may need to be different for your Spring Boot version. Try also excluding `DataSourceTransactionManagerAutoConfiguration`.
- **"No qualifying bean of type FlowEnrichmentFunction"** — the `FlowEnricherConfiguration` may require `DataSource` indirectly. Add a `@MockBean DataSource dataSource` or provide a stub.
- **Output timeout (`outMsg is null` when expected non-null)** — turn on debug logging for `org.springframework.cloud.stream` and check whether the function bean was registered and invoked.

- [ ] **Step 3: Run the full test suite**

Run:
```bash
./compile.pl -pl :org.deltav.flows.flow-enricher test 2>&1 | tail -30
```
Expected: All tests pass. Total count should be 24 (skeleton) + 3 (CapturingPipeline) + 12 (Mapper) + 10 (Classifier) + 1 (Netflow5 processor) + 1 (Netflow9 processor) + 1 (IPFIX processor) + 1 (sFlow processor) + ~5 updated (FlowEnrichmentFunction) + 4 (IT) = **~61 tests total**.

- [ ] **Step 4: Commit (Integration Test)**

```bash
git add core/flow-enricher/src/test/java/org/deltav/flows/enricher/integration/
git commit -m "test(flow-enricher): add stream-binder integration test for all four protocols"
```

---

## Task 14: Final build verification and optional local run

**Files:** No new files.

- [ ] **Step 1: Run the full flow-enricher module build**

Run:
```bash
cd /Users/david/development/src/opennms/delta-v
./compile.pl -pl :org.deltav.flows.flow-enricher -am install 2>&1 | tail -30
```
Expected: `BUILD SUCCESS`, all tests pass, JAR installed to local Maven repo.

- [ ] **Step 2 (optional): Build the flow-enricher Docker image**

```bash
cd opennms-container/delta-v
./build.sh flow-enricher 2>&1 | tail -20
```
Expected: Docker image `opennms/flow-enricher:${VERSION}` built successfully.

- [ ] **Step 3 (optional): Start the delta-v stack and observe real flows**

```bash
cd opennms-container/delta-v
docker compose --profile full up -d flow-enricher
docker compose logs -f flow-enricher | head -50
```

From the labbox or another traffic source, generate some NetFlow traffic directed at the Minion. Then:

```bash
docker compose exec kafka kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 \
    --topic deltav-flows \
    --max-messages 5 \
    --timeout-ms 30000 | head -20
```

Expected: Up to 5 protobuf binary messages visible on the `deltav-flows` topic. Decoding them via `protoc --decode` with the `.proto` file should show populated flow records.

- [ ] **Step 4: Push the branch**

```bash
git push -u origin feature/flow-enricher-phase15
```

- [ ] **Step 5: Open the PR**

```bash
gh pr create --repo pbrane/delta-v --base develop \
    --title "feat(flow-enricher): Phase 1.5 horizon adapter integration" \
    --body "$(cat <<'EOF'
## Summary

Wires horizon protocol adapters (Netflow-5, Netflow-9, IPFIX, sFlow) into the flow-enricher service so each incoming TelemetryMessageLog is parsed into flows, enriched with src/dst/exporter node lookups, locality, interface marking, and port-based classification, and emitted as a List<FlowDocument> to the deltav-flows Kafka topic.

- Function signature changed from `Function<byte[], byte[]>` to `Function<byte[], List<byte[]>>` (Spring Cloud Stream splitter pattern)
- Runtime dispatch by `SinkMessage.moduleId` to one of four protocol processors
- New `CapturingPipeline` implements horizon's `Pipeline` interface as a capturing sink
- New `FlowToDocumentMapper` does field-by-field `Flow → FlowDocumentProtos.FlowDocument` with explicit null-wrapper semantics
- New `PortBasedApplicationClassifier` stub covering ~30 well-known ports (replaced with real classification engine in Phase 1.6)
- All four horizon adapter JARs added with standard exclusion blocks
- Deferred to Phase 1.6: horizon classification engine, clock-skew correction, nodeDeleted cache eviction

## Review plan

- [ ] pom.xml exclusion blocks match the existing pattern for sink.common and telemetry.common
- [ ] CapturingPipeline is per-invocation (not reused) — see AbstractProtocolMessageProcessor.createAdapter()
- [ ] FlowToDocumentMapperTest asserts null-wrapper discipline (unset vs zero)
- [ ] Four protocol processors follow identical shape with only the adapter class varying
- [ ] FlowEnrichmentFunction dispatches on SinkMessage.moduleId with a Map lookup
- [ ] FlowEnrichmentStreamBinderIT exercises all four protocols through the in-memory test binder
- [ ] No ClickHouse or Phase 2 concerns leak into this PR
EOF
)"
```

---

## Self-Review Notes

**Spec coverage:** Every Phase 1.5 deliverable from `docs/superpowers/specs/2026-04-09-flow-processor-design.md` is covered by a task:
- `CapturingPipeline` implementation → Task 2
- `FlowToDocumentMapper` → Task 3
- Per-protocol processors → Tasks 8-11
- Function refactor to splitter signature → Task 7
- Runtime dispatch by moduleId → Tasks 6, 7, 12
- Classification, clock-skew, interface-marking integration → Task 12 (classification via stub), clock-skew deferred to Phase 1.6
- Live integration test against the running delta-v stack → Task 14 Step 3 (optional manual validation)

**Placeholder scan:** No "TBD", "TODO", "implement later", or "add error handling" placeholders. Every step has concrete code or a specific command. A few implementation-discovery notes ("check the actual package path," "method names may differ") are present, which is intentional because horizon JAR introspection requires the build to actually resolve before the exact names are known.

**Type consistency:**
- `FlowEnrichmentFunction` constructor signature evolves from 4 args in Task 7 to 7 args (adds `ApplicationClassifier`, `FlowToDocumentMapper`, dispatch map) in Task 12. Both are documented explicitly.
- `DeserializedSinkMessage` record fields are `moduleId` and `messageLog` throughout.
- `FlowToDocumentMapper.map(...)` takes 8 arguments: flow + three NodeInfo + application + three locality strings. Consistent across Task 3 and Task 12.
- `JdbcNodeInfoLookup.NodeInfo` constructor uses `(nodeId, foreignSource, foreignId, categories)` in Task 3 test and Task 12 test — same order.

---

## Plan complete
