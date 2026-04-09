# Flow Enricher (Phase 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Spring Cloud Stream service that consumes raw flow telemetry from Kafka Sink topics, parses and enriches it, and publishes enriched FlowDocument protobuf messages to the `deltav-flows` topic.

**Architecture:** Single Spring Boot application using Spring Cloud Stream with the Kafka binder. Consumes from 4 Sink topics (Netflow-5/9, IPFIX, sFlow), reuses horizon JAR adapter classes for protocol parsing, enriches via JDBC node lookup and classification engine, publishes enriched protobuf to `deltav-flows`.

**Tech Stack:** Spring Boot 4.0.3, Spring Cloud Stream (Kafka binder), Protocol Buffers, PostgreSQL (JDBC), Horizon JARs (protocol adapters, classification engine, Flow model)

**Spec:** `docs/superpowers/specs/2026-04-09-flow-processor-design.md`

---

## Prerequisites

Before starting this plan:
1. Sink topic prefix rename (`OpenNMS.Sink.*` → `DeltaV.Sink.*`) must be completed or in progress
2. Elasticsearch infrastructure PR #138 should be merged
3. Horizon 1.0.7 must be published (done)

**Note on Sink topic names:** The actual Kafka topic names include a `Telemetry-` prefix from `TelemetrySinkModule.getId()`. The full topic pattern is `{instanceId}.Sink.Telemetry-{queueName}`, e.g., `DeltaV.Sink.Telemetry-Netflow-5`. After the Sink prefix rename, the flow-enricher consumes from these renamed topics.

---

## File Structure

```
core/flow-enricher/
  pom.xml
  src/main/java/org/deltav/flows/enricher/
    FlowEnricherApplication.java          — Spring Boot entry point
    FlowEnricherConfiguration.java        — Bean wiring for enrichment pipeline
    SinkMessageDeserializer.java          — SinkMessage protobuf → TelemetryMessageLog
    FlowEnrichmentFunction.java           — Spring Cloud Stream function: consume → enrich → publish
    enrichment/
      JdbcNodeInfoLookup.java             — JDBC node/interface lookup (replaces NodeInfoCache)
      FlowClassificationService.java      — Loads and applies classification rules
      FlowLocalityCalculator.java         — Private/public IP determination
      ClockSkewCorrector.java             — Timestamp correction
      InterfaceMarkingCache.java          — TTL cache for hasFlows marking
    proto/
      FlowDocumentSerializer.java         — Enriched Flow → FlowDocument protobuf bytes
  src/main/proto/
    deltav-flows.proto                    — Public protobuf contract
  src/main/resources/
    application.yml
  src/test/java/org/deltav/flows/enricher/
    SinkMessageDeserializerTest.java
    FlowEnrichmentFunctionTest.java
    enrichment/
      JdbcNodeInfoLookupTest.java
      FlowLocalityCalculatorTest.java
      InterfaceMarkingCacheTest.java
```

---

### Task 1: Add Spring Cloud Stream BOM to parent POM

**Files:**
- Modify: `pom.xml` (root — add Spring Cloud BOM to dependencyManagement)

- [ ] **Step 1: Verify Spring Cloud compatibility with Spring Boot 4.0.3**

Run: Check Spring Cloud release train compatibility matrix. Spring Boot 4.0.3 requires Spring Cloud 2025.0.x.

```bash
# Verify the latest compatible version
curl -s https://repo1.maven.org/maven2/org/springframework/cloud/spring-cloud-dependencies/maven-metadata.xml | grep '<version>' | tail -5
```

- [ ] **Step 2: Add Spring Cloud BOM to parent POM dependencyManagement**

In `pom.xml`, add after the existing BOM imports (after the horizon BOM, before `</dependencyManagement>`):

