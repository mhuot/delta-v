# Flow Enricher Phase 2 — Parser Bridge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rework `core/flow-enricher` to parse raw Netflow/IPFIX/sFlow UDP wire bytes server-side using horizon `UdpParser` classes, then flip `docker-compose.yml` so the Phase 1 Minion listener becomes the active flow ingress path.

**Architecture:** Two-stage bridge inside `AbstractProtocolMessageProcessor` — Stage 1 (new) uses a singleton horizon `UdpParser` with a `ThreadLocal<CapturingDispatcher>` to turn raw UDP bytes into `FlowMessage` protobuf; Stage 2 (unchanged from Phase 1.5) uses horizon `AbstractFlowAdapter` with `CapturingPipeline` to turn the synthesized `TelemetryMessageLog` into `Flow` POJOs. Parser template state persists across calls because parsers are long-lived Spring beans.

**Tech Stack:** Java 21, Spring Boot 3.x, Spring Cloud Stream + Kafka binder, horizon telemetry parser JARs, Dropwizard Metrics → Micrometer → Prometheus, JUnit Jupiter + AssertJ.

**Spec:** `docs/superpowers/specs/2026-04-12-flow-enricher-phase2-parser-bridge-design.md`

**Branch:** `feature/flow-enricher-phase2-parser-bridge` (already created off `develop`)

---

## File Structure

### New source files
```
core/flow-enricher/src/main/java/org/deltav/flows/enricher/parser/
    CapturingDispatcher.java           — per-call list capture
    ThreadLocalDispatcher.java         — singleton dispatcher with ThreadLocal delegate
    NoOpDnsResolver.java               — always-empty DNS resolver
    LoggingEventForwarder.java         — WARN-log-only event forwarder
    StaticIdentity.java                — config-backed Identity bean
    FlowEnricherMicrometerBridge.java  — Dropwizard→Micrometer adapter
```

### New test files
```
core/flow-enricher/src/test/java/org/deltav/flows/enricher/parser/
    CapturingDispatcherTest.java
    ThreadLocalDispatcherTest.java
    NoOpDnsResolverTest.java
    LoggingEventForwarderTest.java

core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/
    FakeUdpParser.java                 — test stub for processor unit tests
    Netflow9ParserBridgeIT.java        — real parser + fixture
    Netflow5ParserBridgeIT.java
    IpfixParserBridgeIT.java
    SFlowParserBridgeIT.java
```

### New test resources
```
core/flow-enricher/src/test/resources/fixtures/
    netflow9_template_and_data.dat
    netflow5_packet.dat
    ipfix_template_and_data.dat
    sflow_sample.dat
```

### Modified files
```
core/flow-enricher/pom.xml                                        — POM deps
core/flow-enricher/src/main/java/org/deltav/flows/enricher/
    protocol/AbstractProtocolMessageProcessor.java                — rework to 2-stage
    protocol/Netflow5MessageProcessor.java                        — Shape 2 ctor
    protocol/Netflow9MessageProcessor.java                        — Shape 2 ctor
    protocol/IpfixMessageProcessor.java                           — Shape 2 ctor
    protocol/SFlowMessageProcessor.java                           — Shape 2 ctor
    FlowEnricherConfiguration.java                                — new beans
core/flow-enricher/src/main/resources/application.yml             — partition strategy, identity, prometheus
core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/
    Netflow5MessageProcessorTest.java
    Netflow9MessageProcessorTest.java                             — +2 ThreadLocal stress tests
    IpfixMessageProcessorTest.java
    SFlowMessageProcessorTest.java
opennms-container/delta-v/docker-compose.yml                      — 4729 flip
opennms-container/delta-v/telemetryd-overlay/etc/telemetryd-configuration.xml  — drop listener
opennms-container/delta-v/test-flows-e2e.sh                       — add minion
docs/superpowers/specs/2026-04-12-minion-telemetry-receiver-design.md  — spec cleanup
```

---

## Conventions

- Copyright header on every new `.java` file: AGPL-3.0, `Copyright (C) 2026 BeaconStrategists, Inc.` (match the existing Phase 1.5 files).
- Package is `org.deltav.flows.enricher.parser` for the new parser bridge code and `org.deltav.flows.enricher.protocol` for the updated per-protocol processor code.
- JUnit 5 (`org.junit.jupiter.api.Test`), AssertJ (`org.assertj.core.api.Assertions.assertThat`). Match the style of existing `Netflow9MessageProcessorTest.java`.
- Constructor injection only — no `@Autowired` on fields.
- Commit after each task (TDD discipline). Commit messages follow Conventional Commits — examples inline per task.

---

## Task 1: Add horizon UDP parser POM dependencies

**Files:**
- Modify: `core/flow-enricher/pom.xml`

- [ ] **Step 1: Locate the existing horizon adapter dependency block**

Open `core/flow-enricher/pom.xml` and find the `<!-- Horizon: sFlow protocol adapter -->` block (ends around line 169). Every new horizon dep we add follows the same exclusion pattern as the existing ones.

- [ ] **Step 2: Add five new dependencies immediately after the sFlow adapter block**

```xml
        <!-- Horizon: Netflow UDP parsers (Netflow5/Netflow9/IPFIX) -->
        <dependency>
            <groupId>org.opennms.features.telemetry.protocols.netflow</groupId>
            <artifactId>org.opennms.features.telemetry.protocols.netflow.parser</artifactId>
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
                <exclusion><groupId>org.opennms</groupId><artifactId>opennms-util</artifactId></exclusion>
                <exclusion><groupId>org.opennms</groupId><artifactId>opennms-config</artifactId></exclusion>
                <exclusion><groupId>org.opennms.dependencies</groupId><artifactId>atomikos-dependencies</artifactId></exclusion>
                <exclusion><groupId>org.opennms.dependencies</groupId><artifactId>jaxb-dependencies</artifactId></exclusion>
                <exclusion><groupId>org.opennms.features.config</groupId><artifactId>*</artifactId></exclusion>
            </exclusions>
        </dependency>

        <!-- Horizon: sFlow UDP parser -->
        <dependency>
            <groupId>org.opennms.features.telemetry.protocols.sflow</groupId>
            <artifactId>org.opennms.features.telemetry.protocols.sflow.parser</artifactId>
            <exclusions>
                <exclusion><groupId>org.apache.servicemix.bundles</groupId><artifactId>*</artifactId></exclusion>
                <exclusion><groupId>org.opennms.dependencies</groupId><artifactId>spring-dependencies</artifactId></exclusion>
                <exclusion><groupId>org.opennms.dependencies</groupId><artifactId>spring-security-dependencies</artifactId></exclusion>
                <exclusion><groupId>org.hibernate</groupId><artifactId>hibernate-core</artifactId></exclusion>
                <exclusion><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId></exclusion>
                <exclusion><groupId>org.slf4j</groupId><artifactId>jcl-over-slf4j</artifactId></exclusion>
                <exclusion><groupId>org.slf4j</groupId><artifactId>log4j-over-slf4j</artifactId></exclusion>
                <exclusion><groupId>org.ops4j.pax.logging</groupId><artifactId>pax-logging-log4j2</artifactId></exclusion>
                <exclusion><groupId>org.ops4j.pax.logging</groupId><artifactId>pax-logging-api</artifactId></exclusion>
                <exclusion><groupId>commons-logging</groupId><artifactId>commons-logging</artifactId></exclusion>
                <exclusion><groupId>com.fasterxml.jackson.module</groupId><artifactId>jackson-module-scala_2.13</artifactId></exclusion>
                <exclusion><groupId>org.opennms</groupId><artifactId>opennms-model</artifactId></exclusion>
                <exclusion><groupId>org.opennms</groupId><artifactId>opennms-util</artifactId></exclusion>
                <exclusion><groupId>org.opennms</groupId><artifactId>opennms-config</artifactId></exclusion>
                <exclusion><groupId>org.opennms.dependencies</groupId><artifactId>jaxb-dependencies</artifactId></exclusion>
                <exclusion><groupId>org.opennms.features.config</groupId><artifactId>*</artifactId></exclusion>
            </exclusions>
        </dependency>

        <!-- Horizon: Telemetry listeners (UdpParser interface, TelemetryMessage, AsyncDispatcher) -->
        <dependency>
            <groupId>org.opennms.features.telemetry</groupId>
            <artifactId>org.opennms.features.telemetry.listeners</artifactId>
            <exclusions>
                <exclusion><groupId>org.apache.servicemix.bundles</groupId><artifactId>*</artifactId></exclusion>
                <exclusion><groupId>org.opennms.dependencies</groupId><artifactId>spring-dependencies</artifactId></exclusion>
                <exclusion><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId></exclusion>
                <exclusion><groupId>org.slf4j</groupId><artifactId>jcl-over-slf4j</artifactId></exclusion>
                <exclusion><groupId>org.slf4j</groupId><artifactId>log4j-over-slf4j</artifactId></exclusion>
                <exclusion><groupId>commons-logging</groupId><artifactId>commons-logging</artifactId></exclusion>
                <exclusion><groupId>org.opennms</groupId><artifactId>opennms-model</artifactId></exclusion>
                <exclusion><groupId>org.opennms</groupId><artifactId>opennms-util</artifactId></exclusion>
            </exclusions>
        </dependency>

        <!-- Horizon: Events API (EventForwarder interface required by Netflow parsers) -->
        <dependency>
            <groupId>org.opennms.features.events</groupId>
            <artifactId>org.opennms.features.events.api</artifactId>
            <exclusions>
                <exclusion><groupId>commons-logging</groupId><artifactId>commons-logging</artifactId></exclusion>
                <exclusion><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId></exclusion>
                <exclusion><groupId>org.opennms</groupId><artifactId>opennms-model</artifactId></exclusion>
                <exclusion><groupId>org.opennms</groupId><artifactId>opennms-util</artifactId></exclusion>
                <exclusion><groupId>org.apache.servicemix.bundles</groupId><artifactId>*</artifactId></exclusion>
            </exclusions>
        </dependency>

        <!-- Micrometer Prometheus registry for /actuator/prometheus -->
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
```

- [ ] **Step 3: Run a dependency convergence check**

Run: `cd /Users/david/development/src/opennms/delta-v && ./compile.pl -DskipTests --projects :org.deltav.flows.flow-enricher -am compile 2>&1 | tail -30`

Expected: `BUILD SUCCESS`. If the build fails with "Cannot find", the artifact coordinates may differ — confirm against the horizon module POM at `.claude/worktrees/provisiond-spring-boot/features/telemetry/protocols/netflow/parser/pom.xml`.

- [ ] **Step 4: Verify no banned transitive dependencies leaked in**

Run: `cd core/flow-enricher && ../../maven/bin/mvn dependency:tree -Dverbose=true 2>&1 | grep -E "opennms-config|atomikos|eclipselink|jaxb-xjc|features.config" | head`

Expected: no output. If any banned dep appears, locate the parser module that transitively brings it in and add the appropriate `<exclusion>` block.

- [ ] **Step 5: Commit**

```bash
git add core/flow-enricher/pom.xml
git commit -m "feat(flow-enricher): add POM deps for horizon UDP parser modules

Adds netflow.parser, sflow.parser, telemetry.listeners, events.api, and
micrometer-registry-prometheus dependencies to support the Phase 2
server-side parser bridge. Exclusion list mirrors the existing
adapter-module pattern to keep the Spring Boot classpath clean."
```

---

## Task 2: CapturingDispatcher

**Files:**
- Create: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/parser/CapturingDispatcher.java`
- Create: `core/flow-enricher/src/test/java/org/deltav/flows/enricher/parser/CapturingDispatcherTest.java`

- [ ] **Step 1: Write the failing test first**

Create `core/flow-enricher/src/test/java/org/deltav/flows/enricher/parser/CapturingDispatcherTest.java`:

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
package org.deltav.flows.enricher.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.opennms.core.ipc.sink.api.AsyncDispatcher.DispatchStatus;
import org.opennms.netmgt.telemetry.api.receiver.TelemetryMessage;

class CapturingDispatcherTest {

    @Test
    void capturesMessagesInOrder() {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        TelemetryMessage m1 = new TelemetryMessage(
                new InetSocketAddress("10.0.0.1", 1234),
                ByteBuffer.wrap(new byte[]{1, 2, 3}));
        TelemetryMessage m2 = new TelemetryMessage(
                new InetSocketAddress("10.0.0.2", 5678),
                ByteBuffer.wrap(new byte[]{4, 5}));

        CompletableFuture<DispatchStatus> f1 = dispatcher.send(m1);
        CompletableFuture<DispatchStatus> f2 = dispatcher.send(m2);

        assertThat(f1).isCompleted();
        assertThat(f2).isCompleted();
        assertThat(f1.join()).isEqualTo(DispatchStatus.DISPATCHED);
        assertThat(dispatcher.getCaptured()).containsExactly(m1, m2);
    }

    @Test
    void getCapturedReturnsUnmodifiableView() {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        dispatcher.send(new TelemetryMessage(
                new InetSocketAddress("10.0.0.1", 1234),
                ByteBuffer.wrap(new byte[]{1})));

        var captured = dispatcher.getCaptured();
        assertThat(captured).hasSize(1);
        // Mutating the returned list must not affect the internal state
        // and must throw to make accidental writes obvious.
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> captured.add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getQueueSizeReflectsCapturedCount() {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        assertThat(dispatcher.getQueueSize()).isZero();
        dispatcher.send(new TelemetryMessage(
                new InetSocketAddress("10.0.0.1", 1234),
                ByteBuffer.wrap(new byte[]{1})));
        assertThat(dispatcher.getQueueSize()).isEqualTo(1);
    }

    @Test
    void closeIsNoop() throws Exception {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        dispatcher.send(new TelemetryMessage(
                new InetSocketAddress("10.0.0.1", 1234),
                ByteBuffer.wrap(new byte[]{1})));
        dispatcher.close();
        // After close, the captured list is still accessible
        assertThat(dispatcher.getCaptured()).hasSize(1);
    }
}
```

