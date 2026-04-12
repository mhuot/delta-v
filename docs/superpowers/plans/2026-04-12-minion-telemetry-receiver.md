# Minion Telemetry Receiver Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a lightweight UDP-to-Kafka flow telemetry relay to the Boot4 Minion so softflowd can export flows through the Minion instead of directly to the Telemetryd container, completing the "Minion is sole network ingress" architectural goal and unblocking the full E2E flow test.

**Architecture:** A custom Netty-based UDP listener in `core/daemon-boot-minion` receives Netflow v5/v9, IPFIX, and sFlow datagrams on a single configurable port (default 4729), detects the protocol from version header bytes, wraps raw payload in a locally-generated `TelemetryMessageLog` protobuf, and dispatches to `OpenNMS.Sink.Telemetry-*` Kafka topics via the existing Sink API. The flow-enricher consumes these topics unchanged — wire format is byte-identical to horizon's version.

**Tech Stack:** Java 21, Spring Boot 4, Netty (NioDatagramChannel), Protobuf 3.25.5, `protobuf-maven-plugin` 0.6.1, the existing Sink API (`org.opennms.core.ipc.sink.api`) via `daemon-boot-minion-common`'s `KafkaRemoteMessageDispatcherFactory`.

**Spec reference:** `docs/superpowers/specs/2026-04-12-minion-telemetry-receiver-design.md`

**Working directory:** `/Users/david/development/src/opennms/delta-v` (branch `feature/minion-telemetry-receiver` from latest `develop`)

---

## Pre-flight

- [ ] **Step 0.1: Pull latest develop and create feature branch**

```bash
git fetch origin
git checkout develop
git pull origin develop
git checkout -b feature/minion-telemetry-receiver
```

Expected: clean checkout on `feature/minion-telemetry-receiver` with develop at the tip.

---

## Task 1: Add protobuf plugin and telemetry.proto to daemon-boot-minion

**Files:**
- Create: `core/daemon-boot-minion/src/main/proto/telemetry.proto`
- Modify: `core/daemon-boot-minion/pom.xml`

**Why:** The `FlowUdpListener` needs `TelemetryMessageLog` and `TelemetryMessage` protobuf builder classes to construct outbound Sink messages. The `.proto` file is tiny (two messages, six fields total) and the `protobuf-maven-plugin` handles code generation at `generate-sources` time. Generated classes land in `target/generated-sources/protobuf/java/org/deltav/minion/telemetry/proto/TelemetryProtos.java`.

- [ ] **Step 1.1: Create the proto source file**