```xml
<!-- Spring Cloud Stream for flow-enricher and flow-aggregator -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-dependencies</artifactId>
    <version>${spring-cloud.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

Add the property in `<properties>`:
```xml
<spring-cloud.version>2025.0.0</spring-cloud.version>
```

**IMPORTANT:** The Spring Cloud BOM must be imported AFTER `spring-boot-dependencies` (which is first). Order matters — first declared wins for version conflicts.

- [ ] **Step 3: Verify BOM resolves**

Run:
```bash
./mvnw -B help:effective-pom -pl . | grep spring-cloud
```
Expected: Spring Cloud version appears in effective POM.

- [ ] **Step 4: Commit**

```bash
git add pom.xml
git commit -m "build: add Spring Cloud Stream BOM for flow processing services"
```

---

### Task 2: Create flow-enricher Maven module

**Files:**
- Create: `core/flow-enricher/pom.xml`
- Modify: `pom.xml` (root — add module)

- [ ] **Step 1: Create module directory**

```bash
mkdir -p core/flow-enricher/src/main/java/org/deltav/flows/enricher
mkdir -p core/flow-enricher/src/main/proto
mkdir -p core/flow-enricher/src/main/resources
mkdir -p core/flow-enricher/src/test/java/org/deltav/flows/enricher
```

- [ ] **Step 2: Create pom.xml**

Create `core/flow-enricher/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.deltav</groupId>
        <artifactId>delta-v-parent</artifactId>
        <version>0.0.1-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <groupId>org.deltav.flows</groupId>
    <artifactId>org.deltav.flows.flow-enricher</artifactId>
    <name>Delta-V :: Flows :: Enricher</name>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- Spring Cloud Stream + Kafka binder -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-stream</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-stream-binder-kafka</artifactId>
        </dependency>

        <!-- Protocol Buffers -->
        <dependency>
            <groupId>com.google.protobuf</groupId>
            <artifactId>protobuf-java</artifactId>
        </dependency>
        <dependency>
            <groupId>com.google.protobuf</groupId>
            <artifactId>protobuf-java-util</artifactId>
        </dependency>

        <!-- PostgreSQL -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Horizon JARs: Sink IPC (SinkMessage protobuf) -->
        <dependency>
            <groupId>org.opennms</groupId>
            <artifactId>org.opennms.core.ipc.sink.common</artifactId>
        </dependency>

        <!-- Horizon JARs: Telemetry common (TelemetryMessageLog protobuf) -->
        <dependency>
            <groupId>org.opennms</groupId>
            <artifactId>org.opennms.features.telemetry.common</artifactId>
        </dependency>

        <!-- Horizon JARs: Flow protocol adapters -->
        <dependency>
            <groupId>org.opennms</groupId>
            <artifactId>org.opennms.features.telemetry.protocols.netflow.adapter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.opennms</groupId>
            <artifactId>org.opennms.features.telemetry.protocols.sflow.adapter</artifactId>
        </dependency>

        <!-- Horizon JARs: Flow processing (Pipeline, DocumentEnricher, Flow model) -->
        <dependency>
            <groupId>org.opennms</groupId>
            <artifactId>org.opennms.features.flows.processing</artifactId>
        </dependency>

        <!-- Horizon JARs: Flow classification engine -->
        <dependency>
            <groupId>org.opennms</groupId>
            <artifactId>org.opennms.features.flows.classification.engine</artifactId>
        </dependency>

        <!-- Horizon JARs: Flow kafka-persistence (FlowDocument protobuf, FlowDocumentBuilder) -->
        <dependency>
            <groupId>org.opennms</groupId>
            <artifactId>org.opennms.features.flows.kafka-persistence</artifactId>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-stream-test-binder</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <goals>
                            <goal>repackage</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.xolstice.maven.plugins</groupId>
                <artifactId>protobuf-maven-plugin</artifactId>
                <version>0.6.1</version>
                <configuration>
                    <protocArtifact>com.google.protobuf:protoc:${protobuf.version}:exe:${os.detected.classifier}</protocArtifact>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>compile</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
        <extensions>
            <extension>
                <groupId>kr.motd.maven</groupId>
                <artifactId>os-maven-plugin</artifactId>
                <version>1.7.1</version>
            </extension>
        </extensions>
    </build>
</project>
```

**Note:** The exact horizon JAR artifact IDs need verification against the published 1.0.7 artifacts. The names above follow the horizon naming pattern but may need adjustment. Verify with:
```bash
find ~/.m2/repository/org/opennms -name '*flow*' -name '*.jar' | grep '1.0.7' | head -20
```

- [ ] **Step 3: Add module to parent POM**

In root `pom.xml`, add to `<modules>`:
```xml
<module>core/flow-enricher</module>
```

- [ ] **Step 4: Verify module resolves**

```bash
./mvnw -B -pl :org.deltav.flows.flow-enricher -am validate
```
Expected: BUILD SUCCESS (dependencies resolve)

- [ ] **Step 5: Commit**

```bash
git add core/flow-enricher/pom.xml pom.xml
git commit -m "build: create flow-enricher Maven module with Spring Cloud Stream"
```

---

### Task 3: Create FlowDocument protobuf definition

**Files:**
- Create: `core/flow-enricher/src/main/proto/deltav-flows.proto`

- [ ] **Step 1: Copy and adapt the horizon FlowDocument proto**

The base proto is at `delta-v-horizon/features/flows/kafka-persistence/src/main/proto/flowdocument.proto`. Copy it, change the package to `org.deltav.flows`, and add the API version comment.

Create `core/flow-enricher/src/main/proto/deltav-flows.proto`:

```protobuf
// API version: 1
// Public contract for the deltav-flows Kafka topic.
// Community consumers: generate client code from this file.
// Versioning: additive changes are backward compatible.
// Breaking changes require a major version bump.

syntax = "proto3";

package org.deltav.flows;

option java_package = "org.deltav.flows.proto";
option java_outer_classname = "FlowDocumentProtos";

import "google/protobuf/wrappers.proto";

message NodeInfo {
    uint32 node_id = 1;
    string foreign_source = 2;
    string foreign_id = 3;
    repeated string categories = 4;
}

enum Direction {
    DIRECTION_UNKNOWN = 0;
    INGRESS = 1;
    EGRESS = 2;
}

enum Locality {
    LOCALITY_UNKNOWN = 0;
    PRIVATE = 1;
    PUBLIC = 2;
}

enum NetflowVersion {
    NETFLOW_VERSION_UNKNOWN = 0;
    V5 = 1;
    V9 = 2;
    IPFIX = 3;
    SFLOW = 4;
}

enum SamplingAlgorithm {
    SAMPLING_ALGORITHM_UNKNOWN = 0;
    SYSTEMATIC_COUNT_BASED_SAMPLING = 1;
    SYSTEMATIC_TIME_BASED_SAMPLING = 2;
    RANDOM_N_OUT_OF_N_SAMPLING = 3;
    UNIFORM_PROBABILISTIC_SAMPLING = 4;
    PROPERTY_MATCH_FILTERING = 5;
    HASH_BASED_FILTERING = 6;
    FLOW_STATE_DEPENDENT_INTERMEDIATE_FLOW_SELECTION_PROCESS = 7;
}