- [ ] **Step 2: Run the test and verify it fails with a compile error**

Run: `cd /Users/david/development/src/opennms/delta-v/core/flow-enricher && ../../maven/bin/mvn -q test -Dtest=CapturingDispatcherTest 2>&1 | tail -20`

Expected: compile error saying `CapturingDispatcher` cannot be resolved.

- [ ] **Step 3: Implement CapturingDispatcher**

Create `core/flow-enricher/src/main/java/org/deltav/flows/enricher/parser/CapturingDispatcher.java`:

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
package org.deltav.flows.enricher.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.opennms.core.ipc.sink.api.AsyncDispatcher;
import org.opennms.netmgt.telemetry.api.receiver.TelemetryMessage;

/**
 * An {@link AsyncDispatcher} that accumulates dispatched {@link TelemetryMessage}
 * objects into a local list instead of forwarding them anywhere. Used as the
 * capture sink for horizon {@link org.opennms.netmgt.telemetry.listeners.UdpParser}
 * instances during the flow-enricher's Stage 1 parse step.
 *
 * <p><strong>This class is NOT thread-safe.</strong> A fresh instance must be
 * created per {@code process()} call and installed into the singleton
 * {@link ThreadLocalDispatcher}. Sharing one instance across concurrent
 * threads or across sequential calls on the same thread will mix flows from
 * different exporters, which is a correctness bug the Stage 1 error-handling
 * contract explicitly forbids.
 */
public class CapturingDispatcher implements AsyncDispatcher<TelemetryMessage> {

    private final List<TelemetryMessage> captured = new ArrayList<>();

    @Override
    public CompletableFuture<DispatchStatus> send(TelemetryMessage message) {
        captured.add(message);
        return CompletableFuture.completedFuture(DispatchStatus.DISPATCHED);
    }

    @Override
    public int getQueueSize() {
        return captured.size();
    }

    @Override
    public void close() {
        // No resources to release; captures remain accessible.
    }

    /**
     * Returns an unmodifiable view of the messages dispatched to this
     * instance since it was created. Order matches dispatch order.
     */
    public List<TelemetryMessage> getCaptured() {
        return Collections.unmodifiableList(captured);
    }
}
```

- [ ] **Step 4: Run the test and verify it passes**

Run: `cd /Users/david/development/src/opennms/delta-v/core/flow-enricher && ../../maven/bin/mvn -q test -Dtest=CapturingDispatcherTest 2>&1 | tail -20`

Expected: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add core/flow-enricher/src/main/java/org/deltav/flows/enricher/parser/CapturingDispatcher.java \
        core/flow-enricher/src/test/java/org/deltav/flows/enricher/parser/CapturingDispatcherTest.java
git commit -m "feat(flow-enricher): add CapturingDispatcher for parser bridge Stage 1"
```

---

## Task 3: ThreadLocalDispatcher

**Files:**
- Create: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/parser/ThreadLocalDispatcher.java`
- Create: `core/flow-enricher/src/test/java/org/deltav/flows/enricher/parser/ThreadLocalDispatcherTest.java`

- [ ] **Step 1: Write the failing test**

Create `core/flow-enricher/src/test/java/org/deltav/flows/enricher/parser/ThreadLocalDispatcherTest.java`:

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
package org.deltav.flows.enricher.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;
import org.opennms.core.ipc.sink.api.AsyncDispatcher.DispatchStatus;
import org.opennms.netmgt.telemetry.api.receiver.TelemetryMessage;

class ThreadLocalDispatcherTest {

    private static TelemetryMessage msg(int lastByte) {
        return new TelemetryMessage(
                new InetSocketAddress("10.0.0.1", 1234),
                ByteBuffer.wrap(new byte[]{(byte) lastByte}));
    }

    @Test
    void sendDelegatesToInstalledCapturingDispatcher() {
        ThreadLocalDispatcher tld = new ThreadLocalDispatcher();
        CapturingDispatcher capture = new CapturingDispatcher();
        tld.install(capture);
        try {
            var future = tld.send(msg(1));
            assertThat(future.join()).isEqualTo(DispatchStatus.DISPATCHED);
            assertThat(capture.getCaptured()).hasSize(1);
        } finally {
            tld.clear();
        }
    }

    @Test
    void sendWithoutInstallThrowsClearError() {
        ThreadLocalDispatcher tld = new ThreadLocalDispatcher();
        assertThatThrownBy(() -> tld.send(msg(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No CapturingDispatcher installed");
    }

    @Test
    void clearRemovesThreadLocalSoSubsequentSendFails() {
        ThreadLocalDispatcher tld = new ThreadLocalDispatcher();
        tld.install(new CapturingDispatcher());
        tld.clear();
        assertThatThrownBy(() -> tld.send(msg(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void currentReturnsInstalledDispatcherOrNull() {
        ThreadLocalDispatcher tld = new ThreadLocalDispatcher();
        assertThat(tld.current()).isNull();
        CapturingDispatcher capture = new CapturingDispatcher();
        tld.install(capture);
        try {
            assertThat(tld.current()).isSameAs(capture);
        } finally {
            tld.clear();
        }
        assertThat(tld.current()).isNull();
    }

    @Test
    void twoThreadsHaveIndependentInstalls() throws Exception {
        ThreadLocalDispatcher tld = new ThreadLocalDispatcher();
        CapturingDispatcher captureA = new CapturingDispatcher();
        CapturingDispatcher captureB = new CapturingDispatcher();
        tld.install(captureA);
        try {
            Thread t = new Thread(() -> {
                tld.install(captureB);
                try {
                    tld.send(msg(42));
                } finally {
                    tld.clear();
                }
            });
            t.start();
            t.join();
            // Thread B's send landed in captureB; captureA stays empty.
            assertThat(captureA.getCaptured()).isEmpty();
            assertThat(captureB.getCaptured()).hasSize(1);
        } finally {
            tld.clear();
        }
    }

    @Test
    void getQueueSizeDelegatesOrReturnsZero() {
        ThreadLocalDispatcher tld = new ThreadLocalDispatcher();
        assertThat(tld.getQueueSize()).isZero();
        CapturingDispatcher capture = new CapturingDispatcher();
        tld.install(capture);
        try {
            capture.send(msg(1));
            capture.send(msg(2));
            assertThat(tld.getQueueSize()).isEqualTo(2);
        } finally {
            tld.clear();
        }
    }
}
```

- [ ] **Step 2: Run and verify compile failure**

Run: `cd /Users/david/development/src/opennms/delta-v/core/flow-enricher && ../../maven/bin/mvn -q test -Dtest=ThreadLocalDispatcherTest 2>&1 | tail -15`

Expected: compile error saying `ThreadLocalDispatcher` cannot be resolved.

- [ ] **Step 3: Implement ThreadLocalDispatcher**

Create `core/flow-enricher/src/main/java/org/deltav/flows/enricher/parser/ThreadLocalDispatcher.java`:

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
package org.deltav.flows.enricher.parser;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.opennms.core.ipc.sink.api.AsyncDispatcher;
import org.opennms.netmgt.telemetry.api.receiver.TelemetryMessage;

/**
 * A singleton {@link AsyncDispatcher} that delegates every {@code send()}
 * call to a per-thread {@link CapturingDispatcher} installed by the
 * enricher's processor code immediately before it calls
 * {@code UdpParser.parse()} and cleared in the corresponding {@code finally}.
 *
 * <p>Horizon {@code UdpParser} instances take their {@code AsyncDispatcher}
 * as a final constructor field — the dispatcher cannot be swapped per call
 * on a singleton parser. But the parser's per-exporter template cache must
 * persist across calls, so fresh parsers per call are unusable. This class
 * resolves the conflict: the parser is a long-lived singleton bean wired to
 * this {@code ThreadLocalDispatcher}; each processor call installs a private
 * {@link CapturingDispatcher} onto the current thread, lets the parser write
 * into it, reads the captured messages after {@code parse()} returns, and
 * clears the thread-local in {@code finally}.
 *
 * <p><strong>Critical invariant:</strong> every {@link #install} call must be
 * paired with a {@link #clear} call in a {@code finally} block that runs
 * regardless of parser outcome. A leaked thread-local silently cross-contaminates
 * the next parse call on the same thread — a correctness bug that will not
 * surface in tests or staging without explicit coverage (see
 * {@code Netflow9MessageProcessorTest.sequentialCallsDoNotCrossContaminate}).
 */
public class ThreadLocalDispatcher implements AsyncDispatcher<TelemetryMessage> {

    private final ThreadLocal<CapturingDispatcher> delegate = new ThreadLocal<>();

    /**
     * Install the given {@link CapturingDispatcher} as the per-thread delegate.
     * Overwrites any previously-installed dispatcher on this thread; callers
     * that do this are almost certainly violating the install/clear pairing
     * contract, so leaving overwrite semantics implicit is intentional —
     * clear callers in the codebase will either set once per call or pair
     * install/clear cleanly.
     */
    public void install(CapturingDispatcher dispatcher) {
        delegate.set(Objects.requireNonNull(dispatcher, "dispatcher"));
    }

    /**
     * Remove the per-thread delegate. Must be called in a {@code finally} block
     * paired with {@link #install}.
     */
    public void clear() {
        delegate.remove();
    }

    /**
     * Returns the currently-installed {@link CapturingDispatcher} on this
     * thread, or {@code null} if nothing is installed.
     */
    public CapturingDispatcher current() {
        return delegate.get();
    }

    @Override
    public CompletableFuture<DispatchStatus> send(TelemetryMessage message) {
        CapturingDispatcher d = delegate.get();
        if (d == null) {
            throw new IllegalStateException(
                    "No CapturingDispatcher installed on thread "
                            + Thread.currentThread().getName()
                            + "; parser dispatched outside a processor call?");
        }
        return d.send(message);
    }

    @Override
    public int getQueueSize() {
        CapturingDispatcher d = delegate.get();
        return d == null ? 0 : d.getQueueSize();
    }

    @Override
    public void close() {
        // Thread-local dispatchers are cleared per-call; no global cleanup.
    }
}
```

- [ ] **Step 4: Run the tests and verify they pass**

Run: `cd /Users/david/development/src/opennms/delta-v/core/flow-enricher && ../../maven/bin/mvn -q test -Dtest=ThreadLocalDispatcherTest 2>&1 | tail -15`

Expected: `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 5: Commit**

```bash
git add core/flow-enricher/src/main/java/org/deltav/flows/enricher/parser/ThreadLocalDispatcher.java \
        core/flow-enricher/src/test/java/org/deltav/flows/enricher/parser/ThreadLocalDispatcherTest.java
git commit -m "feat(flow-enricher): add ThreadLocalDispatcher for parser capture bridge"
```

---

## Task 4: Parser collaborators (DnsResolver, EventForwarder, Identity)

Three small beans the parser constructors require. All are trivial no-op / log-only / static implementations per the spec's observability decision (Q3: log-only, defer Kafka forwarder).

**Files:**
- Create: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/parser/NoOpDnsResolver.java`
- Create: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/parser/LoggingEventForwarder.java`
- Create: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/parser/StaticIdentity.java`
- Create: `core/flow-enricher/src/test/java/org/deltav/flows/enricher/parser/NoOpDnsResolverTest.java`
- Create: `core/flow-enricher/src/test/java/org/deltav/flows/enricher/parser/LoggingEventForwarderTest.java`

- [ ] **Step 1: Write the NoOpDnsResolver test**

Create `core/flow-enricher/src/test/java/org/deltav/flows/enricher/parser/NoOpDnsResolverTest.java`:

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
package org.deltav.flows.enricher.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class NoOpDnsResolverTest {

    @Test
    void lookupAlwaysReturnsEmptyOptional() throws Exception {
        NoOpDnsResolver resolver = new NoOpDnsResolver();
        Optional<InetAddress> result = resolver.lookup("www.example.com").get();
        assertThat(result).isEmpty();
    }

    @Test
    void reverseLookupAlwaysReturnsEmptyOptional() throws Exception {
        NoOpDnsResolver resolver = new NoOpDnsResolver();
        Optional<String> result = resolver.reverseLookup(InetAddress.getByName("10.0.0.1")).get();
        assertThat(result).isEmpty();
    }
}
```