Create `core/daemon-boot-minion/src/main/proto/telemetry.proto` with this exact content (the wire format matches horizon's `org.opennms.netmgt.telemetry.common.ipc.TelemetryProtos` byte-for-byte; only the Java package differs):

```protobuf
// Copyright (C) 2026 BeaconStrategists, Inc.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published
// by the Free Software Foundation, either version 3 of the License,
// or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.

syntax = "proto2";
option java_package = "org.deltav.minion.telemetry.proto";
option java_outer_classname = "TelemetryProtos";

// Wire-format-identical to
// org.opennms.netmgt.telemetry.common.ipc.TelemetryProtos. Protobuf
// decoding matches by field tag, not Java package, so the flow-enricher
// parses messages produced by this class without modification.

message TelemetryMessage {
    required uint64 timestamp = 1;
    required bytes bytes = 2;
}

message TelemetryMessageLog {
    required string location = 1;
    required string system_id = 2;
    optional string source_address = 3;
    optional uint32 source_port = 4;
    repeated TelemetryMessage message = 5;
}
```

- [ ] **Step 1.2: Add the os-maven-plugin extension and protobuf-maven-plugin to `core/daemon-boot-minion/pom.xml`**

Two changes in `<build>`. First, add the extension block inside `<build>` (the same pattern used by `core/flow-enricher/pom.xml`). The daemon-boot-minion POM currently has `<build><plugins>` but no `<extensions>` section — add the extensions block immediately before the existing `<plugins>` opening tag.

Find this section in `core/daemon-boot-minion/pom.xml` (around the current build block near the bottom of the file; use `grep -n '<build>' core/daemon-boot-minion/pom.xml` to locate it):

```xml
    <build>
        <plugins>
```

Replace with:

```xml
    <build>
        <extensions>
            <extension>
                <groupId>kr.motd.maven</groupId>
                <artifactId>os-maven-plugin</artifactId>
                <version>1.7.1</version>
            </extension>
        </extensions>
        <plugins>
```

Then add a `<protoc.version>` property inside the `<properties>` block near the top of the file (locate with `grep -n '<properties>' core/daemon-boot-minion/pom.xml`):

```xml
    <properties>
        <java.version>21</java.version>
```

Replace with:

```xml
    <properties>
        <java.version>21</java.version>
        <!-- Must match protobuf-java version managed by the horizon BOM -->
        <protoc.version>3.25.5</protoc.version>
```

Finally, add the `protobuf-maven-plugin` execution to the plugins list. Locate the existing `<plugin>` block for `spring-boot-maven-plugin` and add this new `<plugin>` block immediately before the closing `</plugins>` tag in `<build>`:

```xml
            <plugin>
                <groupId>org.xolstice.maven.plugins</groupId>
                <artifactId>protobuf-maven-plugin</artifactId>
                <version>0.6.1</version>
                <configuration>
                    <protocArtifact>com.google.protobuf:protoc:${protoc.version}:exe:${os.detected.classifier}</protocArtifact>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>compile</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
```

- [ ] **Step 1.3: Verify the protobuf classes generate correctly**

Run:
```bash
./mvnw -pl :org.opennms.core.daemon-boot-minion -am -DskipTests clean compile
```

Expected: BUILD SUCCESS. After the build, verify the generated class exists:
```bash
ls core/daemon-boot-minion/target/generated-sources/protobuf/java/org/deltav/minion/telemetry/proto/TelemetryProtos.java
```

Expected: file exists.

Also verify Maven recognized the generated source directory (no "cannot find symbol" errors) by inspecting a few lines of the generated class:
```bash
grep -c "class TelemetryMessageLog" core/daemon-boot-minion/target/generated-sources/protobuf/java/org/deltav/minion/telemetry/proto/TelemetryProtos.java
```

Expected: 1 (one class declaration).

- [ ] **Step 1.4: Commit**

```bash
git add core/daemon-boot-minion/pom.xml core/daemon-boot-minion/src/main/proto/telemetry.proto
git commit -m "build(minion): add protobuf-maven-plugin and telemetry.proto

Wire-format-identical copy of horizon's telemetry.proto under a delta-v
package (org.deltav.minion.telemetry.proto). Required for the upcoming
FlowUdpListener, which builds TelemetryMessageLog envelopes locally
instead of depending on horizon's telemetry.common JAR."
```

---

## Task 2: FlowProtocol enum with version-byte detection

**Files:**
- Create: `core/daemon-boot-minion/src/main/java/org/deltav/minion/telemetry/FlowProtocol.java`
- Test: `core/daemon-boot-minion/src/test/java/org/deltav/minion/telemetry/FlowProtocolTest.java`

**Why:** The single UDP port receives all four protocols. The listener must inspect the version header and route each datagram to the correct Sink topic. This is the heart of the "single port, multiple parsers" design — keeping it in its own enum makes the detection logic testable in isolation and keeps `FlowUdpListener` focused on I/O.

- [ ] **Step 2.1: Write the failing tests first**

Create `core/daemon-boot-minion/src/test/java/org/deltav/minion/telemetry/FlowProtocolTest.java`:

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
package org.deltav.minion.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FlowProtocolTest {

    @Test
    void detectsNetflow5() {
        byte[] datagram = new byte[] { 0x00, 0x05, 0x00, 0x00 };
        assertThat(FlowProtocol.detect(datagram)).isEqualTo(FlowProtocol.NETFLOW_5);
    }

    @Test
    void detectsNetflow9() {
        byte[] datagram = new byte[] { 0x00, 0x09, 0x00, 0x00 };
        assertThat(FlowProtocol.detect(datagram)).isEqualTo(FlowProtocol.NETFLOW_9);
    }

    @Test
    void detectsIpfix() {
        byte[] datagram = new byte[] { 0x00, 0x0A, 0x00, 0x00 };
        assertThat(FlowProtocol.detect(datagram)).isEqualTo(FlowProtocol.IPFIX);
    }

    @Test
    void detectsSflowAsFourByteVersion() {
        byte[] datagram = new byte[] { 0x00, 0x00, 0x00, 0x05 };
        assertThat(FlowProtocol.detect(datagram)).isEqualTo(FlowProtocol.SFLOW);
    }

    @Test
    void returnsNullForUnknownProtocol() {
        byte[] datagram = new byte[] { 0x00, 0x01, 0x00, 0x00 };
        assertThat(FlowProtocol.detect(datagram)).isNull();
    }

    @Test
    void returnsNullForDatagramSmallerThanFourBytes() {
        assertThat(FlowProtocol.detect(new byte[] {})).isNull();
        assertThat(FlowProtocol.detect(new byte[] { 0x00 })).isNull();
        assertThat(FlowProtocol.detect(new byte[] { 0x00, 0x09 })).isNull();
        assertThat(FlowProtocol.detect(new byte[] { 0x00, 0x09, 0x00 })).isNull();
    }

    @Test
    void moduleIdHasExpectedFormat() {
        assertThat(FlowProtocol.NETFLOW_5.getSinkModuleId()).isEqualTo("Telemetry-Netflow-5");
        assertThat(FlowProtocol.NETFLOW_9.getSinkModuleId()).isEqualTo("Telemetry-Netflow-9");
        assertThat(FlowProtocol.IPFIX.getSinkModuleId()).isEqualTo("Telemetry-IPFIX");
        assertThat(FlowProtocol.SFLOW.getSinkModuleId()).isEqualTo("Telemetry-SFlow");
    }
}
```

- [ ] **Step 2.2: Run the tests — expect compile failure**

```bash
./mvnw -pl :org.opennms.core.daemon-boot-minion -Dtest=FlowProtocolTest test
```

Expected: BUILD FAILURE with "cannot find symbol: class FlowProtocol".

- [ ] **Step 2.3: Implement `FlowProtocol`**

Create `core/daemon-boot-minion/src/main/java/org/deltav/minion/telemetry/FlowProtocol.java`:

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
package org.deltav.minion.telemetry;

/**
 * Flow protocols the Minion listens for. Each protocol is identified by a
 * version field in the first few bytes of the UDP datagram and maps to a
 * Kafka Sink topic suffix consumed by the flow-enricher.
 *
 * <p>Netflow v5, Netflow v9, and IPFIX share a 2-byte version field at
 * offset 0. sFlow uses a 4-byte version field at offset 0 with value 5.
 * Since an sFlow packet's first two bytes are {@code 0x0000} (high bytes
 * of the 4-byte field), there is no collision with Netflow v5's
 * {@code 0x0005}.
 */
public enum FlowProtocol {

    NETFLOW_5("Telemetry-Netflow-5"),
    NETFLOW_9("Telemetry-Netflow-9"),
    IPFIX("Telemetry-IPFIX"),
    SFLOW("Telemetry-SFlow");

    private final String sinkModuleId;

    FlowProtocol(String sinkModuleId) {
        this.sinkModuleId = sinkModuleId;
    }

    public String getSinkModuleId() {
        return sinkModuleId;
    }

    /**
     * Returns the detected protocol or {@code null} if the datagram is too
     * small or the version header does not match a supported protocol.
     */
    public static FlowProtocol detect(byte[] datagram) {
        if (datagram == null || datagram.length < 4) {
            return null;
        }
        int version16 = ((datagram[0] & 0xFF) << 8) | (datagram[1] & 0xFF);
        switch (version16) {
            case 0x0005:
                return NETFLOW_5;
            case 0x0009:
                return NETFLOW_9;
            case 0x000A:
                return IPFIX;
            default:
                int version32 = ((datagram[0] & 0xFF) << 24)
                              | ((datagram[1] & 0xFF) << 16)
                              | ((datagram[2] & 0xFF) << 8)
                              | (datagram[3] & 0xFF);
                if (version32 == 5) {
                    return SFLOW;
                }
                return null;
        }
    }
}
```

- [ ] **Step 2.4: Run the tests — expect pass**

```bash
./mvnw -pl :org.opennms.core.daemon-boot-minion -Dtest=FlowProtocolTest test
```

Expected: BUILD SUCCESS, 7 tests passing.

- [ ] **Step 2.5: Commit**

```bash
git add core/daemon-boot-minion/src/main/java/org/deltav/minion/telemetry/FlowProtocol.java \
        core/daemon-boot-minion/src/test/java/org/deltav/minion/telemetry/FlowProtocolTest.java
git commit -m "feat(minion): add FlowProtocol enum for version-byte detection

Detects Netflow v5/v9, IPFIX, and sFlow from datagram header bytes and
maps each protocol to its Kafka Sink topic suffix. Tests cover all four
protocols, unknown versions, and edge cases for datagrams smaller than
the minimum header size."
```

---

## Task 3: FlowTelemetryMessage wrapper and FlowSinkModule

**Files:**
- Create: `core/daemon-boot-minion/src/main/java/org/deltav/minion/telemetry/FlowTelemetryMessage.java`
- Create: `core/daemon-boot-minion/src/main/java/org/deltav/minion/telemetry/FlowSinkModule.java`
- Test: `core/daemon-boot-minion/src/test/java/org/deltav/minion/telemetry/FlowSinkModuleTest.java`

**Why:** The Sink API's `SinkModule<S, T>` requires S and T to extend `org.opennms.core.ipc.sink.api.Message` (a marker interface). Our locally-generated `TelemetryProtos.TelemetryMessageLog` extends `com.google.protobuf.GeneratedMessageV3` and does not implement `Message` — so we need a thin wrapper class that holds the protobuf object and implements the marker. The `FlowSinkModule` then uses `FlowTelemetryMessage` as both S and T (no aggregation in the first cut — each datagram becomes one Kafka message; aggregation can be added later as an optimization matching horizon's `TelemetrySinkModule`).

- [ ] **Step 3.1: Write the failing test first**

Create `core/daemon-boot-minion/src/test/java/org/deltav/minion/telemetry/FlowSinkModuleTest.java`:

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
package org.deltav.minion.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import org.deltav.minion.telemetry.proto.TelemetryProtos.TelemetryMessage;
import org.deltav.minion.telemetry.proto.TelemetryProtos.TelemetryMessageLog;
import org.junit.jupiter.api.Test;