message FlowDocument {
    uint64 timestamp = 1;
    google.protobuf.UInt64Value num_bytes = 2;
    Direction direction = 3;
    string dst_address = 4;
    string dst_hostname = 5;
    google.protobuf.UInt64Value dst_as = 6;
    google.protobuf.UInt32Value dst_mask_len = 7;
    google.protobuf.UInt32Value dst_port = 8;
    google.protobuf.UInt32Value engine_id = 9;
    google.protobuf.UInt32Value engine_type = 10;
    google.protobuf.UInt64Value delta_switched = 11;
    google.protobuf.UInt64Value first_switched = 12;
    google.protobuf.UInt64Value last_switched = 13;
    google.protobuf.UInt32Value num_flow_records = 14;
    google.protobuf.UInt64Value num_packets = 15;
    google.protobuf.UInt64Value flow_seq_num = 16;
    google.protobuf.UInt32Value input_snmp_ifindex = 17;
    google.protobuf.UInt32Value output_snmp_ifindex = 18;
    google.protobuf.UInt32Value ip_protocol_version = 19;
    string next_hop_address = 20;
    string next_hop_hostname = 21;
    google.protobuf.UInt32Value protocol = 22;
    SamplingAlgorithm sampling_algorithm = 23;
    google.protobuf.DoubleValue sampling_interval = 24;
    // field 25 reserved
    string src_address = 26;
    string src_hostname = 27;
    google.protobuf.UInt64Value src_as = 28;
    google.protobuf.UInt32Value src_mask_len = 29;
    google.protobuf.UInt32Value src_port = 30;
    google.protobuf.UInt32Value tcp_flags = 31;
    google.protobuf.UInt32Value tos = 32;
    NetflowVersion netflow_version = 33;
    string vlan = 34;
    NodeInfo src_node = 35;
    NodeInfo exporter_node = 36;
    NodeInfo dest_node = 37;
    string application = 38;
    string host = 39;
    string location = 40;
    Locality src_locality = 41;
    Locality dst_locality = 42;
    Locality flow_locality = 43;
    // field 44 reserved (former convo_key)
    uint64 clock_correction = 45;
    google.protobuf.UInt32Value dscp = 46;
    google.protobuf.UInt32Value ecn = 47;
}
```

- [ ] **Step 2: Verify protobuf compiles**

```bash
./mvnw -B -pl :org.deltav.flows.flow-enricher compile
```
Expected: BUILD SUCCESS, generated Java classes in `target/generated-sources/protobuf/`

- [ ] **Step 3: Commit**

```bash
git add core/flow-enricher/src/main/proto/deltav-flows.proto
git commit -m "feat: add FlowDocument protobuf definition (community API contract)"
```

---

### Task 4: Implement FlowLocalityCalculator

Start with the simplest, stateless enrichment component. TDD.

**Files:**
- Create: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/enrichment/FlowLocalityCalculator.java`
- Create: `core/flow-enricher/src/test/java/org/deltav/flows/enricher/enrichment/FlowLocalityCalculatorTest.java`

- [ ] **Step 1: Write the failing test**

```java
package org.deltav.flows.enricher.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class FlowLocalityCalculatorTest {

    private final FlowLocalityCalculator calculator = new FlowLocalityCalculator();

    @Test
    void privateAddressesArePrivate() {
        assertThat(calculator.classify("10.0.0.1")).isEqualTo(FlowLocalityCalculator.Locality.PRIVATE);
        assertThat(calculator.classify("172.16.5.1")).isEqualTo(FlowLocalityCalculator.Locality.PRIVATE);
        assertThat(calculator.classify("192.168.1.1")).isEqualTo(FlowLocalityCalculator.Locality.PRIVATE);
        assertThat(calculator.classify("127.0.0.1")).isEqualTo(FlowLocalityCalculator.Locality.PRIVATE);
    }

    @Test
    void publicAddressesArePublic() {
        assertThat(calculator.classify("8.8.8.8")).isEqualTo(FlowLocalityCalculator.Locality.PUBLIC);
        assertThat(calculator.classify("1.1.1.1")).isEqualTo(FlowLocalityCalculator.Locality.PUBLIC);
    }

    @Test
    void nullOrEmptyIsUnknown() {
        assertThat(calculator.classify(null)).isEqualTo(FlowLocalityCalculator.Locality.UNKNOWN);
        assertThat(calculator.classify("")).isEqualTo(FlowLocalityCalculator.Locality.UNKNOWN);
    }

    @Test
    void flowLocalityIsPublicIfEitherEndpointIsPublic() {
        assertThat(calculator.flowLocality("10.0.0.1", "8.8.8.8"))
                .isEqualTo(FlowLocalityCalculator.Locality.PUBLIC);
        assertThat(calculator.flowLocality("10.0.0.1", "192.168.1.1"))
                .isEqualTo(FlowLocalityCalculator.Locality.PRIVATE);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./mvnw -B -pl :org.deltav.flows.flow-enricher test -Dtest=FlowLocalityCalculatorTest
```
Expected: FAIL — class not found

- [ ] **Step 3: Implement FlowLocalityCalculator**