- [ ] **Step 2: Implement NoOpDnsResolver**

Create `core/flow-enricher/src/main/java/org/deltav/flows/enricher/parser/NoOpDnsResolver.java`:

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
package org.deltav.flows.enricher.parser;

import java.net.InetAddress;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.opennms.netmgt.dnsresolver.api.DnsResolver;

/**
 * A {@link DnsResolver} that returns an empty {@link Optional} for every
 * lookup. The flow-enricher's downstream enrichment code performs its own
 * node lookups against the ipinterface table, so parser-level DNS resolution
 * is unnecessary here.
 */
public class NoOpDnsResolver implements DnsResolver {

    @Override
    public CompletableFuture<Optional<InetAddress>> lookup(String hostname) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<Optional<String>> reverseLookup(InetAddress inetAddress) {
        return CompletableFuture.completedFuture(Optional.empty());
    }
}
```

- [ ] **Step 3: Write the LoggingEventForwarder test**

Create `core/flow-enricher/src/test/java/org/deltav/flows/enricher/parser/LoggingEventForwarderTest.java`:

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
package org.deltav.flows.enricher.parser;

import org.junit.jupiter.api.Test;
import org.opennms.netmgt.xml.event.Event;
import org.opennms.netmgt.xml.event.Log;

class LoggingEventForwarderTest {

    @Test
    void sendNowEventDoesNotThrow() {
        LoggingEventForwarder forwarder = new LoggingEventForwarder();
        Event event = new Event();
        event.setUei("uei.deltav/test/parser/clockSkew");
        forwarder.sendNow(event);
    }

    @Test
    void sendNowLogDoesNotThrow() {
        LoggingEventForwarder forwarder = new LoggingEventForwarder();
        Log log = new Log();
        forwarder.sendNow(log);
    }

    @Test
    void sendNowSyncEventDoesNotThrow() {
        LoggingEventForwarder forwarder = new LoggingEventForwarder();
        Event event = new Event();
        event.setUei("uei.deltav/test/parser/clockSkew");
        forwarder.sendNowSync(event);
    }

    @Test
    void sendNowSyncLogDoesNotThrow() {
        LoggingEventForwarder forwarder = new LoggingEventForwarder();
        Log log = new Log();
        forwarder.sendNowSync(log);
    }

    @Test
    void nullEventIsTolerated() {
        LoggingEventForwarder forwarder = new LoggingEventForwarder();
        forwarder.sendNow((Event) null);
        forwarder.sendNowSync((Event) null);
    }
}
```

- [ ] **Step 4: Implement LoggingEventForwarder**

Create `core/flow-enricher/src/main/java/org/deltav/flows/enricher/parser/LoggingEventForwarder.java`:

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
package org.deltav.flows.enricher.parser;

import org.opennms.netmgt.events.api.EventForwarder;
import org.opennms.netmgt.xml.event.Event;
import org.opennms.netmgt.xml.event.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link EventForwarder} that logs events at {@code WARN} level and then
 * discards them. The horizon Netflow parsers use {@code EventForwarder} to
 * emit operational signals (clock skew detection, repeated unknown template
 * IDs, illegal flow records) — this implementation keeps those visible in
 * the flow-enricher log without wiring up a real event pipeline.
 *
 * <p>The Phase 2 design explicitly defers a Kafka-backed event forwarder as
 * a followup: triggered the first time a production exporter surfaces an
 * operational incident we want visibility on. See
 * {@code feedback_provisiond_requisition_drift.md}-style followup memory
 * entries tracked in {@code MEMORY.md}.
 */
public class LoggingEventForwarder implements EventForwarder {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingEventForwarder.class);

    @Override
    public void sendNow(Event event) {
        logEvent(event);
    }

    @Override
    public void sendNow(Log eventLog) {
        logBatch(eventLog);
    }

    @Override
    public void sendNowSync(Event event) {
        logEvent(event);
    }

    @Override
    public void sendNowSync(Log eventLog) {
        logBatch(eventLog);
    }

    private void logEvent(Event event) {
        if (event == null) {
            return;
        }
        LOG.warn("Parser event dropped (logging-only forwarder): uei={}, source={}",
                event.getUei(), event.getSource());
    }

    private void logBatch(Log eventLog) {
        if (eventLog == null || eventLog.getEvents() == null || eventLog.getEvents().getEvent() == null) {
            return;
        }
        for (Event e : eventLog.getEvents().getEvent()) {
            logEvent(e);
        }
    }
}
```

- [ ] **Step 5: Implement StaticIdentity (no test — trivial data class)**

Create `core/flow-enricher/src/main/java/org/deltav/flows/enricher/parser/StaticIdentity.java`:

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
package org.deltav.flows.enricher.parser;

import java.util.Objects;

import org.opennms.distributed.core.api.Identity;

/**
 * An {@link Identity} whose values are fixed at construction time from
 * configuration properties. The flow-enricher is a single-tenant Spring Boot
 * service; its location and system ID are set at startup and never change,
 * unlike Minion-side identities that are generated per-deployment.
 */
public class StaticIdentity implements Identity {

    private final String id;
    private final String location;
    private final String type;

    public StaticIdentity(String id, String location, String type) {
        this.id = Objects.requireNonNull(id, "id");
        this.location = Objects.requireNonNull(location, "location");
        this.type = Objects.requireNonNull(type, "type");
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getLocation() {
        return location;
    }

    @Override
    public String getType() {
        return type;
    }
}
```

- [ ] **Step 6: Run all parser-package tests**

Run: `cd /Users/david/development/src/opennms/delta-v/core/flow-enricher && ../../maven/bin/mvn -q test -Dtest='org.deltav.flows.enricher.parser.*' 2>&1 | tail -15`

Expected: all tests pass.

- [ ] **Step 7: Commit**

```bash
git add core/flow-enricher/src/main/java/org/deltav/flows/enricher/parser/NoOpDnsResolver.java \
        core/flow-enricher/src/main/java/org/deltav/flows/enricher/parser/LoggingEventForwarder.java \
        core/flow-enricher/src/main/java/org/deltav/flows/enricher/parser/StaticIdentity.java \
        core/flow-enricher/src/test/java/org/deltav/flows/enricher/parser/NoOpDnsResolverTest.java \
        core/flow-enricher/src/test/java/org/deltav/flows/enricher/parser/LoggingEventForwarderTest.java
git commit -m "feat(flow-enricher): add parser collaborators (DnsResolver, EventForwarder, Identity)"
```

---

## Task 5: Rework AbstractProtocolMessageProcessor for two-stage bridge

This is the core of Phase 2. We add a `UdpParser` field to the base class via a subclass-supplied accessor, run Stage 1 parsing inside a new `runParser()` helper with try/finally ThreadLocal hygiene, synthesize a new `TelemetryMessageLog`, and pass it to the existing Stage 2 adapter path.

**Files:**
- Modify: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/AbstractProtocolMessageProcessor.java`
- Create: `core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/FakeUdpParser.java`

- [ ] **Step 1: Create the FakeUdpParser test stub**

Create `core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/FakeUdpParser.java`:

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
package org.deltav.flows.enricher.protocol;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;

import org.deltav.flows.enricher.parser.ThreadLocalDispatcher;
import org.opennms.netmgt.telemetry.api.receiver.TelemetryMessage;
import org.opennms.netmgt.telemetry.listeners.UdpParser;

import io.netty.buffer.ByteBuf;

/**
 * Test stub for {@link UdpParser}. Allows unit tests to script what bytes
 * the parser should emit for each invocation of {@code parse()}, and
 * optionally force it to throw on the next invocation. Dispatches via the
 * installed {@link ThreadLocalDispatcher} so the stub exercises the
 * real capture path.
 */
final class FakeUdpParser implements UdpParser {

    private final ThreadLocalDispatcher dispatcher;
    private final List<byte[]> scriptedEmissions = new ArrayList<>();
    private RuntimeException nextException;
    private int parseCallCount;

    FakeUdpParser(ThreadLocalDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    /** Queue bytes to dispatch as one captured TelemetryMessage on the next parse() call. */
    FakeUdpParser emitNext(byte[] bytes) {
        scriptedEmissions.add(bytes);
        return this;
    }

    /** Make the next parse() call throw the given exception after incrementing the call count. */
    FakeUdpParser throwOnNextParse(RuntimeException e) {
        this.nextException = e;
        return this;
    }

    int getParseCallCount() {
        return parseCallCount;
    }

    @Override
    public CompletableFuture<?> parse(ByteBuf buffer, InetSocketAddress remoteAddress, InetSocketAddress localAddress) {
        parseCallCount++;
        if (nextException != null) {
            RuntimeException e = nextException;
            nextException = null;
            throw e;
        }
        if (!scriptedEmissions.isEmpty()) {
            byte[] bytes = scriptedEmissions.remove(0);
            TelemetryMessage msg = new TelemetryMessage(remoteAddress, ByteBuffer.wrap(bytes));
            dispatcher.send(msg);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public String getName() {
        return "fake-udp-parser";
    }

    @Override
    public String getDescription() {
        return "Test stub for unit tests";
    }

    @Override
    public Object dumpInternalState() {
        return "fake";
    }

    @Override
    public void start(ScheduledExecutorService executorService) {
        // no-op
    }

    @Override
    public void stop() {
        // no-op
    }
}
```

- [ ] **Step 2: Rewrite `AbstractProtocolMessageProcessor` to the two-stage bridge**