class FlowSinkModuleTest {

    private static final int QUEUE_SIZE = 10_000;
    private static final int NUM_THREADS = 4;

    private TelemetryMessageLog sampleLog() {
        return TelemetryMessageLog.newBuilder()
                .setLocation("Default")
                .setSystemId("minion-default-01")
                .setSourceAddress("192.0.2.10")
                .setSourcePort(54321)
                .addMessage(TelemetryMessage.newBuilder()
                        .setTimestamp(1_700_000_000_000L)
                        .setBytes(ByteString.copyFrom(new byte[] { 0x00, 0x09, 0x01, 0x02 }))
                        .build())
                .build();
    }

    @Test
    void moduleIdMatchesProtocol() {
        FlowSinkModule module = new FlowSinkModule(FlowProtocol.NETFLOW_9, QUEUE_SIZE, NUM_THREADS);
        assertThat(module.getId()).isEqualTo("Telemetry-Netflow-9");
    }

    @Test
    void marshalUnmarshalRoundTripPreservesAllFields() {
        FlowSinkModule module = new FlowSinkModule(FlowProtocol.NETFLOW_9, QUEUE_SIZE, NUM_THREADS);
        FlowTelemetryMessage original = new FlowTelemetryMessage(sampleLog());

        byte[] bytes = module.marshal(original);
        FlowTelemetryMessage roundTripped = module.unmarshal(bytes);

        TelemetryMessageLog expected = original.getLog();
        TelemetryMessageLog actual = roundTripped.getLog();
        assertThat(actual.getLocation()).isEqualTo(expected.getLocation());
        assertThat(actual.getSystemId()).isEqualTo(expected.getSystemId());
        assertThat(actual.getSourceAddress()).isEqualTo(expected.getSourceAddress());
        assertThat(actual.getSourcePort()).isEqualTo(expected.getSourcePort());
        assertThat(actual.getMessageCount()).isEqualTo(1);
        assertThat(actual.getMessage(0).getTimestamp()).isEqualTo(1_700_000_000_000L);
        assertThat(actual.getMessage(0).getBytes()).isEqualTo(expected.getMessage(0).getBytes());
    }

    @Test
    void marshalSingleMessageEqualsMarshalWhenNoAggregation() {
        FlowSinkModule module = new FlowSinkModule(FlowProtocol.IPFIX, QUEUE_SIZE, NUM_THREADS);
        FlowTelemetryMessage msg = new FlowTelemetryMessage(sampleLog());

        assertThat(module.marshalSingleMessage(msg)).isEqualTo(module.marshal(msg));
    }

    @Test
    void aggregationPolicyIsNull() {
        FlowSinkModule module = new FlowSinkModule(FlowProtocol.SFLOW, QUEUE_SIZE, NUM_THREADS);
        assertThat(module.getAggregationPolicy()).isNull();
    }

    @Test
    void asyncPolicyReflectsConstructorArgs() {
        FlowSinkModule module = new FlowSinkModule(FlowProtocol.NETFLOW_5, 5_000, 2);
        assertThat(module.getAsyncPolicy().getQueueSize()).isEqualTo(5_000);
        assertThat(module.getAsyncPolicy().getNumThreads()).isEqualTo(2);
        assertThat(module.getAsyncPolicy().isBlockWhenFull()).isFalse();
    }
}
```

- [ ] **Step 3.2: Run the tests — expect compile failure**

```bash
./mvnw -pl :org.opennms.core.daemon-boot-minion -Dtest=FlowSinkModuleTest test
```

Expected: BUILD FAILURE with "cannot find symbol: FlowTelemetryMessage" and "FlowSinkModule".

- [ ] **Step 3.3: Implement `FlowTelemetryMessage`**

Create `core/daemon-boot-minion/src/main/java/org/deltav/minion/telemetry/FlowTelemetryMessage.java`:

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
package org.deltav.minion.telemetry;

import java.util.Objects;

import org.deltav.minion.telemetry.proto.TelemetryProtos.TelemetryMessageLog;
import org.opennms.core.ipc.sink.api.Message;

/**
 * Thin wrapper that adapts the locally-generated protobuf
 * {@link TelemetryMessageLog} to the Sink API's {@link Message} marker
 * interface. Needed because {@link TelemetryMessageLog} extends
 * {@code com.google.protobuf.GeneratedMessageV3} and does not implement
 * the Sink API marker; horizon's hand-edited {@code TelemetryProtos.java}
 * adds the interface manually, but delta-v uses pure protoc output.
 *
 * <p>Immutable. The wrapped {@link TelemetryMessageLog} is itself
 * immutable (protobuf generated types are).
 */
public final class FlowTelemetryMessage implements Message {

    private final TelemetryMessageLog log;

    public FlowTelemetryMessage(TelemetryMessageLog log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    public TelemetryMessageLog getLog() {
        return log;
    }

    public byte[] toByteArray() {
        return log.toByteArray();
    }
}
```

- [ ] **Step 3.4: Implement `FlowSinkModule`**

Create `core/daemon-boot-minion/src/main/java/org/deltav/minion/telemetry/FlowSinkModule.java`:

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
package org.deltav.minion.telemetry;

import java.util.Objects;

import org.deltav.minion.telemetry.proto.TelemetryProtos.TelemetryMessageLog;
import org.opennms.core.ipc.sink.api.AggregationPolicy;
import org.opennms.core.ipc.sink.api.AsyncPolicy;
import org.opennms.core.ipc.sink.api.SinkModule;

import com.google.protobuf.InvalidProtocolBufferException;

/**
 * Sink module that ships per-datagram {@link FlowTelemetryMessage}
 * envelopes to Kafka topic {@code OpenNMS.Sink.Telemetry-{protocol}}.
 *
 * <p>Each UDP datagram received by {@code FlowUdpListener} becomes one
 * Kafka message. There is no aggregation in this first cut — the
 * {@link AggregationPolicy} returns {@code null}, meaning S and T are
 * the same type and the Sink dispatcher serializes each message
 * individually. Aggregation can be added later as an optimization
 * matching horizon's {@code TelemetrySinkModule}, which batches up to
 * 1000 messages per 500ms keyed by exporter address.
 *
 * <p>{@code getNumConsumerThreads()} returns {@code 0} because the
 * Minion is a producer for these topics; the consumer thread count is
 * only consulted on the consumer side (flow-enricher / Telemetryd).
 */
public class FlowSinkModule implements SinkModule<FlowTelemetryMessage, FlowTelemetryMessage> {

    private final FlowProtocol protocol;
    private final int queueSize;
    private final int numThreads;

    public FlowSinkModule(FlowProtocol protocol, int queueSize, int numThreads) {
        this.protocol = Objects.requireNonNull(protocol, "protocol");
        this.queueSize = queueSize;
        this.numThreads = numThreads;
    }

    @Override
    public String getId() {
        return protocol.getSinkModuleId();
    }

    @Override
    public int getNumConsumerThreads() {
        return 0;
    }

    @Override
    public byte[] marshal(FlowTelemetryMessage message) {
        return message.toByteArray();
    }

    @Override
    public FlowTelemetryMessage unmarshal(byte[] bytes) {
        try {
            return new FlowTelemetryMessage(TelemetryMessageLog.parseFrom(bytes));
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException("Failed to parse TelemetryMessageLog", e);
        }
    }