```java
package org.deltav.flows.enricher.enrichment;

import java.net.InetAddress;

public class FlowLocalityCalculator {

    public enum Locality { UNKNOWN, PRIVATE, PUBLIC }

    public Locality classify(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return Locality.UNKNOWN;
        }
        try {
            InetAddress addr = InetAddress.getByName(ipAddress);
            if (addr.isLoopbackAddress() || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()) {
                return Locality.PRIVATE;
            }
            // 172.16.0.0/12 check (isSiteLocalAddress covers 10.x and 192.168.x but not all of 172.16-31.x)
            byte[] bytes = addr.getAddress();
            if (bytes.length == 4 && (bytes[0] & 0xFF) == 172
                    && (bytes[1] & 0xFF) >= 16 && (bytes[1] & 0xFF) <= 31) {
                return Locality.PRIVATE;
            }
            return Locality.PUBLIC;
        } catch (Exception e) {
            return Locality.UNKNOWN;
        }
    }

    public Locality flowLocality(String srcAddress, String dstAddress) {
        Locality src = classify(srcAddress);
        Locality dst = classify(dstAddress);
        if (src == Locality.PUBLIC || dst == Locality.PUBLIC) {
            return Locality.PUBLIC;
        }
        if (src == Locality.PRIVATE && dst == Locality.PRIVATE) {
            return Locality.PRIVATE;
        }
        return Locality.UNKNOWN;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./mvnw -B -pl :org.deltav.flows.flow-enricher test -Dtest=FlowLocalityCalculatorTest
```
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/flow-enricher/src/main/java/org/deltav/flows/enricher/enrichment/FlowLocalityCalculator.java \
        core/flow-enricher/src/test/java/org/deltav/flows/enricher/enrichment/FlowLocalityCalculatorTest.java
git commit -m "feat(flow-enricher): add FlowLocalityCalculator for private/public IP classification"
```

---

### Task 5: Implement InterfaceMarkingCache

**Files:**
- Create: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/enrichment/InterfaceMarkingCache.java`
- Create: `core/flow-enricher/src/test/java/org/deltav/flows/enricher/enrichment/InterfaceMarkingCacheTest.java`

- [ ] **Step 1: Write the failing test**

```java
package org.deltav.flows.enricher.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class InterfaceMarkingCacheTest {

    @Test
    void firstMarkingExecutesDbUpdate() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        InterfaceMarkingCache cache = new InterfaceMarkingCache(jdbc, Duration.ofHours(24));

        cache.markIfNeeded(5L, 12);

        verify(jdbc).update(
                eq("UPDATE snmpinterface SET hasflows = true WHERE nodeid = ? AND snmpifindex = ?"),
                eq(5L), eq(12));
    }

    @Test
    void secondMarkingWithinTtlSkipsDbUpdate() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        InterfaceMarkingCache cache = new InterfaceMarkingCache(jdbc, Duration.ofHours(24));

        cache.markIfNeeded(5L, 12);
        cache.markIfNeeded(5L, 12);

        verify(jdbc, times(1)).update(anyString(), anyLong(), anyInt());
    }

    @Test
    void differentInterfacesAreTrackedSeparately() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        InterfaceMarkingCache cache = new InterfaceMarkingCache(jdbc, Duration.ofHours(24));

        cache.markIfNeeded(5L, 12);
        cache.markIfNeeded(5L, 13);

        verify(jdbc, times(2)).update(anyString(), anyLong(), anyInt());
    }

    @Test
    void evictNodeClearsAllInterfacesForNode() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        InterfaceMarkingCache cache = new InterfaceMarkingCache(jdbc, Duration.ofHours(24));

        cache.markIfNeeded(5L, 12);
        cache.markIfNeeded(5L, 13);
        cache.evictNode(5L);
        cache.markIfNeeded(5L, 12);

        // 3 total: initial 12, initial 13, re-mark 12 after eviction
        verify(jdbc, times(3)).update(anyString(), anyLong(), anyInt());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./mvnw -B -pl :org.deltav.flows.flow-enricher test -Dtest=InterfaceMarkingCacheTest
```
Expected: FAIL — class not found

- [ ] **Step 3: Implement InterfaceMarkingCache**

```java
package org.deltav.flows.enricher.enrichment;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

public class InterfaceMarkingCache {

    private static final Logger LOG = LoggerFactory.getLogger(InterfaceMarkingCache.class);

    private final JdbcTemplate jdbc;
    private final Duration ttl;
    private final Map<Long, Map<Integer, Instant>> cache = new ConcurrentHashMap<>();

    public InterfaceMarkingCache(JdbcTemplate jdbc, Duration ttl) {
        this.jdbc = jdbc;
        this.ttl = ttl;
    }

    public void markIfNeeded(long nodeId, int ifIndex) {
        Map<Integer, Instant> nodeCache = cache.computeIfAbsent(nodeId, k -> new ConcurrentHashMap<>());
        Instant lastMarked = nodeCache.get(ifIndex);
        Instant now = Instant.now();

        if (lastMarked != null && lastMarked.plus(ttl).isAfter(now)) {
            return;
        }

        try {
            jdbc.update(
                    "UPDATE snmpinterface SET hasflows = true WHERE nodeid = ? AND snmpifindex = ?",
                    nodeId, ifIndex);
            nodeCache.put(ifIndex, now);
        } catch (Exception e) {
            LOG.debug("Failed to mark interface hasFlows for node {} ifIndex {}: {}",
                    nodeId, ifIndex, e.getMessage());
        }
    }

    public void evictNode(long nodeId) {
        cache.remove(nodeId);
    }

    public void cleanExpired() {
        Instant cutoff = Instant.now().minus(ttl);
        cache.forEach((nodeId, interfaces) -> {
            interfaces.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
            if (interfaces.isEmpty()) {
                cache.remove(nodeId);
            }
        });
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./mvnw -B -pl :org.deltav.flows.flow-enricher test -Dtest=InterfaceMarkingCacheTest
```
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/flow-enricher/src/main/java/org/deltav/flows/enricher/enrichment/InterfaceMarkingCache.java \
        core/flow-enricher/src/test/java/org/deltav/flows/enricher/enrichment/InterfaceMarkingCacheTest.java