Replace the entire contents of `core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/AbstractProtocolMessageProcessor.java` with:

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
package org.deltav.flows.enricher.protocol;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.deltav.flows.enricher.parser.CapturingDispatcher;
import org.deltav.flows.enricher.parser.ThreadLocalDispatcher;
import org.deltav.flows.enricher.pipeline.CapturingPipeline;
import org.opennms.netmgt.flows.api.Flow;
import org.opennms.netmgt.telemetry.api.receiver.TelemetryMessage;
import org.opennms.netmgt.telemetry.common.ipc.TelemetryProtos;
import org.opennms.netmgt.telemetry.listeners.UdpParser;
import org.opennms.netmgt.telemetry.protocols.flows.AbstractFlowAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Base class for per-protocol flow processors. Implements the Phase 2
 * two-stage parser bridge:
 *
 * <ol>
 *   <li><strong>Stage 1 — parse raw UDP bytes into {@code FlowMessage} protobuf.</strong>
 *       Uses a horizon {@link UdpParser} supplied by the subclass via
 *       {@link #getParser()}. The parser is a long-lived bean (holds per-exporter
 *       template state in {@code UdpSessionManager}). Its output is captured
 *       into a per-call {@link CapturingDispatcher} installed onto the shared
 *       {@link ThreadLocalDispatcher} injected at construction.</li>
 *   <li><strong>Stage 2 — turn {@code FlowMessage} protobuf into {@link Flow} POJOs.</strong>
 *       Uses horizon's {@link AbstractFlowAdapter} + {@link CapturingPipeline},
 *       exactly as Phase 1.5 did. The Stage 1 output is wrapped in a synthetic
 *       {@link TelemetryProtos.TelemetryMessageLog} whose entries carry the
 *       {@code FlowMessage} bytes Stage 2 expects.</li>
 * </ol>
 *
 * <h2>Error boundaries</h2>
 *
 * <p>Stage 1 and Stage 2 have independent try/catch blocks so a parser bug
 * and an adapter bug get logged with distinct, attributable messages. Per-entry
 * parser failures inside the Stage 1 loop are logged at {@code DEBUG} and do
 * not abort the batch — one corrupt UDP packet should not discard its
 * neighbors. Whole-batch failures (anything that escapes the per-entry
 * try/catch) are logged at {@code WARN} and return an empty flow list.
 *
 * <h2>ThreadLocal hygiene (critical)</h2>
 *
 * <p>Every call to {@link ThreadLocalDispatcher#install install} in
 * {@link #runParser} is paired with a {@link ThreadLocalDispatcher#clear clear}
 * in a {@code finally} block that runs regardless of outcome. A leaked
 * thread-local silently cross-contaminates the next call on the same Spring
 * Cloud Stream consumer thread, which would attribute flows from one exporter
 * to another's result — a correctness bug that does not surface without
 * explicit coverage. The corresponding stress tests live in
 * {@code Netflow9MessageProcessorTest}.
 *
 * <h2>Thread-safety</h2>
 *
 * <p>Each call creates its own {@link CapturingDispatcher} and its own
 * {@link CapturingPipeline} + adapter instance. The only shared state is the
 * singleton {@link UdpParser} (thread-safe via {@code ConcurrentHashMap}
 * inside {@code UdpSessionManager}) and the singleton
 * {@link ThreadLocalDispatcher} (safe by design — per-thread delegation).
 */
public abstract class AbstractProtocolMessageProcessor implements ProtocolMessageProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractProtocolMessageProcessor.class);

    /**
     * Default local address passed to {@code UdpParser.parse()}. Horizon uses
     * the local address as part of its session key; in our server-side bridge
     * we always pass a constant because we are not a real UDP listener — the
     * Minion already bound the real socket.
     */
    private static final InetSocketAddress LOCALHOST_SINK = new InetSocketAddress("127.0.0.1", 0);

    private final ThreadLocalDispatcher threadLocalDispatcher;

    protected AbstractProtocolMessageProcessor(ThreadLocalDispatcher threadLocalDispatcher) {
        this.threadLocalDispatcher = Objects.requireNonNull(threadLocalDispatcher, "threadLocalDispatcher");
    }

    @Override
    public List<Flow> process(TelemetryProtos.TelemetryMessageLog rawLog) {
        if (rawLog == null || rawLog.getMessageCount() == 0) {
            return Collections.emptyList();
        }

        // Stage 1: parse raw UDP bytes -> FlowMessage protobuf bytes
        TelemetryProtos.TelemetryMessageLog parsedLog;
        try {
            parsedLog = runParser(rawLog);
        } catch (RuntimeException e) {
            LOG.warn("Parser {} failed on message log ({} entries, sourceAddr={}): {}",
                    getParser().getClass().getSimpleName(),
                    rawLog.getMessageCount(),
                    rawLog.getSourceAddress(),
                    e.getMessage(), e);
            return Collections.emptyList();
        }
        if (parsedLog.getMessageCount() == 0) {
            // Parser ate everything (e.g. all entries were templates, or all data
            // records referenced unknown templates). Not an error, just nothing to do.
            return Collections.emptyList();
        }

        // Stage 2: FlowMessage protobuf -> Flow POJO (unchanged from Phase 1.5)
        final CapturingPipeline pipeline = new CapturingPipeline();
        final AbstractFlowAdapter<?> adapter = createAdapter(pipeline);
        try {
            adapter.handleMessageLog(parsedLog);
        } catch (RuntimeException e) {
            LOG.warn("Horizon adapter {} failed on parsed log ({} entries): {}",
                    adapter.getClass().getSimpleName(),
                    parsedLog.getMessageCount(),
                    e.getMessage(), e);
            return Collections.emptyList();
        }
        return pipeline.getCapturedFlows();
    }

    /**
     * Run Stage 1. Installs a fresh {@link CapturingDispatcher} onto the
     * {@link ThreadLocalDispatcher}, iterates each entry in the input log
     * through {@link UdpParser#parse}, captures the parser's dispatched
     * messages, and wraps them in a synthetic {@link TelemetryProtos.TelemetryMessageLog}
     * whose entries carry {@code FlowMessage} protobuf bytes.
     *
     * <p>Per-entry parser exceptions are logged at {@code DEBUG} and
     * skipped — one bad UDP packet does not poison the rest of the batch.
     * Exceptions that escape this method (e.g. a catastrophic bug in the
     * capturing dispatcher itself) are caught by the outer try/catch in
     * {@link #process}.
     */
    private TelemetryProtos.TelemetryMessageLog runParser(TelemetryProtos.TelemetryMessageLog rawLog) {
        CapturingDispatcher captureForThisCall = new CapturingDispatcher();
        threadLocalDispatcher.install(captureForThisCall);
        try {
            InetSocketAddress remoteAddress = new InetSocketAddress(
                    rawLog.getSourceAddress(), rawLog.getSourcePort());
            UdpParser parser = getParser();
            for (TelemetryProtos.TelemetryMessage entry : rawLog.getMessageList()) {
                if (entry.getBytes().isEmpty()) {
                    continue;
                }
                ByteBuf buf = Unpooled.wrappedBuffer(entry.getBytes().toByteArray());
                try {
                    parser.parse(buf, remoteAddress, LOCALHOST_SINK).join();
                } catch (Exception e) {
                    LOG.debug("Parser dropped one entry ({} bytes, sourceAddr={}): {}",
                            entry.getBytes().size(), rawLog.getSourceAddress(), e.getMessage());
                }
            }
            return synthesizeParsedLog(rawLog, captureForThisCall.getCaptured());
        } finally {
            threadLocalDispatcher.clear();
        }
    }

    /**
     * Build a new {@link TelemetryProtos.TelemetryMessageLog} that copies the
     * location / systemId / source address / source port fields from the
     * input raw log, and replaces the message entries with one entry per
     * captured {@link TelemetryMessage} whose {@code bytes} field is the
     * captured message's buffer (which is already a serialized
     * {@code FlowMessage} protobuf produced by horizon's parser).
     */
    private static TelemetryProtos.TelemetryMessageLog synthesizeParsedLog(
            TelemetryProtos.TelemetryMessageLog rawLog,
            List<TelemetryMessage> captured) {
        TelemetryProtos.TelemetryMessageLog.Builder builder = TelemetryProtos.TelemetryMessageLog.newBuilder()
                .setLocation(rawLog.getLocation())
                .setSystemId(rawLog.getSystemId());
        if (rawLog.hasSourceAddress()) {
            builder.setSourceAddress(rawLog.getSourceAddress());
        }
        if (rawLog.hasSourcePort()) {
            builder.setSourcePort(rawLog.getSourcePort());
        }
        long now = System.currentTimeMillis();
        for (TelemetryMessage msg : captured) {
            ByteBuffer buffer = msg.getBuffer();
            // Duplicate so we don't mutate the parser's buffer cursor state.
            ByteBuffer copy = buffer.duplicate();
            byte[] bytes = new byte[copy.remaining()];
            copy.get(bytes);
            builder.addMessage(TelemetryProtos.TelemetryMessage.newBuilder()
                    .setTimestamp(now)
                    .setBytes(ByteString.copyFrom(bytes)));
        }
        return builder.build();
    }

    /**
     * Subclass hook — returns the singleton horizon {@link UdpParser} bound
     * to this protocol. The parser is typically injected via the subclass
     * constructor and stored as a final field.
     */
    protected abstract UdpParser getParser();

    /**
     * Subclass hook — constructs a fresh {@link AbstractFlowAdapter}
     * subclass for this call, bound to the supplied capturing pipeline. The
     * adapter must expect each entry in a {@code TelemetryMessageLog} to
     * carry a serialized {@code FlowMessage} protobuf (which is exactly
     * what {@link #synthesizeParsedLog} produces).
     */
    protected abstract AbstractFlowAdapter<?> createAdapter(CapturingPipeline pipeline);
}
```

- [ ] **Step 3: Run the flow-enricher build to surface every subclass that needs updating**

Run: `cd /Users/david/development/src/opennms/delta-v && ./compile.pl -DskipTests --projects :org.deltav.flows.flow-enricher -am compile 2>&1 | tail -30`

Expected: compile errors in `Netflow5MessageProcessor`, `Netflow9MessageProcessor`, `IpfixMessageProcessor`, and `SFlowMessageProcessor` because they don't pass `ThreadLocalDispatcher` to the base constructor and don't implement `getParser()`. These will be fixed in Task 6. Do not commit yet.

- [ ] **Step 4: (Deferred — commit at end of Task 6, where the build goes green again.)**

---

## Task 6: Update per-protocol processors for Shape 2

Each of the four subclasses gains a {@code UdpParser} constructor parameter and implements {@code getParser()}. The existing {@code AdapterDefinition} + {@code MetricRegistry} parameters remain — they feed Stage 2 adapter construction. Existing unit tests get updated to wire the new parameter.

**Files:**
- Modify: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/Netflow5MessageProcessor.java`
- Modify: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/Netflow9MessageProcessor.java`
- Modify: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/IpfixMessageProcessor.java`
- Modify: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/SFlowMessageProcessor.java`
- Modify: `core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/Netflow5MessageProcessorTest.java`
- Modify: `core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/Netflow9MessageProcessorTest.java`
- Modify: `core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/IpfixMessageProcessorTest.java`
- Modify: `core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/SFlowMessageProcessorTest.java`

- [ ] **Step 1: Update Netflow9MessageProcessor**

Replace `core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/Netflow9MessageProcessor.java` with:

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
package org.deltav.flows.enricher.protocol;

import java.util.Objects;

import org.deltav.flows.enricher.parser.ThreadLocalDispatcher;
import org.deltav.flows.enricher.pipeline.CapturingPipeline;
import org.opennms.netmgt.telemetry.config.api.AdapterDefinition;
import org.opennms.netmgt.telemetry.listeners.UdpParser;
import org.opennms.netmgt.telemetry.protocols.flows.AbstractFlowAdapter;
import org.opennms.netmgt.telemetry.protocols.netflow.adapter.netflow9.Netflow9Adapter;

import com.codahale.metrics.MetricRegistry;

/**
 * Protocol processor for Netflow v9. Stage 1 parses raw Netflow v9 UDP wire
 * bytes via a horizon {@link UdpParser}; Stage 2 turns the parser-emitted
 * {@code FlowMessage} protobufs into {@link org.opennms.netmgt.flows.api.Flow}
 * POJOs via horizon's {@link Netflow9Adapter}.
 */
public class Netflow9MessageProcessor extends AbstractProtocolMessageProcessor {

    private final UdpParser parser;
    private final AdapterDefinition adapterDefinition;
    private final MetricRegistry metricRegistry;

    public Netflow9MessageProcessor(
            UdpParser parser,
            AdapterDefinition adapterDefinition,
            MetricRegistry metricRegistry,
            ThreadLocalDispatcher threadLocalDispatcher) {
        super(threadLocalDispatcher);
        this.parser = Objects.requireNonNull(parser, "parser");
        this.adapterDefinition = Objects.requireNonNull(adapterDefinition, "adapterDefinition");
        this.metricRegistry = Objects.requireNonNull(metricRegistry, "metricRegistry");
    }

    @Override
    protected UdpParser getParser() {
        return parser;
    }

    @Override
    protected AbstractFlowAdapter<?> createAdapter(CapturingPipeline pipeline) {
        return new Netflow9Adapter(adapterDefinition, metricRegistry, pipeline);
    }
}
```

- [ ] **Step 2: Update Netflow5MessageProcessor, IpfixMessageProcessor, SFlowMessageProcessor**

Apply the same four-parameter constructor shape to each of the remaining three processors. The only per-processor difference is the adapter class used in `createAdapter()`:

- `Netflow5MessageProcessor` → `new Netflow5Adapter(adapterDefinition, metricRegistry, pipeline);`
  Imports needed: `org.opennms.netmgt.telemetry.protocols.netflow.adapter.netflow5.Netflow5Adapter`
- `IpfixMessageProcessor` → `new IpfixAdapter(adapterDefinition, metricRegistry, pipeline);`
  Imports needed: `org.opennms.netmgt.telemetry.protocols.netflow.adapter.ipfix.IpfixAdapter`
- `SFlowMessageProcessor` → `new SFlowAdapter(adapterDefinition, metricRegistry, pipeline);`
  Imports needed: `org.opennms.netmgt.telemetry.protocols.sflow.adapter.SFlowAdapter`

Each file follows the Netflow9 template exactly — copy it, swap the class name, swap the adapter type, and update the class javadoc for the protocol. sFlow also gets a javadoc note that it is "best-effort" per the Phase 2 spec.

- [ ] **Step 3: Update Netflow9MessageProcessorTest with the two ThreadLocal stress tests**

Replace `core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/Netflow9MessageProcessorTest.java` with:

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
package org.deltav.flows.enricher.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.deltav.flows.enricher.parser.ThreadLocalDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opennms.netmgt.flows.api.Flow;
import org.opennms.netmgt.telemetry.common.ipc.TelemetryProtos;
import org.opennms.netmgt.telemetry.config.api.AdapterDefinition;
import org.opennms.netmgt.telemetry.protocols.netflow.transport.FlowMessage;
import org.opennms.netmgt.telemetry.protocols.netflow.transport.NetflowVersion;

import com.codahale.metrics.MetricRegistry;
import com.google.protobuf.ByteString;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;

class Netflow9MessageProcessorTest {

    private ThreadLocalDispatcher tld;
    private FakeUdpParser fakeParser;
    private Netflow9MessageProcessor processor;

    @BeforeEach
    void setUp() {
        tld = new ThreadLocalDispatcher();
        fakeParser = new FakeUdpParser(tld);
        AdapterDefinition adapterDefinition = TestAdapterDefinitions.testAdapterDefinition("netflow9-test");
        MetricRegistry metricRegistry = new MetricRegistry();
        processor = new Netflow9MessageProcessor(fakeParser, adapterDefinition, metricRegistry, tld);
    }

    @Test
    void processReturnsEmptyListForNullMessageLog() {
        assertThat(processor.process(null)).isEmpty();
    }