    @Override
    public byte[] marshalSingleMessage(FlowTelemetryMessage message) {
        return marshal(message);
    }

    @Override
    public FlowTelemetryMessage unmarshalSingleMessage(byte[] message) {
        return unmarshal(message);
    }

    @Override
    public AggregationPolicy<FlowTelemetryMessage, FlowTelemetryMessage, ?> getAggregationPolicy() {
        return null;
    }

    @Override
    public AsyncPolicy getAsyncPolicy() {
        return new AsyncPolicy() {
            @Override
            public int getQueueSize() {
                return queueSize;
            }

            @Override
            public int getNumThreads() {
                return numThreads;
            }

            @Override
            public boolean isBlockWhenFull() {
                return false;
            }
        };
    }
}
```

- [ ] **Step 3.5: Run the tests — expect pass**

```bash
./mvnw -pl :org.opennms.core.daemon-boot-minion -Dtest=FlowSinkModuleTest test
```

Expected: BUILD SUCCESS, 5 tests passing.

- [ ] **Step 3.6: Commit**

```bash
git add core/daemon-boot-minion/src/main/java/org/deltav/minion/telemetry/FlowTelemetryMessage.java \
        core/daemon-boot-minion/src/main/java/org/deltav/minion/telemetry/FlowSinkModule.java \
        core/daemon-boot-minion/src/test/java/org/deltav/minion/telemetry/FlowSinkModuleTest.java
git commit -m "feat(minion): add FlowSinkModule and FlowTelemetryMessage wrapper

FlowTelemetryMessage wraps the locally-generated TelemetryMessageLog
protobuf so it implements the Sink API's Message marker interface.
FlowSinkModule uses the wrapper as both S and T (no aggregation in
this first cut — each datagram becomes one Kafka message; aggregation
can be added later)."
```

---

## Task 4: FlowUdpListener (Netty NioDatagramChannel handler)

**Files:**
- Create: `core/daemon-boot-minion/src/main/java/org/deltav/minion/telemetry/FlowUdpListener.java`
- Test: `core/daemon-boot-minion/src/test/java/org/deltav/minion/telemetry/FlowUdpListenerTest.java`

**Why:** This is the actual UDP receiver. It owns its own `NioEventLoopGroup` (the Trap and Syslog listeners don't use Netty so there's no existing group to share). On each datagram it: extracts the exporter address/port from Netty's `DatagramPacket.sender()`, runs `FlowProtocol.detect()`, builds a `TelemetryMessageLog` with Minion location/systemId metadata and the raw payload bytes, wraps the result in `FlowTelemetryMessage`, and pushes to the per-protocol `AsyncDispatcher`. Unknown protocols are dropped with a rate-limited WARN (one per 30s).

- [ ] **Step 4.1: Write the failing test first**

Create `core/daemon-boot-minion/src/test/java/org/deltav/minion/telemetry/FlowUdpListenerTest.java`:

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
package org.deltav.minion.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import org.deltav.minion.telemetry.proto.TelemetryProtos.TelemetryMessageLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opennms.core.ipc.sink.api.AsyncDispatcher;
import org.opennms.core.ipc.sink.api.AsyncDispatcher.DispatchStatus;

class FlowUdpListenerTest {

    private FlowUdpListener listener;
    private Map<FlowProtocol, AsyncDispatcher<FlowTelemetryMessage>> dispatchers;
    private Map<FlowProtocol, List<FlowTelemetryMessage>> captured;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        captured = new EnumMap<>(FlowProtocol.class);
        dispatchers = new EnumMap<>(FlowProtocol.class);
        for (FlowProtocol p : FlowProtocol.values()) {
            CopyOnWriteArrayList<FlowTelemetryMessage> list = new CopyOnWriteArrayList<>();
            captured.put(p, list);
            dispatchers.put(p, newCapturingDispatcher(list));
        }
        // Bind to port 0 to get an ephemeral port from the kernel
        listener = new FlowUdpListener(0, "127.0.0.1", dispatchers, "Default", "minion-test-01");
        listener.start();
        port = listener.getBoundPort();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (listener != null) {
            listener.stop();
        }
    }

    @Test
    void receivesNetflow9DatagramAndDispatchesToCorrectProtocol() throws Exception {
        byte[] payload = new byte[] { 0x00, 0x09, 0x00, 0x01, 0x02, 0x03 };

        sendDatagram(payload);

        await().atMost(Duration.ofSeconds(5)).until(() -> !captured.get(FlowProtocol.NETFLOW_9).isEmpty());

        List<FlowTelemetryMessage> netflow9 = captured.get(FlowProtocol.NETFLOW_9);
        assertThat(netflow9).hasSize(1);

        TelemetryMessageLog log = netflow9.get(0).getLog();
        assertThat(log.getLocation()).isEqualTo("Default");
        assertThat(log.getSystemId()).isEqualTo("minion-test-01");
        assertThat(log.getSourceAddress()).isEqualTo("127.0.0.1");
        assertThat(log.getSourcePort()).isGreaterThan(0);
        assertThat(log.getMessageCount()).isEqualTo(1);
        assertThat(log.getMessage(0).getBytes().toByteArray()).isEqualTo(payload);
        assertThat(log.getMessage(0).getTimestamp()).isGreaterThan(0L);

        // Other protocols should remain empty
        assertThat(captured.get(FlowProtocol.NETFLOW_5)).isEmpty();
        assertThat(captured.get(FlowProtocol.IPFIX)).isEmpty();
        assertThat(captured.get(FlowProtocol.SFLOW)).isEmpty();
    }

    @Test
    void receivesSflowDatagram() throws Exception {
        byte[] payload = new byte[] { 0x00, 0x00, 0x00, 0x05, 0x10, 0x20 };

        sendDatagram(payload);

        await().atMost(Duration.ofSeconds(5)).until(() -> !captured.get(FlowProtocol.SFLOW).isEmpty());
        assertThat(captured.get(FlowProtocol.SFLOW)).hasSize(1);
    }

    @Test
    void dropsUnknownProtocolDatagrams() throws Exception {
        byte[] payload = new byte[] { 0x00, 0x01, 0x00, 0x00 };

        sendDatagram(payload);

        // Give the listener time to process; then verify no dispatcher received anything
        Thread.sleep(200);
        for (FlowProtocol p : FlowProtocol.values()) {
            assertThat(captured.get(p)).isEmpty();
        }
    }

    private void sendDatagram(byte[] payload) throws Exception {
        try (DatagramSocket sock = new DatagramSocket()) {
            DatagramPacket packet = new DatagramPacket(
                    payload, payload.length, InetAddress.getByName("127.0.0.1"), port);
            sock.send(packet);
        }
    }

    private AsyncDispatcher<FlowTelemetryMessage> newCapturingDispatcher(List<FlowTelemetryMessage> sink) {
        return new AsyncDispatcher<>() {
            @Override
            public CompletableFuture<DispatchStatus> send(FlowTelemetryMessage message) {
                sink.add(message);
                return CompletableFuture.completedFuture(DispatchStatus.DISPATCHED);
            }

            @Override
            public int getQueueSize() {
                return 0;
            }

            @Override
            public void close() {
                // no-op
            }
        };
    }
}
```

- [ ] **Step 4.2: Ensure `awaitility` is on the test classpath**