git commit -m "feat(flow-enricher): add InterfaceMarkingCache with TTL and nodeDeleted eviction"
```

---

### Task 6: Implement SinkMessageDeserializer

Handles the two-layer protobuf unwrapping: Kafka bytes → SinkMessage → TelemetryMessageLog.

**Files:**
- Create: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/SinkMessageDeserializer.java`
- Create: `core/flow-enricher/src/test/java/org/deltav/flows/enricher/SinkMessageDeserializerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package org.deltav.flows.enricher;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.opennms.core.ipc.sink.model.SinkMessage;
import org.opennms.netmgt.telemetry.ipc.TelemetryProtos;

class SinkMessageDeserializerTest {

    private final SinkMessageDeserializer deserializer = new SinkMessageDeserializer();

    @Test
    void deserializesSingleChunkMessage() throws Exception {
        // Build a TelemetryMessageLog with one empty message
        TelemetryProtos.TelemetryMessageLog messageLog = TelemetryProtos.TelemetryMessageLog.newBuilder()
                .setLocation("Default")
                .setSystemId("minion-01")
                .setSourceAddress("192.168.1.1")
                .setSourcePort(4729)
                .build();

        // Wrap in SinkMessage
        SinkMessage sinkMessage = SinkMessage.newBuilder()
                .setMessageId("test-1")
                .setContent(com.google.protobuf.ByteString.copyFrom(messageLog.toByteArray()))
                .setCurrentChunkNumber(0)
                .setTotalChunks(1)
                .build();

        TelemetryProtos.TelemetryMessageLog result = deserializer.deserialize(sinkMessage.toByteArray());

        assertThat(result).isNotNull();
        assertThat(result.getLocation()).isEqualTo("Default");
        assertThat(result.getSystemId()).isEqualTo("minion-01");
        assertThat(result.getSourceAddress()).isEqualTo("192.168.1.1");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./mvnw -B -pl :org.deltav.flows.flow-enricher test -Dtest=SinkMessageDeserializerTest
```
Expected: FAIL — class not found

- [ ] **Step 3: Implement SinkMessageDeserializer**

```java
package org.deltav.flows.enricher;

import org.opennms.core.ipc.sink.model.SinkMessage;
import org.opennms.netmgt.telemetry.ipc.TelemetryProtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deserializes Kafka Sink topic bytes into TelemetryMessageLog.
 *
 * <p>Handles the two-layer protobuf unwrapping:
 * Kafka bytes → SinkMessage → extract content → TelemetryMessageLog.
 *
 * <p>Large message chunking is not supported in this initial implementation.
 * Messages with totalChunks > 1 are dropped with a warning. Flow telemetry
 * messages are typically small enough to fit in a single chunk.
 */
public class SinkMessageDeserializer {

    private static final Logger LOG = LoggerFactory.getLogger(SinkMessageDeserializer.class);

    public TelemetryProtos.TelemetryMessageLog deserialize(byte[] kafkaBytes) {
        try {
            SinkMessage sinkMessage = SinkMessage.parseFrom(kafkaBytes);

            if (sinkMessage.getTotalChunks() > 1) {
                LOG.warn("Chunked SinkMessage not supported (messageId={}, chunks={}), dropping",
                        sinkMessage.getMessageId(), sinkMessage.getTotalChunks());
                return null;
            }

            byte[] content = sinkMessage.getContent().toByteArray();
            return TelemetryProtos.TelemetryMessageLog.parseFrom(content);
        } catch (Exception e) {
            LOG.warn("Failed to deserialize SinkMessage: {}", e.getMessage());
            return null;
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./mvnw -B -pl :org.deltav.flows.flow-enricher test -Dtest=SinkMessageDeserializerTest
```
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/flow-enricher/src/main/java/org/deltav/flows/enricher/SinkMessageDeserializer.java \
        core/flow-enricher/src/test/java/org/deltav/flows/enricher/SinkMessageDeserializerTest.java
git commit -m "feat(flow-enricher): add SinkMessageDeserializer for Kafka Sink protobuf unwrapping"
```

---

### Task 7: Implement JdbcNodeInfoLookup

JDBC-based node lookup for flow enrichment (exporter, src, dst nodes).

**Files:**
- Create: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/enrichment/JdbcNodeInfoLookup.java`
- Create: `core/flow-enricher/src/test/java/org/deltav/flows/enricher/enrichment/JdbcNodeInfoLookupTest.java`

- [ ] **Step 1: Write the failing test**