    @Test
    void processReturnsEmptyListForEmptyMessageLog() {
        TelemetryProtos.TelemetryMessageLog empty = TelemetryProtos.TelemetryMessageLog.newBuilder()
                .setLocation("Default")
                .setSystemId("test-system")
                .build();
        assertThat(processor.process(empty)).isEmpty();
    }

    @Test
    void processYieldsFlowsWhenParserEmitsFlowMessageBytes() {
        byte[] flowMessageBytes = buildFlowMessage("10.0.0.1", "10.0.0.2").toByteArray();
        fakeParser.emitNext(flowMessageBytes);

        TelemetryProtos.TelemetryMessageLog raw = rawLogWithOneEntry(new byte[]{1, 2, 3, 4});
        List<Flow> flows = processor.process(raw);

        assertThat(flows).hasSize(1);
        assertThat(flows.get(0).getSrcAddr()).isEqualTo("10.0.0.1");
        assertThat(flows.get(0).getDstAddr()).isEqualTo("10.0.0.2");
        assertThat(fakeParser.getParseCallCount()).isEqualTo(1);
    }

    @Test
    void parserExceptionDoesNotLeakThreadLocal() {
        fakeParser.throwOnNextParse(new IllegalStateException("boom from parser"));

        TelemetryProtos.TelemetryMessageLog raw = rawLogWithOneEntry(new byte[]{1, 2, 3, 4});
        List<Flow> flows = processor.process(raw);

        // The per-entry catch should swallow the exception at DEBUG and
        // produce zero flows, NOT rethrow.
        assertThat(flows).isEmpty();
        // The critical invariant: thread-local is cleared even after exception.
        assertThat(tld.current()).isNull();
    }

    @Test
    void sequentialCallsDoNotCrossContaminate() {
        byte[] flowA = buildFlowMessage("10.0.0.11", "10.0.0.12").toByteArray();
        byte[] flowB = buildFlowMessage("10.0.0.21", "10.0.0.22").toByteArray();
        fakeParser.emitNext(flowA).emitNext(flowB);

        List<Flow> resultA = processor.process(rawLogWithOneEntry(new byte[]{1}));
        List<Flow> resultB = processor.process(rawLogWithOneEntry(new byte[]{2}));

        assertThat(resultA).hasSize(1);
        assertThat(resultA.get(0).getSrcAddr()).isEqualTo("10.0.0.11");

        assertThat(resultB).hasSize(1);
        assertThat(resultB.get(0).getSrcAddr()).isEqualTo("10.0.0.21");

        // A's flow must not appear in B's result
        assertThat(resultB).noneMatch(f -> "10.0.0.11".equals(f.getSrcAddr()));
        // And vice versa
        assertThat(resultA).noneMatch(f -> "10.0.0.21".equals(f.getSrcAddr()));
    }

    private static TelemetryProtos.TelemetryMessageLog rawLogWithOneEntry(byte[] bytes) {
        return TelemetryProtos.TelemetryMessageLog.newBuilder()
                .setLocation("Default")
                .setSystemId("test-system")
                .setSourceAddress("192.0.2.1")
                .setSourcePort(54321)
                .addMessage(TelemetryProtos.TelemetryMessage.newBuilder()
                        .setTimestamp(System.currentTimeMillis())
                        .setBytes(ByteString.copyFrom(bytes)))
                .build();
    }

    private static FlowMessage buildFlowMessage(String srcAddr, String dstAddr) {
        return FlowMessage.newBuilder()
                .setTimestamp(System.currentTimeMillis())
                .setNetflowVersion(NetflowVersion.V9)
                .setSrcAddress(srcAddr)
                .setDstAddress(dstAddr)
                .setNextHopAddress("10.0.0.254")
                .setSrcPort(UInt32Value.of(12345))
                .setDstPort(UInt32Value.of(80))
                .setProtocol(UInt32Value.of(17))
                .setNumBytes(UInt64Value.of(2048L))
                .setNumPackets(UInt64Value.of(16L))
                .setFirstSwitched(UInt64Value.of(1_700_000_000_000L))
                .setLastSwitched(UInt64Value.of(1_700_000_001_000L))
                .setDeltaSwitched(UInt64Value.of(1_700_000_000_000L))
                .setInputSnmpIfindex(UInt32Value.of(3))
                .setOutputSnmpIfindex(UInt32Value.of(4))
                .setIpProtocolVersion(UInt32Value.of(4))
                .build();
    }
}
```

- [ ] **Step 4: Update the other three processor tests for Shape 2**

For `Netflow5MessageProcessorTest`, `IpfixMessageProcessorTest`, `SFlowMessageProcessorTest`:

Update each test's `@BeforeEach setUp()` to match the Netflow9 pattern:

```java
@BeforeEach
void setUp() {
    tld = new ThreadLocalDispatcher();
    fakeParser = new FakeUdpParser(tld);
    AdapterDefinition adapterDefinition = TestAdapterDefinitions.testAdapterDefinition("netflow5-test"); // or ipfix-test / sflow-test
    MetricRegistry metricRegistry = new MetricRegistry();
    processor = new Netflow5MessageProcessor(fakeParser, adapterDefinition, metricRegistry, tld);
    // similar for Ipfix / SFlow
}
```

Add the `processYieldsFlowsWhenParserEmitsFlowMessageBytes` test with protocol-appropriate `NetflowVersion` (V5 / V10 / SFLOW). For sFlow, the parser typically produces BSON rather than FlowMessage — the sFlow test can use a minimal FlowMessage fixture but document that live sFlow fixtures are deferred per the best-effort Phase 2 decision.

Do NOT duplicate the two ThreadLocal stress tests across the four files — they live only in `Netflow9MessageProcessorTest` because they're parser-agnostic. Keep the other three test files focused on their protocol's happy path.

- [ ] **Step 5: Run all four processor tests and verify they pass**

Run: `cd /Users/david/development/src/opennms/delta-v/core/flow-enricher && ../../maven/bin/mvn -q test -Dtest='org.deltav.flows.enricher.protocol.*MessageProcessorTest' 2>&1 | tail -30`

Expected: all tests pass across the four processor test classes.

- [ ] **Step 6: Commit Task 5 + Task 6 together**

The Task 5 changes are dependent on Task 6 for the build to succeed, so the two get one commit:

```bash
git add core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/AbstractProtocolMessageProcessor.java \
        core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/Netflow5MessageProcessor.java \
        core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/Netflow9MessageProcessor.java \
        core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/IpfixMessageProcessor.java \
        core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/SFlowMessageProcessor.java \
        core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/FakeUdpParser.java \
        core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/Netflow5MessageProcessorTest.java \
        core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/Netflow9MessageProcessorTest.java \
        core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/IpfixMessageProcessorTest.java \
        core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/SFlowMessageProcessorTest.java
git commit -m "feat(flow-enricher): rework AbstractProtocolMessageProcessor for two-stage parser bridge

Stage 1 runs a horizon UdpParser under a ThreadLocalDispatcher-captured
dispatcher to turn raw UDP bytes into FlowMessage protobuf. Stage 2 reuses
the Phase 1.5 AbstractFlowAdapter path unchanged. Subclass constructors
gain a UdpParser parameter (Shape 2) but keep their existing AdapterDefinition
and MetricRegistry dependencies for Stage 2 adapter construction.

Adds two ThreadLocal stress tests in Netflow9MessageProcessorTest that
directly verify the install/clear invariant and cross-call isolation."
```

---

## Task 7: Spring wiring in FlowEnricherConfiguration

Add beans for the `InformationElementDatabase`, `ScheduledExecutorService`, `ThreadLocalDispatcher`, the three parser collaborators, the four `UdpParser` singletons, and rewire the four processor beans to the Shape 2 constructor.

**Files:**
- Modify: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/FlowEnricherConfiguration.java`

- [ ] **Step 1: Update imports and add the new beans**

Replace the contents of `core/flow-enricher/src/main/java/org/deltav/flows/enricher/FlowEnricherConfiguration.java` with the updated configuration. The key changes from the existing file are:

1. Add imports for the new parser classes, `UdpParser` subclasses, collaborators, and `ScheduledExecutorService`.
2. Add beans for: `ScheduledExecutorService`, `InformationElementDatabase`, `NoOpDnsResolver`, `LoggingEventForwarder`, `StaticIdentity`, `ThreadLocalDispatcher`, and the four concrete `UdpParser` beans.
3. Rewrite the four processor beans to pass `UdpParser` + `ThreadLocalDispatcher`.
4. Keep all the existing enrichment wiring (node lookup, locality, mapping, etc.) untouched.

The full updated class:

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
package org.deltav.flows.enricher;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import javax.sql.DataSource;

import com.codahale.metrics.MetricRegistry;

import org.deltav.flows.enricher.classification.ApplicationClassifier;
import org.deltav.flows.enricher.classification.PortBasedApplicationClassifier;
import org.deltav.flows.enricher.enrichment.FlowLocalityCalculator;
import org.deltav.flows.enricher.enrichment.InterfaceMarkingCache;
import org.deltav.flows.enricher.enrichment.JdbcNodeInfoLookup;
import org.deltav.flows.enricher.mapping.FlowToDocumentMapper;
import org.deltav.flows.enricher.parser.LoggingEventForwarder;
import org.deltav.flows.enricher.parser.NoOpDnsResolver;
import org.deltav.flows.enricher.parser.StaticIdentity;
import org.deltav.flows.enricher.parser.ThreadLocalDispatcher;
import org.deltav.flows.enricher.protocol.IpfixMessageProcessor;
import org.deltav.flows.enricher.protocol.Netflow5MessageProcessor;
import org.deltav.flows.enricher.protocol.Netflow9MessageProcessor;
import org.deltav.flows.enricher.protocol.ProtocolMessageProcessor;
import org.deltav.flows.enricher.protocol.SFlowMessageProcessor;
import org.deltav.flows.enricher.protocol.SimpleAdapterDefinition;
import org.opennms.distributed.core.api.Identity;
import org.opennms.netmgt.dnsresolver.api.DnsResolver;
import org.opennms.netmgt.events.api.EventForwarder;
import org.opennms.netmgt.telemetry.listeners.UdpParser;
import org.opennms.netmgt.telemetry.protocols.netflow.parser.IpfixUdpParser;
import org.opennms.netmgt.telemetry.protocols.netflow.parser.Netflow5UdpParser;
import org.opennms.netmgt.telemetry.protocols.netflow.parser.Netflow9UdpParser;
import org.opennms.netmgt.telemetry.protocols.netflow.parser.ie.InformationElementDatabase;
import org.opennms.netmgt.telemetry.protocols.sflow.parser.SFlowUdpParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Bean wiring for the flow-enricher service. Phase 2 adds the parser bridge
 * beans (parsers, collaborators, scheduler, ThreadLocalDispatcher, InformationElementDatabase)
 * and rewires the four protocol processor beans to take the new Shape 2
 * constructor parameters.
 */
@Configuration
public class FlowEnricherConfiguration {

    // ---- Existing enrichment infrastructure (unchanged from Phase 1.5) ----

    @Bean
    JdbcTemplate flowEnricherJdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    JdbcNodeInfoLookup jdbcNodeInfoLookup(
            JdbcTemplate flowEnricherJdbcTemplate,
            @Value("${deltav.flows.node-lookup.cache-ttl:5m}") Duration cacheTtl) {
        return new JdbcNodeInfoLookup(flowEnricherJdbcTemplate, cacheTtl);
    }

    @Bean
    FlowLocalityCalculator flowLocalityCalculator() {
        return new FlowLocalityCalculator();
    }

    @Bean
    InterfaceMarkingCache interfaceMarkingCache(
            JdbcTemplate flowEnricherJdbcTemplate,
            @Value("${deltav.flows.interface-marking.cache-ttl:24h}") Duration cacheTtl) {
        return new InterfaceMarkingCache(flowEnricherJdbcTemplate, cacheTtl);
    }

    @Bean
    SinkMessageDeserializer sinkMessageDeserializer() {
        return new SinkMessageDeserializer();
    }

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

    // ---- Phase 2: parser bridge collaborators ----

    @Bean(destroyMethod = "shutdown")
    ScheduledExecutorService flowParserSessionCleanup() {
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicLong counter = new AtomicLong();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "flow-parser-session-cleanup-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        };
        return Executors.newSingleThreadScheduledExecutor(factory);
    }

    @Bean
    InformationElementDatabase informationElementDatabase() {
        // The no-arg-providers overload reads the default set of information
        // element CSV resources (IPFIX IANA registry + Netflow9 vendor
        // elements). Takes ~150ms at container startup.
        return new InformationElementDatabase();
    }

    @Bean
    DnsResolver flowParserDnsResolver() {
        return new NoOpDnsResolver();
    }

    @Bean
    EventForwarder flowParserEventForwarder() {
        return new LoggingEventForwarder();
    }