Check whether the Spring Boot test starter already brings in `awaitility`:
```bash
./mvnw -pl :org.opennms.core.daemon-boot-minion -o dependency:tree 2>/dev/null | grep -i awaitility
```

If the grep returns non-empty, skip ahead to Step 4.3. If empty, add the dependency to `core/daemon-boot-minion/pom.xml` inside the existing `<dependencies>` block, just after the existing `spring-boot-starter-test` dependency:

```xml
        <dependency>
            <groupId>org.awaitility</groupId>
            <artifactId>awaitility</artifactId>
            <scope>test</scope>
        </dependency>
```

Then re-run the dependency check to confirm it appears.

- [ ] **Step 4.3: Run the tests — expect compile failure**

```bash
./mvnw -pl :org.opennms.core.daemon-boot-minion -Dtest=FlowUdpListenerTest test
```

Expected: BUILD FAILURE with "cannot find symbol: class FlowUdpListener".

- [ ] **Step 4.4: Implement `FlowUdpListener`**

Create `core/daemon-boot-minion/src/main/java/org/deltav/minion/telemetry/FlowUdpListener.java`:

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
package org.deltav.minion.telemetry;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.deltav.minion.telemetry.proto.TelemetryProtos.TelemetryMessage;
import org.deltav.minion.telemetry.proto.TelemetryProtos.TelemetryMessageLog;
import org.opennms.core.ipc.sink.api.AsyncDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;

/**
 * Netty-based UDP listener that receives flow protocol datagrams on a
 * single port, detects the protocol via {@link FlowProtocol#detect}, and
 * dispatches each datagram to a per-protocol {@link AsyncDispatcher}
 * targeting the {@code OpenNMS.Sink.Telemetry-{protocol}} Kafka topic.
 *
 * <p>Owns its own single-thread {@link NioEventLoopGroup}. The existing
 * Minion Trap listener uses horizon's {@code TrapListener} (internal
 * socket management) and the Syslog listener uses
 * {@code SyslogReceiverJavaNetImpl} (plain {@code DatagramSocket}), so
 * there is no pre-existing Netty group to share.
 *
 * <p>Lifecycle:
 * <ul>
 *   <li>{@link #start()} binds the UDP port and starts the event loop</li>
 *   <li>{@link #stop()} closes the channel and shuts the event loop down</li>
 * </ul>
 */
public class FlowUdpListener {

    private static final Logger LOG = LoggerFactory.getLogger(FlowUdpListener.class);

    /** Minimum interval between "unknown protocol" warnings, in nanoseconds. */
    private static final long WARN_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(30);

    private final int port;
    private final String bindAddress;
    private final Map<FlowProtocol, AsyncDispatcher<FlowTelemetryMessage>> dispatchers;
    private final String location;
    private final String systemId;

    private final AtomicLong lastUnknownWarnNanos = new AtomicLong(0);

    private EventLoopGroup eventLoopGroup;
    private Channel channel;

    public FlowUdpListener(int port,
                           String bindAddress,
                           Map<FlowProtocol, AsyncDispatcher<FlowTelemetryMessage>> dispatchers,
                           String location,
                           String systemId) {
        this.port = port;
        this.bindAddress = Objects.requireNonNull(bindAddress, "bindAddress");
        this.dispatchers = Objects.requireNonNull(dispatchers, "dispatchers");
        this.location = Objects.requireNonNull(location, "location");
        this.systemId = Objects.requireNonNull(systemId, "systemId");
    }

    public synchronized void start() throws InterruptedException {
        if (channel != null) {
            return;
        }
        eventLoopGroup = new NioEventLoopGroup(1);
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(eventLoopGroup)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_BROADCAST, false)
                .option(ChannelOption.SO_REUSEADDR, true)
                .handler(new ChannelInitializer<NioDatagramChannel>() {
                    @Override
                    protected void initChannel(NioDatagramChannel ch) {
                        ch.pipeline().addLast(new DatagramHandler());
                    }
                });

        InetSocketAddress local = "*".equals(bindAddress)
                ? new InetSocketAddress(port)
                : new InetSocketAddress(bindAddress, port);
        channel = bootstrap.bind(local).sync().channel();
        LOG.info("Flow telemetry listener started on {}:{} (protocols: Netflow-5, Netflow-9, IPFIX, sFlow)",
                bindAddress, getBoundPort());
    }

    public synchronized void stop() throws InterruptedException {
        if (channel != null) {
            channel.close().sync();
            channel = null;
        }
        if (eventLoopGroup != null) {
            eventLoopGroup.shutdownGracefully(0, 500, TimeUnit.MILLISECONDS).sync();
            eventLoopGroup = null;
        }
        for (AsyncDispatcher<?> d : dispatchers.values()) {
            try {
                d.close();
            } catch (Exception e) {
                LOG.warn("Failed to close dispatcher: {}", e.getMessage());
            }
        }
        LOG.info("Flow telemetry listener stopped");
    }

    /**
     * Returns the actual bound port. Useful for tests that bind to port 0
     * and need to know the ephemeral port the kernel chose.
     */
    public int getBoundPort() {
        if (channel == null) {
            return -1;
        }
        return ((InetSocketAddress) channel.localAddress()).getPort();
    }

    private void handleDatagram(ByteBuf buf, InetSocketAddress sender) {
        byte[] payload = new byte[buf.readableBytes()];
        buf.readBytes(payload);

        FlowProtocol protocol = FlowProtocol.detect(payload);
        if (protocol == null) {
            maybeWarnUnknownProtocol(payload.length, sender);
            return;
        }

        TelemetryMessage message = TelemetryMessage.newBuilder()
                .setTimestamp(System.currentTimeMillis())
                .setBytes(ByteString.copyFrom(payload))
                .build();

        TelemetryMessageLog log = TelemetryMessageLog.newBuilder()
                .setLocation(location)
                .setSystemId(systemId)
                .setSourceAddress(sender.getAddress().getHostAddress())
                .setSourcePort(sender.getPort())
                .addMessage(message)
                .build();

        AsyncDispatcher<FlowTelemetryMessage> dispatcher = dispatchers.get(protocol);
        if (dispatcher == null) {
            // Defensive: should never happen because the config class wires
            // a dispatcher for every FlowProtocol.
            LOG.warn("No dispatcher registered for {}; dropping datagram", protocol);
            return;
        }
        dispatcher.send(new FlowTelemetryMessage(log));
    }

    private void maybeWarnUnknownProtocol(int length, InetSocketAddress sender) {
        long now = System.nanoTime();
        long last = lastUnknownWarnNanos.get();
        if (now - last >= WARN_INTERVAL_NANOS
                && lastUnknownWarnNanos.compareAndSet(last, now)) {
            LOG.warn("Dropping {}-byte datagram from {} with unrecognized protocol version header",
                    length, sender);
        }
    }

    private class DatagramHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        @Override
        protected void channelRead0(io.netty.channel.ChannelHandlerContext ctx, DatagramPacket msg) {
            handleDatagram(msg.content(), msg.sender());
        }

        @Override
        public void exceptionCaught(io.netty.channel.ChannelHandlerContext ctx, Throwable cause) {
            LOG.warn("Exception in flow UDP handler: {}", cause.getMessage(), cause);
        }
    }
}
```

- [ ] **Step 4.5: Run the tests — expect pass**

```bash
./mvnw -pl :org.opennms.core.daemon-boot-minion -Dtest=FlowUdpListenerTest test
```

Expected: BUILD SUCCESS, 3 tests passing. If the test hangs on `await()`, the listener failed to dispatch — check for `NullPointerException` on `channel.localAddress()` or a missing dispatcher entry.

- [ ] **Step 4.6: Commit**

```bash
git add core/daemon-boot-minion/src/main/java/org/deltav/minion/telemetry/FlowUdpListener.java \
        core/daemon-boot-minion/src/test/java/org/deltav/minion/telemetry/FlowUdpListenerTest.java \
        core/daemon-boot-minion/pom.xml