```java
package org.deltav.flows.enricher.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcNodeInfoLookupTest {

    @Test
    void lookupByIpAddressReturnsNodeInfo() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcNodeInfoLookup lookup = new JdbcNodeInfoLookup(jdbc);

        JdbcNodeInfoLookup.NodeInfo mockNode = new JdbcNodeInfoLookup.NodeInfo(
                5, "delta-v", "router-1", "Default");
        when(jdbc.query(contains("ipaddr"), any(RowMapper.class), eq("192.168.1.1")))
                .thenReturn(java.util.List.of(mockNode));

        JdbcNodeInfoLookup.NodeInfo result = lookup.lookupByIpAddress("192.168.1.1");

        assertThat(result).isNotNull();
        assertThat(result.nodeId()).isEqualTo(5);
        assertThat(result.foreignSource()).isEqualTo("delta-v");
        assertThat(result.foreignId()).isEqualTo("router-1");
    }

    @Test
    void lookupByIpAddressReturnsNullWhenNotFound() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcNodeInfoLookup lookup = new JdbcNodeInfoLookup(jdbc);

        when(jdbc.query(anyString(), any(RowMapper.class), anyString()))
                .thenReturn(java.util.List.of());

        assertThat(lookup.lookupByIpAddress("1.2.3.4")).isNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./mvnw -B -pl :org.deltav.flows.flow-enricher test -Dtest=JdbcNodeInfoLookupTest
```
Expected: FAIL — class not found

- [ ] **Step 3: Implement JdbcNodeInfoLookup**

```java
package org.deltav.flows.enricher.enrichment;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcNodeInfoLookup {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcNodeInfoLookup.class);

    public record NodeInfo(int nodeId, String foreignSource, String foreignId, String location) {}

    private final JdbcTemplate jdbc;

    public JdbcNodeInfoLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public NodeInfo lookupByIpAddress(String ipAddress) {
        try {
            List<NodeInfo> results = jdbc.query(
                    "SELECT n.nodeid, n.foreignsource, n.foreignid, n.location " +
                    "FROM node n JOIN ipinterface i ON n.nodeid = i.nodeid " +
                    "WHERE i.ipaddr = ? LIMIT 1",
                    (rs, rowNum) -> new NodeInfo(
                            rs.getInt("nodeid"),
                            rs.getString("foreignsource"),
                            rs.getString("foreignid"),
                            rs.getString("location")),
                    ipAddress);
            return results.isEmpty() ? null : results.getFirst();
        } catch (Exception e) {
            LOG.debug("Node lookup failed for IP {}: {}", ipAddress, e.getMessage());
            return null;
        }
    }

    public NodeInfo lookupByNodeId(int nodeId) {
        try {
            List<NodeInfo> results = jdbc.query(
                    "SELECT nodeid, foreignsource, foreignid, location FROM node WHERE nodeid = ?",
                    (rs, rowNum) -> new NodeInfo(
                            rs.getInt("nodeid"),
                            rs.getString("foreignsource"),
                            rs.getString("foreignid"),
                            rs.getString("location")),
                    nodeId);
            return results.isEmpty() ? null : results.getFirst();
        } catch (Exception e) {
            LOG.debug("Node lookup failed for nodeId {}: {}", nodeId, e.getMessage());
            return null;
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./mvnw -B -pl :org.deltav.flows.flow-enricher test -Dtest=JdbcNodeInfoLookupTest
```
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/flow-enricher/src/main/java/org/deltav/flows/enricher/enrichment/JdbcNodeInfoLookup.java \
        core/flow-enricher/src/test/java/org/deltav/flows/enricher/enrichment/JdbcNodeInfoLookupTest.java
git commit -m "feat(flow-enricher): add JdbcNodeInfoLookup for flow node resolution"
```

---

### Task 8: Implement FlowEnrichmentFunction and Application wiring

This is the core Spring Cloud Stream function that ties everything together. Also creates the Application class and configuration.

**Files:**
- Create: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/FlowEnricherApplication.java`
- Create: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/FlowEnricherConfiguration.java`
- Create: `core/flow-enricher/src/main/java/org/deltav/flows/enricher/FlowEnrichmentFunction.java`
- Create: `core/flow-enricher/src/main/resources/application.yml`

- [ ] **Step 1: Create FlowEnricherApplication**

```java
package org.deltav.flows.enricher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {
        HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class
})
@EnableScheduling
public class FlowEnricherApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowEnricherApplication.class, args);
    }
}
```

- [ ] **Step 2: Create FlowEnricherConfiguration**

```java
package org.deltav.flows.enricher;

import java.time.Duration;
import java.util.function.Function;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import org.deltav.flows.enricher.enrichment.FlowLocalityCalculator;
import org.deltav.flows.enricher.enrichment.InterfaceMarkingCache;
import org.deltav.flows.enricher.enrichment.JdbcNodeInfoLookup;

@Configuration
public class FlowEnricherConfiguration {

    @Bean
    public JdbcTemplate flowJdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public JdbcNodeInfoLookup jdbcNodeInfoLookup(JdbcTemplate flowJdbcTemplate) {
        return new JdbcNodeInfoLookup(flowJdbcTemplate);
    }

    @Bean
    public FlowLocalityCalculator flowLocalityCalculator() {
        return new FlowLocalityCalculator();
    }

    @Bean
    public InterfaceMarkingCache interfaceMarkingCache(
            JdbcTemplate flowJdbcTemplate,
            @Value("${deltav.flows.interface-marking.cache-ttl:24h}") Duration cacheTtl) {
        return new InterfaceMarkingCache(flowJdbcTemplate, cacheTtl);
    }