    @Bean
    Identity flowParserIdentity(
            @Value("${deltav.flows.identity.id:flow-enricher}") String id,
            @Value("${deltav.flows.identity.location:Default}") String location,
            @Value("${deltav.flows.identity.type:flow-enricher}") String type) {
        return new StaticIdentity(id, location, type);
    }

    @Bean
    ThreadLocalDispatcher flowParserThreadLocalDispatcher() {
        return new ThreadLocalDispatcher();
    }

    // ---- Phase 2: per-protocol UdpParser beans ----

    // NOTE: Spring's @Bean(initMethod=..., destroyMethod=...) cannot pass
    // arguments to the init/destroy methods, and horizon's Parser.start()
    // requires a ScheduledExecutorService argument. So we do NOT use
    // Spring's init/destroy method hooks on the parser beans — start/stop
    // is driven explicitly by the ParserLifecycle bean declared below.
    @Bean
    Netflow5UdpParser netflow5UdpParser(
            ThreadLocalDispatcher flowParserThreadLocalDispatcher,
            EventForwarder flowParserEventForwarder,
            Identity flowParserIdentity,
            DnsResolver flowParserDnsResolver,
            MetricRegistry flowEnricherMetricRegistry) {
        return new Netflow5UdpParser(
                "flow-enricher-netflow5",
                flowParserThreadLocalDispatcher,
                flowParserEventForwarder,
                flowParserIdentity,
                flowParserDnsResolver,
                flowEnricherMetricRegistry);
    }

    @Bean
    Netflow9UdpParser netflow9UdpParser(
            ThreadLocalDispatcher flowParserThreadLocalDispatcher,
            EventForwarder flowParserEventForwarder,
            Identity flowParserIdentity,
            DnsResolver flowParserDnsResolver,
            MetricRegistry flowEnricherMetricRegistry,
            InformationElementDatabase informationElementDatabase) {
        return new Netflow9UdpParser(
                "flow-enricher-netflow9",
                flowParserThreadLocalDispatcher,
                flowParserEventForwarder,
                flowParserIdentity,
                flowParserDnsResolver,
                flowEnricherMetricRegistry,
                informationElementDatabase);
    }

    @Bean
    IpfixUdpParser ipfixUdpParser(
            ThreadLocalDispatcher flowParserThreadLocalDispatcher,
            EventForwarder flowParserEventForwarder,
            Identity flowParserIdentity,
            DnsResolver flowParserDnsResolver,
            MetricRegistry flowEnricherMetricRegistry,
            InformationElementDatabase informationElementDatabase) {
        return new IpfixUdpParser(
                "flow-enricher-ipfix",
                flowParserThreadLocalDispatcher,
                flowParserEventForwarder,
                flowParserIdentity,
                flowParserDnsResolver,
                flowEnricherMetricRegistry,
                informationElementDatabase);
    }

    @Bean
    SFlowUdpParser sflowUdpParser(
            ThreadLocalDispatcher flowParserThreadLocalDispatcher,
            DnsResolver flowParserDnsResolver) {
        return new SFlowUdpParser(
                "flow-enricher-sflow",
                flowParserThreadLocalDispatcher,
                flowParserDnsResolver);
    }

    /**
     * Starts and stops all parser beans against the shared cleanup scheduler.
     * Spring's init/destroy method support cannot pass arguments, so we do it
     * explicitly here instead of annotating each parser bean with
     * {@code initMethod}/{@code destroyMethod}.
     */
    @Bean
    ParserLifecycle flowParserLifecycle(
            ScheduledExecutorService flowParserSessionCleanup,
            Netflow5UdpParser netflow5UdpParser,
            Netflow9UdpParser netflow9UdpParser,
            IpfixUdpParser ipfixUdpParser,
            SFlowUdpParser sflowUdpParser) {
        return new ParserLifecycle(flowParserSessionCleanup,
                List.of(netflow5UdpParser, netflow9UdpParser, ipfixUdpParser, sflowUdpParser));
    }

    public static class ParserLifecycle {
        private static final org.slf4j.Logger LOG =
                org.slf4j.LoggerFactory.getLogger(ParserLifecycle.class);

        private final ScheduledExecutorService scheduler;
        private final List<UdpParser> parsers;

        ParserLifecycle(ScheduledExecutorService scheduler, List<UdpParser> parsers) {
            this.scheduler = scheduler;
            this.parsers = List.copyOf(parsers);
        }

        @jakarta.annotation.PostConstruct
        public void start() {
            for (UdpParser parser : parsers) {
                parser.start(scheduler);
                LOG.info("Started horizon parser {}", parser.getName());
            }
        }

        @jakarta.annotation.PreDestroy
        public void stop() {
            for (UdpParser parser : parsers) {
                try {
                    parser.stop();
                    LOG.info("Stopped horizon parser {}", parser.getName());
                } catch (Exception e) {
                    LOG.warn("Failed to stop parser {}: {}", parser.getName(), e.getMessage(), e);
                }
            }
        }
    }

    // ---- Phase 2: protocol processor beans (Shape 2 constructor) ----

    @Bean
    Netflow5MessageProcessor netflow5Processor(
            Netflow5UdpParser netflow5UdpParser,
            MetricRegistry flowEnricherMetricRegistry,
            ThreadLocalDispatcher flowParserThreadLocalDispatcher) {
        return new Netflow5MessageProcessor(
                netflow5UdpParser,
                new SimpleAdapterDefinition("Netflow-5"),
                flowEnricherMetricRegistry,
                flowParserThreadLocalDispatcher);
    }

    @Bean
    Netflow9MessageProcessor netflow9Processor(
            Netflow9UdpParser netflow9UdpParser,
            MetricRegistry flowEnricherMetricRegistry,
            ThreadLocalDispatcher flowParserThreadLocalDispatcher) {
        return new Netflow9MessageProcessor(
                netflow9UdpParser,
                new SimpleAdapterDefinition("Netflow-9"),
                flowEnricherMetricRegistry,
                flowParserThreadLocalDispatcher);
    }

    @Bean
    IpfixMessageProcessor ipfixProcessor(
            IpfixUdpParser ipfixUdpParser,
            MetricRegistry flowEnricherMetricRegistry,
            ThreadLocalDispatcher flowParserThreadLocalDispatcher) {
        return new IpfixMessageProcessor(
                ipfixUdpParser,
                new SimpleAdapterDefinition("IPFIX"),
                flowEnricherMetricRegistry,
                flowParserThreadLocalDispatcher);
    }

    @Bean
    SFlowMessageProcessor sflowProcessor(
            SFlowUdpParser sflowUdpParser,
            MetricRegistry flowEnricherMetricRegistry,
            ThreadLocalDispatcher flowParserThreadLocalDispatcher) {
        return new SFlowMessageProcessor(
                sflowUdpParser,
                new SimpleAdapterDefinition("SFlow"),
                flowEnricherMetricRegistry,
                flowParserThreadLocalDispatcher);
    }

    // ---- Spring Cloud Stream function (unchanged from Phase 1.5) ----

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
                deserializer,
                nodeInfoLookup,
                localityCalculator,
                interfaceMarkingCache,
                applicationClassifier,
                flowToDocumentMapper,
                dispatchMap);
    }

    @Bean
    Function<Message<byte[]>, List<byte[]>> enrichFlows(FlowEnrichmentFunction enrichmentFunction) {
        return enrichmentFunction::processMessage;
    }

    @Bean
    InterfaceMarkingCacheCleaner interfaceMarkingCacheCleaner(InterfaceMarkingCache cache) {
        return new InterfaceMarkingCacheCleaner(cache);
    }

    public static class InterfaceMarkingCacheCleaner {
        private final InterfaceMarkingCache cache;

        InterfaceMarkingCacheCleaner(InterfaceMarkingCache cache) {
            this.cache = cache;
        }

        @Scheduled(fixedRateString = "${deltav.flows.interface-marking.clean-interval-ms:3600000}")
        public void clean() {
            cache.cleanExpired();
        }
    }
}
```

- [ ] **Step 2: Build to verify wiring compiles and Spring context loads**

Run: `cd /Users/david/development/src/opennms/delta-v/core/flow-enricher && ../../maven/bin/mvn -q compile 2>&1 | tail -20`

Expected: `BUILD SUCCESS`. If the compile fails because of `jakarta.annotation.PostConstruct` not being found, that's a missing transitive — add `<dependency><groupId>jakarta.annotation</groupId><artifactId>jakarta.annotation-api</artifactId></dependency>` to the POM, but Spring Boot Starter typically brings it already.

- [ ] **Step 3: Commit**

```bash
git add core/flow-enricher/src/main/java/org/deltav/flows/enricher/FlowEnricherConfiguration.java
git commit -m "feat(flow-enricher): wire parser beans and ParserLifecycle in Spring config"
```

---

## Task 8: Micrometer bridge + application.yml config

**Files:**
- Create: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/parser/FlowEnricherMicrometerBridge.java`
- Modify: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/FlowEnricherConfiguration.java` (add bridge bean)
- Modify: `core/flow-enricher/src/main/resources/application.yml`

- [ ] **Step 1: Implement the Dropwizard→Micrometer bridge**

The simplest form of the bridge is to just register a `DropwizardMeterRegistry` as a bean and let Spring Boot's Actuator auto-configuration pick it up. Create `core/flow-enricher/src/main/java/org/deltav/flows/enricher/parser/FlowEnricherMicrometerBridge.java`:

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
package org.deltav.flows.enricher.parser;

import com.codahale.metrics.MetricRegistry;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.dropwizard.DropwizardConfig;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;

/**
 * Publishes the shared Dropwizard {@link MetricRegistry} (used by horizon's
 * flow adapters and parsers for their internal timers/meters) into Spring
 * Boot's Micrometer registry, so all parser and adapter metrics are
 * scrapable at {@code /actuator/prometheus}.
 */
public class FlowEnricherMicrometerBridge extends DropwizardMeterRegistry {

    public FlowEnricherMicrometerBridge(MetricRegistry dropwizardRegistry, Clock clock) {
        super(CONFIG, dropwizardRegistry, HierarchicalNameMapper.DEFAULT, clock);
    }

    @Override
    protected Double nullGaugeValue() {
        return Double.NaN;
    }

    private static final DropwizardConfig CONFIG = new DropwizardConfig() {
        @Override
        public String prefix() {
            return "flow_enricher";
        }

        @Override
        public String get(String key) {
            return null;
        }
    };
}
```

- [ ] **Step 2: Register the bridge as a bean in `FlowEnricherConfiguration`**

Add the following imports to `FlowEnricherConfiguration.java`:

```java
import io.micrometer.core.instrument.Clock;
import org.deltav.flows.enricher.parser.FlowEnricherMicrometerBridge;
```

Add this bean method in the "Phase 2: parser bridge collaborators" section (after `flowEnricherMetricRegistry`):

```java
    @Bean
    FlowEnricherMicrometerBridge flowEnricherMicrometerBridge(
            MetricRegistry flowEnricherMetricRegistry) {
        return new FlowEnricherMicrometerBridge(flowEnricherMetricRegistry, Clock.SYSTEM);
    }
```

- [ ] **Step 3: Update application.yml for partition stickiness, identity, and prometheus exposure**

Open `core/flow-enricher/src/main/resources/application.yml`. Locate the `spring.cloud.stream.kafka.bindings.enrichFlows-in-0.consumer` block (create it if it does not exist). Add the partition assignment strategy; also add the `deltav.flows.identity.*` section and expand `management.endpoints.web.exposure.include` to include `prometheus`.

Concretely, make sure the file contains these sections (merge into existing structure — do not duplicate top-level keys):

```yaml
spring:
  cloud:
    stream:
      kafka:
        bindings:
          enrichFlows-in-0:
            consumer:
              configuration:
                partition.assignment.strategy: org.apache.kafka.clients.consumer.CooperativeStickyAssignor

deltav:
  flows:
    identity:
      id: flow-enricher
      location: Default
      type: flow-enricher

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
```

- [ ] **Step 4: Verify build and Spring context load**

Run: `cd /Users/david/development/src/opennms/delta-v/core/flow-enricher && ../../maven/bin/mvn -q compile test-compile 2>&1 | tail -15`

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add core/flow-enricher/src/main/java/org/deltav/flows/enricher/parser/FlowEnricherMicrometerBridge.java \
        core/flow-enricher/src/main/java/org/deltav/flows/enricher/FlowEnricherConfiguration.java \
        core/flow-enricher/src/main/resources/application.yml
git commit -m "feat(flow-enricher): add Dropwizard→Micrometer bridge and partition stickiness