git commit -m "feat(minion): add FlowUdpListener Netty UDP receiver

Single-port UDP listener receives Netflow v5/v9, IPFIX, and sFlow
datagrams; detects protocol from version header bytes; wraps raw
payload in TelemetryMessageLog protobuf with Minion metadata and
exporter address; dispatches to per-protocol AsyncDispatcher.

Owns its own single-thread NioEventLoopGroup (no sharing with Trap
or Syslog listeners, which use non-Netty transports).

Rate-limits unknown-protocol WARN logs to one per 30 seconds via
AtomicLong timestamp check to prevent log flooding during
misconfiguration or DDoS scenarios."
```

---

## Task 5: TelemetryListenerConfiguration Spring wiring

**Files:**
- Create: `core/daemon-boot-minion/src/main/java/org/deltav/minion/boot/TelemetryListenerConfiguration.java`
- Modify: `core/daemon-boot-minion/src/main/resources/application.yml`

**Why:** This is the glue that turns the plain-Java `FlowUdpListener` into a Spring-managed bean that starts at the right SmartLifecycle phase (400, after Sink client phase 200 and RPC server phase 300, matching the Trap/Syslog pattern). The `@ConditionalOnProperty` allows operators to disable telemetry collection entirely via `opennms.minion.telemetry.enabled=false`.

- [ ] **Step 5.1: Add telemetry properties to `core/daemon-boot-minion/src/main/resources/application.yml`**

The file currently has `telemetry.enabled: true` under `opennms.minion`. Replace that single line with a nested block to add port/address/queue properties. Locate this section (around line 20):

```yaml
    telemetry.enabled: true
```

Replace with:

```yaml
    telemetry:
      enabled: true
      port: 4729
      address: "*"
      queue-size: 10000
      num-threads: 2
```

- [ ] **Step 5.2: Create `TelemetryListenerConfiguration.java`**

Create `core/daemon-boot-minion/src/main/java/org/deltav/minion/boot/TelemetryListenerConfiguration.java`:

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
package org.deltav.minion.boot;

import java.util.EnumMap;
import java.util.Map;

import org.deltav.minion.telemetry.FlowProtocol;
import org.deltav.minion.telemetry.FlowSinkModule;
import org.deltav.minion.telemetry.FlowTelemetryMessage;
import org.deltav.minion.telemetry.FlowUdpListener;
import org.opennms.core.ipc.sink.api.AsyncDispatcher;
import org.opennms.core.ipc.sink.api.MessageDispatcherFactory;
import org.opennms.netmgt.dao.api.DistPollerDao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the flow telemetry UDP listener and its four per-protocol Sink
 * dispatchers.
 *
 * <p>When enabled, opens UDP port {@code opennms.minion.telemetry.port}
 * (default 4729) to receive Netflow v5/v9, IPFIX, and sFlow datagrams
 * and forwards each one to the appropriate
 * {@code OpenNMS.Sink.Telemetry-*} Kafka topic via the Sink API.</p>
 *
 * <p>Lifecycle phase 400: listeners start last, after Sink client
 * (200) and RPC server (300) are ready. Matches the Trap and Syslog
 * listener pattern.</p>
 */
@Configuration
@ConditionalOnProperty(name = "opennms.minion.telemetry.enabled", havingValue = "true", matchIfMissing = true)
public class TelemetryListenerConfiguration {

    @Value("${opennms.minion.telemetry.port:4729}")
    private int telemetryPort;

    @Value("${opennms.minion.telemetry.address:*}")
    private String bindAddress;

    @Value("${opennms.minion.telemetry.queue-size:10000}")
    private int queueSize;

    @Value("${opennms.minion.telemetry.num-threads:2}")
    private int numThreads;

    @Bean
    public FlowUdpListener flowUdpListener(MessageDispatcherFactory messageDispatcherFactory,
                                           DistPollerDao distPollerDao) {
        Map<FlowProtocol, AsyncDispatcher<FlowTelemetryMessage>> dispatchers =
                new EnumMap<>(FlowProtocol.class);
        for (FlowProtocol protocol : FlowProtocol.values()) {
            FlowSinkModule module = new FlowSinkModule(protocol, queueSize, numThreads);
            dispatchers.put(protocol, messageDispatcherFactory.createAsyncDispatcher(module));
        }

        return new FlowUdpListener(
                telemetryPort,
                bindAddress,
                dispatchers,
                distPollerDao.whoami().getLocation(),
                distPollerDao.whoami().getId());
    }

    @Bean
    public SmartLifecycle flowUdpListenerLifecycle(FlowUdpListener listener) {
        return new SmartLifecycle() {
            private volatile boolean running;

            @Override
            public void start() {
                try {
                    listener.start();
                    running = true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while starting FlowUdpListener", e);
                }
            }

            @Override
            public void stop() {
                try {
                    listener.stop();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    running = false;
                }
            }

            @Override
            public boolean isRunning() {
                return running;
            }

            @Override
            public int getPhase() {
                return 400;
            }
        };
    }
}
```

- [ ] **Step 5.3: Build the full module (verify nothing is broken)**

```bash
./mvnw -pl :org.opennms.core.daemon-boot-minion -am -DskipTests clean package
```

Expected: BUILD SUCCESS. This builds the minion jar including the Spring Boot repackage. If there are classpath issues with `MessageDispatcherFactory` or `DistPollerDao`, they are already transitively available via `daemon-boot-minion-common` — no POM changes should be needed.

- [ ] **Step 5.4: Run all unit tests for the module**

```bash
./mvnw -pl :org.opennms.core.daemon-boot-minion test
```

Expected: BUILD SUCCESS, all existing tests plus the 15 new tests from this plan passing.

- [ ] **Step 5.5: Commit**

```bash
git add core/daemon-boot-minion/src/main/java/org/deltav/minion/boot/TelemetryListenerConfiguration.java \
        core/daemon-boot-minion/src/main/resources/application.yml
git commit -m "feat(minion): wire TelemetryListenerConfiguration Spring Boot bean

Creates FlowUdpListener and four per-protocol AsyncDispatchers at
startup, backed by the existing KafkaRemoteMessageDispatcherFactory.
SmartLifecycle phase 400 ensures listener binds only after Sink
client (200) and RPC server (300) are ready.

application.yml gains opennms.minion.telemetry.{port,address,queue-size,
num-threads} properties (defaults: 4729, *, 10000, 2)."
```

---

## Task 6: Rebuild Minion Docker image and do a smoke test

**Files:** none (build and runtime verification only)

**Why:** Before touching Docker Compose and the E2E test, verify the new code actually works end-to-end in a real container. A quick smoke test catches classpath bloat or bean wiring issues that unit tests miss.

- [ ] **Step 6.1: Rebuild the Minion image**