    @Bean
    public SinkMessageDeserializer sinkMessageDeserializer() {
        return new SinkMessageDeserializer();
    }

    @Bean
    public FlowEnrichmentFunction flowEnrichmentFunction(
            SinkMessageDeserializer deserializer,
            JdbcNodeInfoLookup nodeInfoLookup,
            FlowLocalityCalculator localityCalculator,
            InterfaceMarkingCache interfaceMarkingCache) {
        return new FlowEnrichmentFunction(
                deserializer, nodeInfoLookup, localityCalculator, interfaceMarkingCache);
    }

    /**
     * Spring Cloud Stream function binding. Consumes from Sink topics,
     * produces enriched FlowDocument bytes to deltav-flows.
     *
     * Spring Cloud Stream discovers this bean by name convention:
     * "enrichFlows" maps to the function definition in application.yml.
     */
    @Bean
    public Function<byte[], byte[]> enrichFlows(FlowEnrichmentFunction enrichmentFunction) {
        return enrichmentFunction::processMessage;
    }

    @Scheduled(fixedRate = 3600000) // hourly
    public void cleanExpiredMarkings(InterfaceMarkingCache cache) {
        cache.cleanExpired();
    }
}
```

- [ ] **Step 3: Create FlowEnrichmentFunction**

This is the core processing class. Initially wires up deserialization → enrichment → serialization for a single input topic (Netflow-9). Multi-topic support (Netflow-5, IPFIX, sFlow) is added by registering multiple bindings to the same function.

```java
package org.deltav.flows.enricher;

import java.util.List;

import org.deltav.flows.enricher.enrichment.FlowLocalityCalculator;
import org.deltav.flows.enricher.enrichment.InterfaceMarkingCache;
import org.deltav.flows.enricher.enrichment.JdbcNodeInfoLookup;
import org.deltav.flows.proto.FlowDocumentProtos;
import org.opennms.netmgt.telemetry.ipc.TelemetryProtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowEnrichmentFunction {

    private static final Logger LOG = LoggerFactory.getLogger(FlowEnrichmentFunction.class);

    private final SinkMessageDeserializer deserializer;
    private final JdbcNodeInfoLookup nodeInfoLookup;
    private final FlowLocalityCalculator localityCalculator;
    private final InterfaceMarkingCache interfaceMarkingCache;

    public FlowEnrichmentFunction(
            SinkMessageDeserializer deserializer,
            JdbcNodeInfoLookup nodeInfoLookup,
            FlowLocalityCalculator localityCalculator,
            InterfaceMarkingCache interfaceMarkingCache) {
        this.deserializer = deserializer;
        this.nodeInfoLookup = nodeInfoLookup;
        this.localityCalculator = localityCalculator;
        this.interfaceMarkingCache = interfaceMarkingCache;
    }

    /**
     * Processes a single Kafka Sink message: deserialize → parse → enrich → serialize.
     * Returns null if the message cannot be processed (logged at WARN).
     */
    public byte[] processMessage(byte[] kafkaBytes) {
        TelemetryProtos.TelemetryMessageLog messageLog = deserializer.deserialize(kafkaBytes);
        if (messageLog == null) {
            return null;
        }

        // For the MVP, process the first TelemetryMessage in the log.
        // Full implementation will iterate all messages and use the appropriate
        // protocol adapter based on the source topic.
        if (messageLog.getMessageCount() == 0) {
            return null;
        }

        try {
            // Build enriched FlowDocument
            // NOTE: Full adapter integration (Netflow5/9, IPFIX, sFlow parsing)
            // requires wiring the horizon adapter JARs. This MVP skeleton
            // establishes the Spring Cloud Stream pipeline; adapter integration
            // is completed when the exact horizon JAR class compatibility is verified.
            FlowDocumentProtos.FlowDocument.Builder builder =
                    FlowDocumentProtos.FlowDocument.newBuilder();

            builder.setTimestamp(System.currentTimeMillis());
            builder.setLocation(messageLog.getLocation());
            builder.setHost(messageLog.getSourceAddress());

            // Enrich: exporter node lookup
            JdbcNodeInfoLookup.NodeInfo exporterNode =
                    nodeInfoLookup.lookupByIpAddress(messageLog.getSourceAddress());
            if (exporterNode != null) {
                builder.setExporterNode(FlowDocumentProtos.NodeInfo.newBuilder()
                        .setNodeId(exporterNode.nodeId())
                        .setForeignSource(exporterNode.foreignSource() != null ? exporterNode.foreignSource() : "")
                        .setForeignId(exporterNode.foreignId() != null ? exporterNode.foreignId() : "")
                        .build());
            }

            // Enrich: locality
            // (src/dst addresses will come from protocol adapter parsing)

            FlowDocumentProtos.FlowDocument flowDocument = builder.build();
            return flowDocument.toByteArray();
        } catch (Exception e) {
            LOG.warn("Failed to process flow message from {}: {}",
                    messageLog.getSourceAddress(), e.getMessage());
            return null;
        }
    }
}
```

- [ ] **Step 4: Create application.yml**

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/opennms}
    username: ${SPRING_DATASOURCE_USERNAME:opennms}
    password: ${SPRING_DATASOURCE_PASSWORD:opennms}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2

  cloud:
    stream:
      function:
        definition: enrichFlows
      bindings:
        enrichFlows-in-0:
          destination: ${DELTAV_FLOWS_SINK_TOPIC:DeltaV.Sink.Telemetry-Netflow-9}
          group: deltav-flow-enricher
        enrichFlows-out-0:
          destination: deltav-flows
      kafka:
        binder:
          brokers: ${KAFKA_BOOTSTRAP_SERVERS:kafka:9092}

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: always

deltav:
  flows:
    interface-marking:
      cache-ttl: ${DELTAV_FLOWS_INTERFACE_MARKING_CACHE_TTL:24h}

logging:
  level:
    org.deltav: INFO
    org.opennms: INFO
```