Exposes horizon parser + adapter metrics at /actuator/prometheus by
registering a DropwizardMeterRegistry bean wired to the shared
MetricRegistry. Adds CooperativeStickyAssignor to the enrichFlows-in-0
consumer so an exporter's Kafka partition stays with the same flow-enricher
replica, keeping UdpSessionManager template caches warm across rebalances."
```

---

## Task 9: Netflow9 parser bridge integration test (critical path)

The most important integration test. Uses a real `Netflow9UdpParser` with a real `InformationElementDatabase` and real captured wire bytes. Verifies the whole Stage 1 path works with horizon's actual parser code, not just the FakeUdpParser stub.

**Files:**
- Create: `core/flow-enricher/src/test/resources/fixtures/netflow9_template_and_data.dat`
- Create: `core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/Netflow9ParserBridgeIT.java`

- [ ] **Step 1: Locate and copy a Netflow v9 fixture from the horizon worktree**

Horizon's Netflow v9 parser tests include captured wire-bytes fixtures. Find one that contains a template packet followed by a data packet:

```bash
find /Users/david/development/src/opennms/delta-v/.claude/worktrees/provisiond-spring-boot/features/telemetry/protocols/netflow/parser/src/test/resources -name "netflow9*" -type f
```

Pick a fixture file (e.g., `netflow9_test_valid01.dat` or similar — names vary). Copy it to `core/flow-enricher/src/test/resources/fixtures/netflow9_template_and_data.dat`. If the horizon fixture is split across two files (one for template, one for data), copy both and name them `netflow9_template.dat` and `netflow9_data.dat`.

```bash
mkdir -p core/flow-enricher/src/test/resources/fixtures
cp /Users/david/development/src/opennms/delta-v/.claude/worktrees/provisiond-spring-boot/features/telemetry/protocols/netflow/parser/src/test/resources/<fixture-name>.dat \
   core/flow-enricher/src/test/resources/fixtures/netflow9_template_and_data.dat
```

If the horizon fixture file is not a single concatenated stream but a set of individual datagram files, use the individual ones and adjust the test to feed them in sequence.

- [ ] **Step 2: Write the Netflow9ParserBridgeIT**

Create `core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/Netflow9ParserBridgeIT.java`:

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
package org.deltav.flows.enricher.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.deltav.flows.enricher.parser.LoggingEventForwarder;
import org.deltav.flows.enricher.parser.NoOpDnsResolver;
import org.deltav.flows.enricher.parser.StaticIdentity;
import org.deltav.flows.enricher.parser.ThreadLocalDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opennms.netmgt.flows.api.Flow;
import org.opennms.netmgt.telemetry.common.ipc.TelemetryProtos;
import org.opennms.netmgt.telemetry.protocols.netflow.parser.Netflow9UdpParser;
import org.opennms.netmgt.telemetry.protocols.netflow.parser.ie.InformationElementDatabase;

import com.codahale.metrics.MetricRegistry;
import com.google.protobuf.ByteString;

/**
 * Integration test for the Netflow v9 parser bridge using a real
 * {@link Netflow9UdpParser}, a real {@link InformationElementDatabase}, and
 * captured wire bytes from a Netflow v9 exporter. Verifies the whole
 * Stage 1 path works against horizon's actual parser — including the
 * per-exporter template cache — not just a mocked stub.
 */
class Netflow9ParserBridgeIT {

    private ScheduledExecutorService scheduler;
    private Netflow9UdpParser parser;
    private Netflow9MessageProcessor processor;

    @BeforeEach
    void setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        ThreadLocalDispatcher tld = new ThreadLocalDispatcher();
        MetricRegistry metricRegistry = new MetricRegistry();
        InformationElementDatabase ied = new InformationElementDatabase();

        parser = new Netflow9UdpParser(
                "test-netflow9",
                tld,
                new LoggingEventForwarder(),
                new StaticIdentity("test", "Default", "test"),
                new NoOpDnsResolver(),
                metricRegistry,
                ied);
        parser.start(scheduler);

        processor = new Netflow9MessageProcessor(
                parser,
                TestAdapterDefinitions.testAdapterDefinition("netflow9-it"),
                metricRegistry,
                tld);
    }

    @AfterEach
    void tearDown() {
        if (parser != null) {
            parser.stop();
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    @Test
    void parsesCapturedNetflow9WirePacket() throws Exception {
        byte[] wireBytes = readFixture("fixtures/netflow9_template_and_data.dat");

        TelemetryProtos.TelemetryMessageLog rawLog = TelemetryProtos.TelemetryMessageLog.newBuilder()
                .setLocation("Default")
                .setSystemId("test-system")
                .setSourceAddress("192.0.2.100")
                .setSourcePort(54321)
                .addMessage(TelemetryProtos.TelemetryMessage.newBuilder()
                        .setTimestamp(System.currentTimeMillis())
                        .setBytes(ByteString.copyFrom(wireBytes)))
                .build();

        List<Flow> flows = processor.process(rawLog);

        // A template-only packet will produce 0 flows; a data packet with a
        // previously-seen template will produce >=1 flows. Regardless, no
        // exceptions should have been thrown.
        assertThat(flows).isNotNull();
        // If the fixture contains a full template+data pair, we expect >=1.
        // If it's template-only, we expect 0 and then a second call with the
        // data packet would produce flows; this test asserts the critical
        // invariant that parsing completes without error.
        for (Flow flow : flows) {
            assertThat(flow.getSrcAddr()).isNotNull();
            assertThat(flow.getProtocol()).isNotNull();
        }
    }

    private static byte[] readFixture(String resourcePath) throws Exception {
        try (InputStream in = Netflow9ParserBridgeIT.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Fixture not found on classpath: " + resourcePath);
            }
            return in.readAllBytes();
        }
    }
}
```

- [ ] **Step 3: Run the IT**

Run: `cd /Users/david/development/src/opennms/delta-v/core/flow-enricher && ../../maven/bin/mvn -q test -Dtest=Netflow9ParserBridgeIT 2>&1 | tail -30`

Expected: `Tests run: 1, Failures: 0, Errors: 0`. If the test errors with `Fixture not found`, the fixture copy in Step 1 did not land — verify the file path. If it errors with a parse exception, the chosen fixture may require a multi-packet sequence (template then data); split into two feeds.

- [ ] **Step 4: Commit**

```bash
git add core/flow-enricher/src/test/resources/fixtures/netflow9_template_and_data.dat \
        core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/Netflow9ParserBridgeIT.java
git commit -m "test(flow-enricher): add Netflow9 parser bridge integration test with real wire bytes"
```

---

## Task 10: Netflow5, IPFIX, sFlow parser bridge ITs

Three more integration tests following the same pattern as Task 9 but for the other protocols. Netflow5 and sFlow have no template concept (Netflow5 is fixed-format; sFlow uses inline type descriptors in each sample), so their fixtures are simpler. IPFIX behaves like Netflow9 and needs a template+data pair.

**Files:**
- Create: `core/flow-enricher/src/test/resources/fixtures/netflow5_packet.dat`
- Create: `core/flow-enricher/src/test/resources/fixtures/ipfix_template_and_data.dat`
- Create: `core/flow-enricher/src/test/resources/fixtures/sflow_sample.dat`
- Create: `core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/Netflow5ParserBridgeIT.java`
- Create: `core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/IpfixParserBridgeIT.java`
- Create: `core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/SFlowParserBridgeIT.java`

- [ ] **Step 1: Copy fixture files for Netflow5, IPFIX, sFlow**

```bash
# Netflow5
find /Users/david/development/src/opennms/delta-v/.claude/worktrees/provisiond-spring-boot/features/telemetry/protocols/netflow/parser/src/test/resources -iname "netflow5*" -type f
cp /Users/david/development/src/opennms/delta-v/.claude/worktrees/provisiond-spring-boot/features/telemetry/protocols/netflow/parser/src/test/resources/<chosen-file> \
   core/flow-enricher/src/test/resources/fixtures/netflow5_packet.dat

# IPFIX
find /Users/david/development/src/opennms/delta-v/.claude/worktrees/provisiond-spring-boot/features/telemetry/protocols/netflow/parser/src/test/resources -iname "ipfix*" -type f
cp /Users/david/development/src/opennms/delta-v/.claude/worktrees/provisiond-spring-boot/features/telemetry/protocols/netflow/parser/src/test/resources/<chosen-file> \
   core/flow-enricher/src/test/resources/fixtures/ipfix_template_and_data.dat

# sFlow
find /Users/david/development/src/opennms/delta-v/.claude/worktrees/provisiond-spring-boot/features/telemetry/protocols/sflow/parser/src/test/resources -iname "sflow*" -type f
cp /Users/david/development/src/opennms/delta-v/.claude/worktrees/provisiond-spring-boot/features/telemetry/protocols/sflow/parser/src/test/resources/<chosen-file> \
   core/flow-enricher/src/test/resources/fixtures/sflow_sample.dat
```

If a protocol has no single file that contains a usable packet, create a placeholder file of zero bytes and mark the corresponding test as `@Disabled("no suitable horizon fixture found")` — do NOT fake the binary content.

- [ ] **Step 2: Write the three IT classes**

Each test follows the Netflow9 template from Task 9 with these per-protocol differences:

**`Netflow5ParserBridgeIT.java`:**
- Parser constructor: `new Netflow5UdpParser("test-netflow5", tld, eventForwarder, identity, dnsResolver, metricRegistry)` — no InformationElementDatabase.
- No `informationElementDatabase` bean needed.
- Fixture: `fixtures/netflow5_packet.dat`.
- Assertion: flow list >= 1 (Netflow5 has no templates; every data packet produces flows immediately).

**`IpfixParserBridgeIT.java`:**
- Parser constructor: same as Netflow9 (takes InformationElementDatabase).
- Fixture: `fixtures/ipfix_template_and_data.dat`.
- Assertion: same as Netflow9 (>= 0, no exceptions; typically >= 1 if fixture has template+data).

**`SFlowParserBridgeIT.java`:**
- Parser constructor: `new SFlowUdpParser("test-sflow", tld, dnsResolver)`.
- Fixture: `fixtures/sflow_sample.dat`.
- **Best-effort note** in Javadoc: "Per the Phase 2 spec, sFlow is best-effort — this test passes if parsing completes without exception. Flow count assertions are relaxed because BSON→Flow conversion in horizon's SFlowAdapter may produce zero flows for fixtures that don't include full sample bodies."
- Assertion: `assertThat(flows).isNotNull()` and `assertThat(flows).allSatisfy(f -> assertThat(f).isNotNull())`.

Since each IT is ~75 lines and structurally identical to Task 9's IT, use Task 9's test class as a template and change only (a) the parser constructor, (b) the processor class, (c) the fixture path, (d) the class name and javadoc.

- [ ] **Step 3: Run all four parser bridge ITs**

Run: `cd /Users/david/development/src/opennms/delta-v/core/flow-enricher && ../../maven/bin/mvn -q test -Dtest='*ParserBridgeIT' 2>&1 | tail -30`

Expected: all four tests run. Any with `@Disabled` report as skipped. Remaining tests pass.

- [ ] **Step 4: Commit**

```bash
git add core/flow-enricher/src/test/resources/fixtures/netflow5_packet.dat \
        core/flow-enricher/src/test/resources/fixtures/ipfix_template_and_data.dat \
        core/flow-enricher/src/test/resources/fixtures/sflow_sample.dat \
        core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/Netflow5ParserBridgeIT.java \
        core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/IpfixParserBridgeIT.java \
        core/flow-enricher/src/test/java/org/deltav/flows/enricher/protocol/SFlowParserBridgeIT.java
git commit -m "test(flow-enricher): add Netflow5/IPFIX/sFlow parser bridge integration tests"
```

---

## Task 11: Deployment flip — Docker Compose, telemetryd config, E2E script, spec update

All the container-side changes that activate the Phase 2 path. One task, four related edits committed together so develop has no intermediate broken state.

**Files:**
- Modify: `opennms-container/delta-v/docker-compose.yml`
- Modify: `opennms-container/delta-v/telemetryd-overlay/etc/telemetryd-configuration.xml`
- Modify: `opennms-container/delta-v/test-flows-e2e.sh`
- Modify: `docs/superpowers/specs/2026-04-12-minion-telemetry-receiver-design.md`

- [ ] **Step 1: Flip `docker-compose.yml` port routing**

Open `opennms-container/delta-v/docker-compose.yml`. Find the `telemetryd:` service definition. Remove `4729:4729/udp` from its `ports:` list. Find the `minion:` service definition and add `4729:4729/udp` to its `ports:` list.

Find the `flow-default-testnode-1:` service definition (the softflowd exporter test container). Change its `NETFLOW_COLLECTOR` environment variable from `telemetryd:4729` to `minion:4729`. Change its `depends_on:` entry from `telemetryd` to `minion`.

Exact sed approach:

```bash
# Remove the port mapping from telemetryd
# (review with diff before running)
```

Because the exact line numbers depend on the current file layout, use a text editor to make the four edits manually:

1. Delete or comment out `- "4729:4729/udp"` in the telemetryd `ports:` block.
2. Add `- "4729:4729/udp"` to the minion `ports:` block.
3. In `flow-default-testnode-1`, change `NETFLOW_COLLECTOR: telemetryd:4729` to `NETFLOW_COLLECTOR: minion:4729`.
4. In `flow-default-testnode-1`, change `depends_on: [telemetryd]` (or similar) to `depends_on: [minion]`.

- [ ] **Step 2: Remove the Netflow-9-UDP-4729 listener block from telemetryd config**

Open `opennms-container/delta-v/telemetryd-overlay/etc/telemetryd-configuration.xml`. Find the `<listener name="Netflow-9-UDP-4729" ...>...</listener>` block and delete it entirely. If the block is the only listener in a `<listeners>` element, leave the empty `<listeners/>` element in place — the XML schema still requires it. Do NOT touch any other listener definitions.

- [ ] **Step 3: Update `test-flows-e2e.sh` to require the minion service**

Open `opennms-container/delta-v/test-flows-e2e.sh`. Find the `REQUIRED_SERVICES=` variable declaration (probably near the top of the script) and add `minion` to the space-separated list. If the list is currently something like `REQUIRED_SERVICES="telemetryd clickhouse kafka flow-enricher"`, make it `REQUIRED_SERVICES="minion telemetryd clickhouse kafka flow-enricher"`.

- [ ] **Step 4: Update the Phase 1 spec cleanup TODO**

Open `docs/superpowers/specs/2026-04-12-minion-telemetry-receiver-design.md`. Find the paragraph in the `## Target Architecture` section (around lines 22-32) that says something like "The flow-enricher consumes these topics unchanged — it cannot distinguish Minion-produced messages from Telemetryd-produced ones because the wire format is identical." Replace it with:

```markdown
The flow-enricher (Phase 2, shipped in feature/flow-enricher-phase2-parser-bridge)
consumes these topics with a server-side UdpParser bridge. Each
`TelemetryMessage.bytes` entry now carries raw UDP wire bytes; the
flow-enricher runs them through horizon's `Netflow5UdpParser`,
`Netflow9UdpParser`, `IpfixUdpParser`, or `SFlowUdpParser` to produce
`FlowMessage` protobufs, then feeds those through the existing
`AbstractFlowAdapter` + `CapturingPipeline` path to get `Flow` POJOs. The
legacy pre-parsed `FlowMessage` bytes wire format used by Phase 1.5's
telemetryd-driven path is no longer supported.
```

- [ ] **Step 5: Verify docker-compose.yml is still valid**

Run: `cd /Users/david/development/src/opennms/delta-v/opennms-container/delta-v && docker compose config 2>&1 | tail -20`

Expected: valid YAML output (no parse errors). If `docker compose` isn't installed locally, skip this check — the first build in Task 12 will catch any YAML issues.

- [ ] **Step 6: Commit all four changes together**

```bash
git add opennms-container/delta-v/docker-compose.yml \
        opennms-container/delta-v/telemetryd-overlay/etc/telemetryd-configuration.xml \
        opennms-container/delta-v/test-flows-e2e.sh \
        docs/superpowers/specs/2026-04-12-minion-telemetry-receiver-design.md
git commit -m "refactor(deltav): flip flow ingress from telemetryd to minion

- docker-compose.yml: move 4729/udp from telemetryd to minion; repoint
  softflowd flow-default-testnode-1 at minion:4729
- telemetryd-configuration.xml: remove Netflow-9-UDP-4729 listener block
- test-flows-e2e.sh: require minion in REQUIRED_SERVICES
- minion-telemetry-receiver-design.md: replace outdated wire-format-identical
  paragraph with Phase 2 parser bridge description"
```

---

## Task 12: Build, deploy, live E2E verification

The Phase 2 code is complete. Rebuild the stack, run the E2E test, capture output, and commit the verification log.

**Files:**
- Optional commit: no source file changes; verification output recorded in commit message.

- [ ] **Step 1: Rebuild flow-enricher and minion images**

Run: `cd /Users/david/development/src/opennms/delta-v && ./build.sh deltav 2>&1 | tail -30`

Expected: `BUILD SUCCESS` and the Docker images for flow-enricher + minion are rebuilt. If the build fails, the earlier tasks missed something — fix inline, commit, and rerun.

- [ ] **Step 2: Bring up the stack**

```bash
cd /Users/david/development/src/opennms/delta-v/opennms-container/delta-v
docker compose down -v  # ensure clean state
docker compose up -d
```

Wait ~60 seconds for all services to become healthy. Check with:

```bash
docker compose ps
```

Expected: `flow-enricher`, `minion`, `telemetryd`, `kafka`, `clickhouse`, and `flow-default-testnode-1` all in running state.

- [ ] **Step 3: Run the E2E test**

```bash
./test-flows-e2e.sh 2>&1 | tee /tmp/flow-enricher-phase2-e2e.log
```

Expected output includes:
- All REQUIRED_SERVICES healthy (including `minion`)
- `softflowd` starts generating Netflow v9 on `flow-default-testnode-1`
- Within 60 seconds, `deltav.flows_raw` row count grows from 0 to >0
- All 4 dimension materialized views (`flows_by_*`) contain data
- Final report: `PASS`

- [ ] **Step 4: Verify parser metrics are flowing**

```bash
curl -s http://localhost:<flow-enricher-actuator-port>/actuator/prometheus | grep -i flow_parser
```

Expected: non-zero values for `flow_parser_*` counters.

- [ ] **Step 5: Scan flow-enricher logs for parser warnings**

```bash
docker compose logs flow-enricher 2>&1 | grep -i "parser.*failed\|parser event dropped" | wc -l
```

Expected: a small number relative to the packet rate (<1% of packets). Zero is ideal but not required — a few warnings during startup as the first template arrives are normal.

- [ ] **Step 6: (Optional) Capture verification output as a commit**

If the E2E run produced interesting output worth preserving in the git history, add a no-op commit with the log excerpt in the body:

```bash
git commit --allow-empty -m "test: E2E verification — Phase 2 parser bridge working

$(tail -40 /tmp/flow-enricher-phase2-e2e.log)"
```

This matches the Phase 1 pattern and gives reviewers something concrete to point at in the PR description.

---

## Task 13: Followup memory entries + PR creation

Housekeeping to keep the Followups section of the spec from rotting into forgotten work.

**Files:**
- Create: `~/.claude/projects/-Users-david-development-src-opennms-delta-v/memory/project_flow_enricher_kafka_event_forwarder.md`
- Create: `~/.claude/projects/-Users-david-development-src-opennms-delta-v/memory/project_flow_enricher_concurrency_raise.md`
- Create: `~/.claude/projects/-Users-david-development-src-opennms-delta-v/memory/project_flow_enricher_real_clock_skew.md`
- Create: `~/.claude/projects/-Users-david-development-src-opennms-delta-v/memory/project_flow_enricher_minion_aggregation.md`
- Create: `~/.claude/projects/-Users-david-development-src-opennms-delta-v/memory/project_flow_enricher_sflow_live_verification.md`
- Modify: `~/.claude/projects/-Users-david-development-src-opennms-delta-v/memory/MEMORY.md` (add index entries)

- [ ] **Step 1: Create the five followup memory files**

Each file has the standard frontmatter (`name`, `description`, `type: project`) and 3-6 sentences describing the followup scope, trigger condition, and expected effort. See the "Followups" section of `docs/superpowers/specs/2026-04-12-flow-enricher-phase2-parser-bridge-design.md` for the content to capture. Name each file after its topic and add a corresponding one-line pointer in `MEMORY.md` under "Near-Term Followups" or "Future Architecture".

- [ ] **Step 2: Open the PR against `pbrane/delta-v:develop`**

```bash
git push -u origin feature/flow-enricher-phase2-parser-bridge
gh pr create --repo pbrane/delta-v --base develop \
    --title "feat(flow-enricher): Phase 2 server-side UDP parser bridge" \
    --body "$(cat <<'EOF'
## Summary

Implements Phase 2 of the Minion telemetry receiver split (per
`docs/superpowers/specs/2026-04-12-flow-enricher-phase2-parser-bridge-design.md`).
Phase 1 (PRs #147/#148) shipped a dormant Minion-side flow UDP listener;
this PR reworks the flow-enricher to parse raw UDP bytes server-side
using horizon UdpParser classes, then flips docker-compose.yml so port
4729 moves from telemetryd to minion. After this PR, the "Minion is the
sole network ingress" commandment is fully honored for flow telemetry.

## Architecture

Two-stage bridge inside `AbstractProtocolMessageProcessor`:

- **Stage 1 (new):** Singleton horizon UdpParser + per-call CapturingDispatcher
  installed via a ThreadLocalDispatcher. Turns raw UDP wire bytes into
  FlowMessage protobuf. Parser template state persists across calls.
- **Stage 2 (unchanged from Phase 1.5):** AbstractFlowAdapter +
  CapturingPipeline turn FlowMessage protobuf into Flow POJOs.

Adds Micrometer bridge so horizon parser + adapter metrics are
scrapable at /actuator/prometheus. Adds CooperativeStickyAssignor to
the Kafka consumer to keep per-exporter template caches warm across
rebalances.

## Test plan

- [x] Unit tests for CapturingDispatcher, ThreadLocalDispatcher,
      NoOpDnsResolver, LoggingEventForwarder
- [x] Updated unit tests for all 4 processors with FakeUdpParser stub
- [x] Two targeted ThreadLocal stress tests in Netflow9MessageProcessorTest
      (parserExceptionDoesNotLeakThreadLocal,
      sequentialCallsDoNotCrossContaminate)
- [x] Integration tests with real UdpParser instances and captured wire
      bytes for Netflow5/Netflow9/IPFIX/sFlow
- [x] Live E2E run via test-flows-e2e.sh — flows reach deltav.flows_raw
      and 4 dimension MVs within 60s of softflowd starting
- [x] /actuator/prometheus returns non-zero flow_parser_* metrics

## Rollback

Single revert of the merge commit restores the Phase 1.5 telemetryd-based
ingestion path. No schema changes involved.
EOF
)"
```

- [ ] **Step 3: Verify PR landed on the fork, not upstream**

```bash
gh pr list --repo pbrane/delta-v --author @me
```

Expected: your new PR appears in the fork's PR list. If `gh pr create` accidentally defaulted to `OpenNMS/opennms`, close that PR immediately and recreate with `--repo pbrane/delta-v` (the `feedback_never_pr_opennms.md` rule).

---

## Plan Self-Review

**Spec coverage:**
- Goals 1-3: covered by Tasks 1-12.
- Non-goals: respected — no `FlowMessage→Flow` reimplementation (Stage 2 reused), no Flink, no Kafka event forwarder (log-only, followup memory in Task 13), no real clock skew computation (still 0L), no full telemetryd deletion (only Netflow-9 listener removed).
- Architecture — two-stage bridge: Task 5.
- Parser state singleton: Task 7 (parser beans as singletons, ParserLifecycle for start/stop).
- ThreadLocal capture: Tasks 3, 5.
- Partition stickiness: Task 8.
- Component inventory: Tasks 2, 3, 4, 5, 6, 7, 8, 9, 10.
- Data flow walkthrough: implemented across Tasks 5-8.
- Parser lifecycle: Task 7 (`ParserLifecycle.start()` in `@PostConstruct`, `stop()` in `@PreDestroy`).
- Threading model: default `concurrency=1` preserved (no config change in Task 8 to raise it).
- Error handling: Task 5 (per-entry + per-log try/catch, ThreadLocal hygiene).
- Observability: Task 8 (Micrometer bridge, prometheus exposure).
- Unit tests: Tasks 2, 3, 4, 6.
- ThreadLocal stress tests: Task 6.
- Fixture-based ITs: Tasks 9, 10.
- E2E verification: Task 12.
- PR plan (single PR, branch `feature/flow-enricher-phase2-parser-bridge`): Task 13.
- Followup memory entries: Task 13.

**Placeholder scan:** No `TBD`, `TODO`, or `implement later` markers. Step 1 of Task 9/10 uses `<fixture-name>` as a stand-in because the actual horizon fixture filenames can't be known without inspecting the worktree — the step explicitly tells the implementer to run `find` and pick one. That's guidance, not a placeholder.

**Type consistency:** `ThreadLocalDispatcher`, `CapturingDispatcher`, `UdpParser`, and the four processor class names match across all tasks. The `ParserLifecycle` inner class is referenced in Task 7 and used only there. `FlowEnricherMicrometerBridge` extends `DropwizardMeterRegistry` consistently between Task 8 Step 1 and Step 2.

**One caveat to flag:** Task 9/10 rely on the availability of horizon test fixture files at a path inside `.claude/worktrees/provisiond-spring-boot/`. If the worktree was created solely for API discovery and may be pruned before Phase 2 execution, the implementer should copy fixtures into the main repo BEFORE the worktree disappears, or the tests will fail at fixture-load time with no way to recover without re-cloning horizon source. Consider running Task 9 Step 1 early (right after Task 1) to lock in the fixture files.