The delta-v build system uses a script at `opennms-container/delta-v/build.sh`. The Minion image is built as part of `deltav`, but a targeted rebuild saves time:

```bash
./opennms-container/delta-v/build.sh deltav
```

Expected: BUILD SUCCESS. The log should end with "Built opennms/minion-boot:..." among other images. If the build fails with "cannot find symbol" errors for any of the new classes, re-run Task 5 Step 5.3 to verify the module compiles in isolation first.

- [ ] **Step 6.2: Start a minimal stack and verify listener starts**

Bring up just kafka, postgres, and minion (not the full E2E stack yet):

```bash
cd opennms-container/delta-v
docker compose up -d kafka postgres minion
```

Wait ~20 seconds for Minion to start, then check the log for the telemetry listener startup message:

```bash
docker compose logs minion 2>&1 | grep -i "flow telemetry listener"
```

Expected: one line like `Flow telemetry listener started on *:4729 (protocols: Netflow-5, Netflow-9, IPFIX, sFlow)`.

Also verify Minion health:

```bash
curl -sf http://localhost:8301/actuator/health | python3 -m json.tool
```

Expected: `"status": "UP"`.

If the Minion fails to start, check the full log for `BindException` (port conflict with another process on the host — unlikely since the container is isolated) or `BeanCreationException` (wiring issue in `TelemetryListenerConfiguration`).

- [ ] **Step 6.3: Manually send a test datagram from the host**

The Minion container's port 4729 is not yet exposed to the host (that's a docker-compose change in Task 7). To test from within the delta-v network, exec into a lightweight container:

```bash
docker run --rm --network deltav_default alpine sh -c \
  'apk add --no-cache netcat-openbsd >/dev/null && \
   printf "\x00\x09\x00\x01\x02\x03" | nc -u -w1 minion 4729 && \
   echo done'
```

Expected: `done` printed, no error.

Then check the Minion log for no warnings about unknown protocols:

```bash
docker compose logs --tail 50 minion | grep -i "unrecognized\|exception"
```

Expected: empty output (the datagram was recognized as Netflow v9 and dispatched).

- [ ] **Step 6.4: Stop the smoke-test stack**

```bash
docker compose down
```

No commit — Task 6 is verification only; nothing changed.

---

## Task 7: Docker Compose and Telemetryd config changes

**Files:**
- Modify: `opennms-container/delta-v/docker-compose.yml`
- Modify: `opennms-container/delta-v/telemetryd-overlay/etc/telemetryd-configuration.xml`

**Why:** Redirect the flow data path from `softflowd → telemetryd:4729` to `softflowd → minion:4729`. Expose the Minion's new UDP port to the host, remove the redundant Telemetryd UDP listener, and update the flow-exporter's destination and `depends_on`. The Telemetryd container stays in the stack — it remains the Kafka consumer for future non-flow adapters (JTI, BMP, etc.) — but no longer listens on any UDP ports.

- [ ] **Step 7.1: Add UDP port 4729 to the Minion service in `opennms-container/delta-v/docker-compose.yml`**

Locate the `minion` service's `ports` block (lines ~115-118). It currently reads:

```yaml
    ports:
      - "8301:8080"           # Actuator (was 8201 Karaf SSH)
      - "11162:1162/udp"      # SNMP Trapd
      - "1514:1514/udp"       # Syslog
```

Replace with:

```yaml
    ports:
      - "8301:8080"           # Actuator (was 8201 Karaf SSH)
      - "11162:1162/udp"      # SNMP Trapd
      - "1514:1514/udp"       # Syslog
      - "4729:4729/udp"       # Flow telemetry (Netflow v5/v9, IPFIX, sFlow)
```

- [ ] **Step 7.2: Remove port 4729 from the Telemetryd service in the same file**

Locate the `telemetryd` service's `ports` block (around line 479-480). It currently reads:

```yaml
    ports:
      - "4729:4729/udp"
```

Delete those two lines entirely. The `telemetryd` service stays in the file — we're only removing its UDP port binding, not the service itself. After the edit, the `telemetryd` block should have no `ports:` section at all (it doesn't need any; it's a Kafka consumer only).

If removing the `ports:` key leaves a `volumes:` section immediately adjacent, verify the YAML indentation remains valid:

```bash
docker compose config --services 2>&1 | head -20
```

Expected: the list of services printed, no YAML parse errors. If `docker compose config` reports an error, you likely left trailing whitespace or an orphaned `ports:` key. Re-inspect the telemetryd block.

- [ ] **Step 7.3: Point `flow-default-testnode-1` at the Minion**

Locate the `flow-default-testnode-1` service (lines ~566-579). The `depends_on` and `environment` currently read:

```yaml
    depends_on:
      telemetryd:
        condition: service_healthy
    environment:
      NETFLOW_COLLECTOR: telemetryd:4729
      NETFLOW_VERSION: "9"
      TRAFFIC_INTERVAL: "5"
```

Replace with:

```yaml
    depends_on:
      minion:
        condition: service_healthy
    environment:
      NETFLOW_COLLECTOR: minion:4729
      NETFLOW_VERSION: "9"
      TRAFFIC_INTERVAL: "5"
```

- [ ] **Step 7.4: Remove the Netflow-9 listener from `telemetryd-configuration.xml`**

Open `opennms-container/delta-v/telemetryd-overlay/etc/telemetryd-configuration.xml`. The top of the file currently contains:

```xml
    <listener name="Netflow-9-UDP-4729" class-name="org.opennms.netmgt.telemetry.listeners.UdpListener" enabled="true">
        <parameter key="port" value="4729"/>
        <parser name="Netflow-9-Parser" class-name="org.opennms.netmgt.telemetry.protocols.netflow.parser.Netflow9UdpParser" queue="Netflow-9"/>
    </listener>
```

Delete those four lines entirely. The file's header comment at the top already states "No listeners, no connectors, all adapters disabled. Minion is sole network ingress." — removing this listener makes the config match the comment. The `<queue>` blocks below the listener stay unchanged.

Verify the result with a quick validation:

```bash
grep -c "UdpListener" opennms-container/delta-v/telemetryd-overlay/etc/telemetryd-configuration.xml
```

Expected: `0`.

- [ ] **Step 7.5: Validate the compose file parses**

```bash
cd opennms-container/delta-v
docker compose --profile full config >/dev/null
```

Expected: no output (the command normally dumps the resolved config to stdout; redirecting hides it). If the command prints errors, there is a YAML syntax issue — re-read the affected service block.

Return to the repo root:

```bash
cd ../..
```

- [ ] **Step 7.6: Commit**

```bash
git add opennms-container/delta-v/docker-compose.yml \
        opennms-container/delta-v/telemetryd-overlay/etc/telemetryd-configuration.xml
git commit -m "chore(deltav): route flows through Minion UDP listener

- Expose minion:4729/udp on the host for flow ingestion
- Remove telemetryd:4729/udp (telemetryd is now Kafka-consumer only)
- Redirect flow-default-testnode-1 NETFLOW_COLLECTOR to minion:4729
- Update depends_on: minion (was telemetryd)
- Remove the Netflow-9-UDP-4729 listener from telemetryd-configuration.xml;
  the config comment already said 'No listeners', now it matches reality

Aligns flow pipeline with the 'Minion is sole network ingress' rule."
```

---