**Note:** The MVP binds to a single Sink topic (Netflow-9) for initial testing. Multi-topic binding (Netflow-5, IPFIX, sFlow) requires either multiple function definitions or a CompositeMessageChannelFactory. This is addressed in a follow-up task after the single-topic pipeline is proven.

- [ ] **Step 5: Verify the module compiles**

```bash
./mvnw -B -pl :org.deltav.flows.flow-enricher -am compile
```
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add core/flow-enricher/src/main/java/org/deltav/flows/enricher/FlowEnricherApplication.java \
        core/flow-enricher/src/main/java/org/deltav/flows/enricher/FlowEnricherConfiguration.java \
        core/flow-enricher/src/main/java/org/deltav/flows/enricher/FlowEnrichmentFunction.java \
        core/flow-enricher/src/main/resources/application.yml
git commit -m "feat(flow-enricher): wire Spring Cloud Stream pipeline with enrichment skeleton"
```

---

### Task 9: Docker Compose integration

**Files:**
- Modify: `opennms-container/delta-v/docker-compose.yml`
- Modify: `opennms-container/delta-v/build.sh` (add flow-enricher to staging)

- [ ] **Step 1: Add flow-enricher service to docker-compose.yml**

Add after the `telemetryd` service block:

```yaml
  flow-enricher:
    profiles: [full]
    image: ${IMAGE_PREFIX:-opennms}/flow-enricher:${VERSION}
    container_name: delta-v-flow-enricher
    hostname: flow-enricher
    depends_on:
      db-init:
        condition: service_completed_successfully
      kafka:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/opennms
      SPRING_DATASOURCE_USERNAME: opennms
      SPRING_DATASOURCE_PASSWORD: opennms
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      DELTAV_FLOWS_SINK_TOPIC: DeltaV.Sink.Telemetry-Netflow-9
    healthcheck:
      test: ["CMD", "curl", "-sf", "http://localhost:8080/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 20s
```

- [ ] **Step 2: Add flow-enricher to build.sh daemon list**

In `build.sh`, add `flow-enricher` to the daemon staging list (the `daemon_names` variable in `do_deltav_images()`). The exact line depends on current build.sh structure — find the daemon list and append.

- [ ] **Step 3: Build and verify Docker image**

```bash
make build
cd opennms-container/delta-v && ./build.sh deltav
```
Expected: `opennms/flow-enricher:0.0.1-SNAPSHOT` image built

- [ ] **Step 4: Start and verify health**

```bash
docker compose --profile full up -d flow-enricher
# Wait for healthy
docker compose --profile full ps | grep flow-enricher
```
Expected: `(healthy)` status

- [ ] **Step 5: Commit**

```bash
git add opennms-container/delta-v/docker-compose.yml opennms-container/delta-v/build.sh
git commit -m "feat(flow-enricher): add Docker Compose service and build integration"
```

---

### Task 10: Verify end-to-end message flow

Manual verification that the pipeline works: send a flow message to Kafka, confirm enriched output on `deltav-flows`.

- [ ] **Step 1: Check flow-enricher logs for Kafka consumer group assignment**

```bash
docker compose --profile full logs flow-enricher | grep -i 'partition\|assigned\|consumer'
```
Expected: Consumer assigned to `DeltaV.Sink.Telemetry-Netflow-9` partitions

- [ ] **Step 2: Produce a test SinkMessage to the Sink topic**

This requires a helper script or manual protobuf message creation. For MVP verification, check that the consumer is polling and the Spring Cloud Stream binding is active:

```bash
docker compose --profile full logs flow-enricher | grep -i 'started\|binding\|cloud'
```
Expected: Spring Cloud Stream bindings started, function `enrichFlows` bound

- [ ] **Step 3: Check deltav-flows topic exists**

```bash
docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --list | grep deltav-flows
```
Expected: `deltav-flows` topic listed (auto-created by Spring Cloud Stream on first output)

- [ ] **Step 4: Commit verification notes**

No code changes — verification is manual. Document results in PR description.

---

## Follow-Up Tasks (After Phase 1 MVP)

These extend the MVP but are not part of this plan:

1. **Multi-topic binding** — Bind to all 4 Sink topics (Netflow-5, Netflow-9, IPFIX, sFlow) with per-topic protocol adapter selection
2. **Full protocol adapter integration** — Wire horizon `Netflow5Adapter`, `Netflow9Adapter`, `IpfixAdapter`, `SFlowAdapter` for real protocol parsing
3. **Classification engine** — Load `DefaultClassificationEngine` from PostgreSQL and apply application classification
4. **Clock skew correction** — Implement timestamp adjustment based on received-vs-reported delta
5. **nodeDeleted event listener** — Subscribe to Kafka fault-events for cache eviction
6. **Phase 2 plan** — flow-aggregator with Kafka Streams windowed aggregation → Elasticsearch