## Task 8: Update and run the E2E flow test

**Files:**
- Modify: `opennms-container/delta-v/test-flows-e2e.sh`

**Why:** The E2E test currently requires `clickhouse kafka telemetryd flow-enricher` to be running. After Task 7, the Minion is in the critical path, so the test must also verify it is running. The rest of the test assertions (ClickHouse flow counts, dimension MVs) need no changes because the wire format produced by the Minion is byte-identical to what Telemetryd produced — the flow-enricher deserializes them identically.

- [ ] **Step 8.1: Add `minion` to `REQUIRED_SERVICES`**

Open `opennms-container/delta-v/test-flows-e2e.sh`. Locate line ~137:

```bash
REQUIRED_SERVICES="clickhouse kafka telemetryd flow-enricher"
```

Replace with:

```bash
REQUIRED_SERVICES="clickhouse kafka telemetryd flow-enricher minion"
```

Also update the nearby `ok` message (line ~143) to keep the log accurate:

```bash
ok "Required services running (clickhouse, kafka, telemetryd, flow-enricher)"
```

Replace with:

```bash
ok "Required services running (clickhouse, kafka, telemetryd, flow-enricher, minion)"
```

- [ ] **Step 8.2: Start the full stack**

```bash
cd opennms-container/delta-v
./deploy.sh up full
```

Expected: all services come up. This may take 1-2 minutes. The `deploy.sh` script is the canonical entry point; if it does not exist on your branch, use `docker compose --profile full up -d` instead.

Verify all required services are in the "running" state:

```bash
docker compose ps --status running --format '{{.Name}}' | sort
```

Expected: a list including `deltav-clickhouse`, `deltav-flow-enricher`, `delta-v-minion`, `deltav-telemetryd`, `kafka`, plus whatever else the `full` profile activates.

- [ ] **Step 8.3: Run the E2E test**

Give the flow-exporter container ~60 seconds after stack startup to begin emitting flows, then:

```bash
./test-flows-e2e.sh
```

Expected: BUILD SUCCESS in the test output. The test's Phase 1 checks all 5 required services (now including minion); Phase 2 waits for non-zero rows in `deltav.flows_raw`; Phase 3 verifies the four dimension MVs; Phase 4 checks enrichment integrity (`exporter_node_id > 0`).

If the test fails at Phase 2 ("no flows in flows_raw"):
1. Check the Minion log for the listener startup message and any dispatch errors:
   ```bash
   docker compose logs minion | grep -iE "flow telemetry|exception|unrecognized"
   ```
2. Confirm the flow-exporter is actually sending packets and has resolved `minion:4729` correctly:
   ```bash
   docker compose logs flow-default-testnode-1 | tail -30
   ```
3. Verify Kafka received the messages by listing topics:
   ```bash
   docker compose exec kafka kafka-topics.sh --bootstrap-server localhost:9092 --list | grep -i telemetry
   ```

   Expected: at least `OpenNMS.Sink.Telemetry-Netflow-9`.

- [ ] **Step 8.4: Stop the stack**

```bash
./deploy.sh down
```

Or:

```bash
docker compose down
```

- [ ] **Step 8.5: Commit**

```bash
cd ../..
git add opennms-container/delta-v/test-flows-e2e.sh
git commit -m "test(flows): add minion to required services for E2E flow test

The Minion is now in the flow data path (Task 7), so the E2E test must
verify it is running before asserting on ClickHouse flow counts. All
other assertions are unchanged: the Minion's TelemetryMessageLog
protobuf wire format is byte-identical to what Telemetryd produced."
```

---

## Task 9: Push branch and open PR

**Files:** none

**Why:** Final delivery step. The feature branch gets pushed to the delta-v fork and a PR is opened against `develop`. The PR description should summarize the nine commits and link back to the spec.

- [ ] **Step 9.1: Push the branch**

```bash
git push -u origin feature/minion-telemetry-receiver
```

Expected: the branch is pushed and GitHub prints a URL to create the PR.

- [ ] **Step 9.2: Open the PR against `pbrane/delta-v` develop**

```bash
gh pr create --repo pbrane/delta-v --base develop --title "feat(minion): add telemetry UDP listener for flow collection" --body "$(cat <<'EOF'
## Summary
- Lightweight Netty-based UDP listener receives Netflow v5/v9, IPFIX, and sFlow on the Minion (port 4729), detects protocol from version header bytes, and dispatches each datagram to \`OpenNMS.Sink.Telemetry-*\` Kafka topics via the Sink API.
- Keeps the Minion free of horizon telemetry dependencies — the \`TelemetryMessageLog\` protobuf is locally generated from a copied \`.proto\` file, and a thin \`FlowTelemetryMessage\` wrapper adapts it to the Sink API \`Message\` marker interface.
- Redirects the flow-exporter from \`telemetryd:4729\` to \`minion:4729\`, removes the redundant Netflow listener from \`telemetryd-configuration.xml\`, and adds \`minion\` to the E2E test's required services. Aligns the flow pipeline with the "Minion is sole network ingress" architectural rule.
- Unblocks full E2E flow testing: \`softflowd → Minion → Kafka → flow-enricher → ClickHouse\`.

Design: \`docs/superpowers/specs/2026-04-12-minion-telemetry-receiver-design.md\`

## Test plan
- [x] Unit tests: \`FlowProtocolTest\`, \`FlowSinkModuleTest\`, \`FlowUdpListenerTest\` (15 new tests, all passing)
- [x] Smoke test: Minion image rebuilt, container starts, listener log line appears, manual Netflow v9 datagram dispatched without warning (Task 6)
- [x] E2E: \`test-flows-e2e.sh\` passes with the new Minion-first flow path (Task 8)
- [x] All previously-passing Minion unit tests still pass
EOF
)"
```

Expected: PR URL printed. **Never** use \`gh pr create\` without \`--repo pbrane/delta-v\` — the default target is \`OpenNMS/opennms\`.

---

## Success Criteria

The plan is complete when:

1. All 15 new unit tests pass locally (`./mvnw -pl :org.opennms.core.daemon-boot-minion test`)
2. The Minion Docker image builds and starts cleanly, with the "Flow telemetry listener started on *:4729" log line visible
3. `test-flows-e2e.sh` passes end-to-end against the updated Docker Compose stack, with flows flowing through the Minion instead of the Telemetryd container
4. The PR is open against `pbrane/delta-v` develop with all nine commits linked

## Known Risks

- **Netty vs Spring Boot classpath**: Spring Boot's `spring-boot-starter-web` brings in its own Netty version. If there is a version conflict with horizon's Netty, the `NioDatagramChannel` class may not load. If the unit test fails with `NoClassDefFoundError` on Netty classes, check `dependency:tree` for multiple Netty versions and add explicit version management or exclusions.
- **Kafka topic not created**: The first time the Minion dispatches to `OpenNMS.Sink.Telemetry-*`, Kafka auto-creates the topic if `auto.create.topics.enable=true`. If that setting is off in this stack, the dispatch will fail silently. Verify with `kafka-topics.sh --list` before running the E2E test (the grep in Task 8 Step 8.3 covers this).
- **flow-enricher consumer group lag**: The flow-enricher's consumer group `deltav-flow-enricher` starts from `latest`, so it won't replay older messages that were produced while the Minion was being restarted. This is typically fine for an E2E test because the flow-exporter runs continuously.
