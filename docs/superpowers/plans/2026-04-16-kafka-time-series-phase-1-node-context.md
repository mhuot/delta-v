# Kafka Time Series Phase 1 — Provisiond Node-Context Change Feed — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the provisiond-side producer for the compacted `deltav-node-context` Kafka topic. Subscribe to node-lifecycle UEIs, coalesce event bursts via a per-nodeId debouncer, publish `NodeContext` protobuf records keyed `{location}@{node_id}`, emit explicit `deleted=true` tombstones on `nodeDeleted`, and bootstrap the full node set on provisiond startup.

**Architecture:** Six Java classes in a new `org.deltav.netmgt.provision.nodecontext` package, gated by `@ConditionalOnProperty("deltav.node-context.enabled", matchIfMissing=true)`. Protobuf schemas extract to a new `core/deltav-kafka-contracts` shared module; both Collectd and Provisiond depend on it. Same PR moves the `deltavNodeContextTopic` `NewTopic` bean from Collectd to Provisiond and drops the Collectd `deltav-timeseries` retention default from 7 → 1 day.

**Tech Stack:** Java 21, Spring Boot 4.0.3, Spring Cloud Stream Kafka binder, `StreamBridge` imperative send, protobuf 3.25.5 via `protobuf-maven-plugin` 0.6.1 + `os-maven-plugin` 1.7.1, Micrometer Prometheus, JUnit 5, Mockito, `spring-cloud-stream-test-binder`, Testcontainers Kafka.

**Design doc:** `docs/superpowers/specs/2026-04-16-kafka-time-series-phase-1-node-context-design.md`
**Next-session prompt:** `docs/superpowers/next-session-prompts/2026-04-16-kafka-time-series-phase-1-provisiond.md`
**Feature branch:** `feature/kafka-ts-phase-1-node-context` (already created; spec commit `d83d256a106` present on branch)

**Critical memory references:**
- `feedback_never_pr_opennms` — delta-v PRs always use `gh pr create --repo pbrane/delta-v`
- `feedback_feature_branches` — never commit directly to `develop`
- `feedback_pull_before_branching` — already pulled before the branch was created
- `feedback_deltav_package_namespace` — new code under `org.deltav.*`, delta-v copyright header
- `feedback_delta_v_uses_mvnw_not_compile_pl` — all builds use `./mvnw`
- `feedback_delta_v_full_reactor_verify` — after cross-module changes, run a full delta-v reactor build
- `feedback_rebuild_all_daemons` — before `build.sh deltav`, rebuild all 12 daemon boot JARs
- `feedback_spring_boot_scan_package_trap` — `scanBasePackages` must include the new package; real-main-class IT is mandatory (Task 12)
- `feedback_bom_import_precedence` — if adding Spring Cloud Stream to provisiond pom, the BOM import order already in place for provisiond is correct
- `project_provisioning_adapters_as_sidecars` — the circular in-process event pattern is documented; Phase 1 does not attempt to fix it
- `project_eventd_eliminated` — events flow through Kafka, not an in-JVM bus

---

## File Structure

### New files

```
core/deltav-kafka-contracts/
├── pom.xml                                            (new module, protobuf-maven-plugin only)
└── src/main/proto/
    ├── deltav-timeseries.proto                        (moved from daemon-boot-collectd)
    └── deltav-node-context.proto                      (moved from daemon-boot-collectd)

core/daemon-boot-provisiond/src/main/java/org/deltav/netmgt/provision/nodecontext/
├── NodeContextProducerConfiguration.java              @Configuration + @ConditionalOnProperty
├── NodeContextChangeFeedListener.java                  @EventListener with 13 UEI handlers
├── NodeContextDebouncer.java                          per-nodeId cancel-and-reschedule
├── NodeContextPublisher.java                          translate + send orchestration
├── NodeToProtobufTranslator.java                      pure function OnmsNode → NodeContext
└── NodeContextBootstrapRunner.java                    SmartLifecycle startup enumeration

core/daemon-boot-provisiond/src/test/java/org/deltav/netmgt/provision/nodecontext/
├── NodeToProtobufTranslatorTest.java                  ~18 unit tests
├── NodeContextPublisherTest.java                      ~14 unit tests (mocked deps)
├── NodeContextDebouncerTest.java                      ~8 unit tests (deterministic scheduler)
├── NodeContextChangeFeedListenerIT.java               ~10 SCS test-binder tests
├── NodeContextKafkaIT.java                            ~6 Testcontainers Kafka tests
└── NodeContextProducerSpringContextIT.java            real-main-class scan-package IT

opennms-container/delta-v/
├── test-node-context-e2e.sh                           E2E smoke test script
```

### Modified files

```
pom.xml                                                 add core/deltav-kafka-contracts to <modules>
core/daemon-boot-collectd/pom.xml                       drop protobuf-maven-plugin + os-maven-plugin extension; add deltav-kafka-contracts dep
core/daemon-boot-collectd/src/main/proto/               deleted (files move to contracts module)
core/daemon-boot-collectd/src/main/java/org/deltav/collectd/timeseries/TimeseriesKafkaPublisherConfiguration.java
                                                        delete deltavNodeContextTopic @Bean; flip @Value default 7 → 1
core/daemon-boot-collectd/src/main/resources/application.yml
                                                        flip DELTAV_TIMESERIES_RETENTION_DAYS default 7 → 1
core/daemon-boot-provisiond/pom.xml                     add deltav-kafka-contracts, spring-cloud-starter-stream-kafka, protobuf-java, spring-cloud-stream-test-binder (test), testcontainers-kafka (test), micrometer-registry-prometheus
core/daemon-boot-provisiond/src/main/java/org/deltav/netmgt/provision/boot/ProvisiondApplication.java
                                                        add "org.deltav.netmgt.provision.nodecontext" to scanBasePackages
core/daemon-boot-provisiond/src/main/resources/application.yml
                                                        add spring.kafka.bootstrap-servers, SCS binding, deltav.node-context.* properties
core/opennms-model-jakarta/src/main/java/org/opennms/netmgt/model/jakarta/dao/NodeDaoJpa.java
                                                        implement findByForeignSource (currently UnsupportedOperationException)
opennms-container/delta-v/README.md                    add Phase 1 env vars, metrics, topic properties, known limitations
```

---

## Task 1: Create `core/deltav-kafka-contracts` Maven module

**Files:**
- Create: `core/deltav-kafka-contracts/pom.xml`
- Modify: `pom.xml` (root) — add module to `<modules>`

- [ ] **Step 1: Create the new module directory + pom.xml**

```bash
mkdir -p core/deltav-kafka-contracts/src/main/proto
```

Write `core/deltav-kafka-contracts/pom.xml` exactly as follows:

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

    <groupId>org.deltav.core</groupId>
    <artifactId>deltav-kafka-contracts</artifactId>
    <name>OpenNMS :: Core :: Delta-V Kafka Contracts</name>
    <description>Shared protobuf schemas for deltav-timeseries and deltav-node-context Kafka topics.
        Consumed by both daemon-boot-collectd (timeseries producer) and daemon-boot-provisiond
        (node-context producer).</description>

    <properties>
        <java.version>21</java.version>
        <protoc.version>3.25.5</protoc.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>com.google.protobuf</groupId>
            <artifactId>protobuf-java</artifactId>
        </dependency>
    </dependencies>

    <build>
        <extensions>
            <extension>
                <groupId>kr.motd.maven</groupId>
                <artifactId>os-maven-plugin</artifactId>
                <version>1.7.1</version>
            </extension>
        </extensions>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <release>${java.version}</release>
                </configuration>
            </plugin>
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
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Add module to root pom.xml**

Locate the `<modules>` block in `/Users/david/development/src/opennms/delta-v/pom.xml` that lists the `core/*` modules. Add in alphabetical order:

```xml
<module>core/deltav-kafka-contracts</module>
```

Place it between `core/daemon-registry` and `core/events` (alphabetically).

- [ ] **Step 3: Verify module is parsed by Maven**

Run:
```bash
./mvnw -pl core/deltav-kafka-contracts validate
```

Expected: `BUILD SUCCESS`, no protobuf compilation yet because no `.proto` files exist.

- [ ] **Step 4: Commit**

```bash
git add core/deltav-kafka-contracts/pom.xml pom.xml
git commit -m "feat(kafka-contracts): create core/deltav-kafka-contracts Maven module

Shared module for Kafka wire contracts. Initial POM only; .proto files move
in the next commit. Gives Collectd and Provisiond a single source of truth
for deltav-timeseries and deltav-node-context schemas."
```

---

## Task 2: Move .proto files from Collectd to contracts module

**Files:**
- Move: `core/daemon-boot-collectd/src/main/proto/deltav-timeseries.proto` → `core/deltav-kafka-contracts/src/main/proto/deltav-timeseries.proto`
- Move: `core/daemon-boot-collectd/src/main/proto/deltav-node-context.proto` → `core/deltav-kafka-contracts/src/main/proto/deltav-node-context.proto`
- Modify: `core/daemon-boot-collectd/pom.xml` — remove `protobuf-maven-plugin` + `os-maven-plugin` extension; add `deltav-kafka-contracts` dep; remove `protobuf-java` dep (transitive now)

- [ ] **Step 1: Move both .proto files using git mv**

```bash
git mv core/daemon-boot-collectd/src/main/proto/deltav-timeseries.proto core/deltav-kafka-contracts/src/main/proto/deltav-timeseries.proto
git mv core/daemon-boot-collectd/src/main/proto/deltav-node-context.proto core/deltav-kafka-contracts/src/main/proto/deltav-node-context.proto
rmdir core/daemon-boot-collectd/src/main/proto
```

- [ ] **Step 2: Update Collectd pom.xml — remove protobuf-maven-plugin, add contracts dep**

In `core/daemon-boot-collectd/pom.xml`:

1. Delete the `<extensions>` block containing `os-maven-plugin` (lines around 525-531).
2. Delete the `<plugin>` block for `org.xolstice.maven.plugins:protobuf-maven-plugin` (lines around 540-554).
3. Delete the `<protoc.version>3.25.5</protoc.version>` property (line around 21) since it's no longer used here.
4. Replace the existing `protobuf-java` dependency:
```xml
<dependency>
    <groupId>com.google.protobuf</groupId>
    <artifactId>protobuf-java</artifactId>
</dependency>
```
with:
```xml
<dependency>
    <groupId>org.deltav.core</groupId>
    <artifactId>deltav-kafka-contracts</artifactId>
    <version>${project.version}</version>
</dependency>
```

- [ ] **Step 3: Build contracts module first to generate classes**

Run:
```bash
./mvnw -pl core/deltav-kafka-contracts -am install -DskipTests
```

Expected: `BUILD SUCCESS`. Verify generated classes exist:
```bash
ls core/deltav-kafka-contracts/target/generated-sources/protobuf/java/org/deltav/timeseries/proto/ | head
```

Expected output includes: `TimeseriesBatch.java`, `NodeContext.java`, `Resource.java`, `Attribute.java`, etc.

- [ ] **Step 4: Build Collectd to verify migration**

Run:
```bash
./mvnw -pl core/daemon-boot-collectd -am install -DskipTests
```

Expected: `BUILD SUCCESS`. No unresolved `org.deltav.timeseries.proto.*` imports in Collectd code.

- [ ] **Step 5: Run Collectd tests to verify behavior unchanged**

```bash
./mvnw -pl core/daemon-boot-collectd test
```

Expected: all tests pass, zero failures. Protobuf-dependent tests in `CollectionSetToProtobufTranslatorTest`, `TimeseriesKafkaPublisherTest`, etc. still find the classes.

- [ ] **Step 6: Commit**

```bash
git add core/deltav-kafka-contracts/src/main/proto/ core/daemon-boot-collectd/pom.xml core/daemon-boot-collectd/src/main/proto
git commit -m "refactor(kafka-contracts): move .proto files from daemon-boot-collectd to shared module

Both daemon-boot-collectd (via dep on deltav-kafka-contracts) and daemon-boot-provisiond
(upcoming) consume generated classes from a single source of truth. Eliminates the
schema-drift risk as the number of daemons touching these topics grows.

No wire-format changes. Generated package org.deltav.timeseries.proto unchanged."
```

---

## Task 3: Add dependencies to `daemon-boot-provisiond`

**Files:**
- Modify: `core/daemon-boot-provisiond/pom.xml`

- [ ] **Step 1: Add production dependencies**

In `core/daemon-boot-provisiond/pom.xml`, within the `<dependencies>` block, add **after** the existing `<!-- Metrics (required by KafkaRpcClientFactory) -->` dependency:

```xml
        <!-- ========== Kafka Time Series Phase 1 (node-context producer) ========== -->
        <dependency>
            <groupId>org.deltav.core</groupId>
            <artifactId>deltav-kafka-contracts</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.google.protobuf</groupId>
            <artifactId>protobuf-java</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-stream-kafka</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
```

- [ ] **Step 2: Add test dependencies**

Within the same `<dependencies>` block, after the existing test section (after `<artifactId>junit-jupiter</artifactId>` with `<scope>test</scope>`), add:

```xml
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-stream-test-binder</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>kafka</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.awaitility</groupId>
            <artifactId>awaitility</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 3: Verify resolution**

```bash
./mvnw -pl core/daemon-boot-provisiond dependency:resolve -DskipTests 2>&1 | tail -20
```

Expected: no unresolved dependencies. If `spring-cloud-starter-stream-kafka` fails to resolve, the provisiond parent BOM may not import Spring Cloud Stream. Check `pom.xml` root — it should already import `spring-cloud-dependencies` (flow-enricher uses it). If missing, add under `<dependencyManagement>` in root pom:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-dependencies</artifactId>
    <version>${spring-cloud.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

(Do NOT add this unless Step 3 shows an unresolved failure — almost certainly already present.)

- [ ] **Step 4: Verify compile with new deps**

```bash
./mvnw -pl core/daemon-boot-provisiond compile -DskipTests
```

Expected: `BUILD SUCCESS`. Generated proto classes (`org.deltav.timeseries.proto.NodeContext`) are now importable from provisiond code.

- [ ] **Step 5: Commit**

```bash
git add core/daemon-boot-provisiond/pom.xml
git commit -m "build(daemon-boot-provisiond): add deps for Kafka node-context producer

Adds deltav-kafka-contracts (protobuf schemas), spring-cloud-starter-stream-kafka
(producer binding), protobuf-java (runtime), micrometer-registry-prometheus
(observability), and test deps (spring-cloud-stream-test-binder, testcontainers-kafka,
awaitility) required by the upcoming Phase 1 node-context producer."
```

---

## Task 4: Implement `NodeDao.findByForeignSource` in `NodeDaoJpa`

**Files:**
- Modify: `core/opennms-model-jakarta/src/main/java/org/opennms/netmgt/model/jakarta/dao/NodeDaoJpa.java`
- Test: `core/opennms-model-jakarta/src/test/java/org/opennms/netmgt/model/jakarta/dao/NodeDaoJpaTest.java` (create if absent; if absent, see Step 0)

Context: Phase 1's `IMPORT_SUCCESSFUL_UEI` handler needs `nodeDao.findByForeignSource(String)` to enumerate all nodes for a given requisition. Currently throws `UnsupportedOperationException`.

- [ ] **Step 0: Check whether test class exists**

```bash
find core/opennms-model-jakarta/src/test -name "NodeDaoJpaTest.java"
```

If the file does not exist, skip writing a test (the DAO is thin, and existing DAO ITs in daemon-boot modules cover behavior). If it exists, proceed with steps 1-4 for TDD. **If absent, jump to Step 5.**

- [ ] **Step 1: Write the failing test (only if NodeDaoJpaTest.java exists)**

Add this method to the existing `NodeDaoJpaTest` class:

```java
@Test
void findByForeignSource_returnsOnlyMatchingNodes() {
    // Arrange: create 3 nodes in 2 foreign sources
    OnmsNode nodeA = buildNode("a", "fs-1", "id-1", "Default");
    OnmsNode nodeB = buildNode("b", "fs-1", "id-2", "Default");
    OnmsNode nodeC = buildNode("c", "fs-other", "id-3", "Default");
    nodeDao.save(nodeA);
    nodeDao.save(nodeB);
    nodeDao.save(nodeC);
    nodeDao.flush();

    // Act
    List<OnmsNode> matches = nodeDao.findByForeignSource("fs-1");

    // Assert
    assertThat(matches).extracting(OnmsNode::getLabel).containsExactlyInAnyOrder("a", "b");
}
```

Use whatever `buildNode` helper already exists in the test class. If no helper, construct `OnmsNode` inline with `setLabel`, `setForeignSource`, `setForeignId`, `setLocation(findByName("Default"))`.

- [ ] **Step 2: Run and verify failure**

```bash
./mvnw -pl core/opennms-model-jakarta test -Dtest=NodeDaoJpaTest#findByForeignSource_returnsOnlyMatchingNodes
```

Expected: FAIL with `UnsupportedOperationException: findByForeignSource() is not used by Alarmd`.

- [ ] **Step 3: Replace the stub in `NodeDaoJpa.java` (lines 150-153)**

Replace the current method body:
```java
@Override
public List<OnmsNode> findByForeignSource(String foreignSource) {
    throw new UnsupportedOperationException("findByForeignSource() is not used by Alarmd");
}
```

with:
```java
@Override
public List<OnmsNode> findByForeignSource(String foreignSource) {
    if (foreignSource == null) {
        return List.of();
    }
    return entityManager
            .createQuery("SELECT n FROM OnmsNode n WHERE n.foreignSource = :fs", OnmsNode.class)
            .setParameter("fs", foreignSource)
            .getResultList();
}
```

- [ ] **Step 4: Run test to verify pass**

```bash
./mvnw -pl core/opennms-model-jakarta test -Dtest=NodeDaoJpaTest#findByForeignSource_returnsOnlyMatchingNodes
```

Expected: PASS. (Skip Steps 1, 2, 4 if Step 0 confirmed no test class; Step 3 still applies.)

- [ ] **Step 5: Commit**

```bash
git add core/opennms-model-jakarta/src/main/java/org/opennms/netmgt/model/jakarta/dao/NodeDaoJpa.java
# add test file if it was touched
git commit -m "feat(opennms-model-jakarta): implement NodeDao.findByForeignSource

Previously stubbed with UnsupportedOperationException (only Alarmd used NodeDaoJpa).
Phase 1 node-context IMPORT_SUCCESSFUL_UEI handler needs this to enumerate all nodes
belonging to a foreign source after a requisition import completes.

Simple JPQL query; no index changes required (foreignSource is already indexed per
the existing schema from horizon's baseline)."
```

---

## Task 5: `NodeToProtobufTranslator` (TDD)

**Files:**
- Create: `core/daemon-boot-provisiond/src/main/java/org/deltav/netmgt/provision/nodecontext/NodeToProtobufTranslator.java`
- Create: `core/daemon-boot-provisiond/src/test/java/org/deltav/netmgt/provision/nodecontext/NodeToProtobufTranslatorTest.java`

Pure function. No Spring, no DB, no I/O. Walks `OnmsNode` + interfaces + services + metadata into a `NodeContext` protobuf.

- [ ] **Step 1: Write the failing test skeleton**

Create `NodeToProtobufTranslatorTest.java`:

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
package org.deltav.netmgt.provision.nodecontext;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.util.List;

import org.deltav.timeseries.proto.NodeContext;
import org.junit.jupiter.api.Test;
import org.opennms.netmgt.model.OnmsCategory;
import org.opennms.netmgt.model.OnmsIpInterface;
import org.opennms.netmgt.model.OnmsMetaData;
import org.opennms.netmgt.model.OnmsMonitoredService;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.OnmsServiceType;
import org.opennms.netmgt.model.monitoringLocations.OnmsMonitoringLocation;

class NodeToProtobufTranslatorTest {

    private final NodeToProtobufTranslator translator = new NodeToProtobufTranslator();

    @Test
    void tombstone_returnsMinimalDeletedRecord() {
        NodeContext tombstone = translator.tombstone(42, "Default", 1_700_000_000L);

        assertThat(tombstone.getNodeId()).isEqualTo(42);
        assertThat(tombstone.getLocation()).isEqualTo("Default");
        assertThat(tombstone.getDeleted()).isTrue();
        assertThat(tombstone.getUpdatedAtMs()).isEqualTo(1_700_000_000L);
        assertThat(tombstone.getNodeLabel()).isEmpty();
        assertThat(tombstone.getForeignSource()).isEmpty();
        assertThat(tombstone.getForeignId()).isEmpty();
        assertThat(tombstone.getCategoriesCount()).isZero();
        assertThat(tombstone.getMetadataCount()).isZero();
    }

    // Additional tests added in subsequent steps
}
```

- [ ] **Step 2: Run to verify failure**

```bash
./mvnw -pl core/daemon-boot-provisiond test -Dtest=NodeToProtobufTranslatorTest
```

Expected: FAIL with `NodeToProtobufTranslator` class not found (unresolved import).

- [ ] **Step 3: Write the minimal translator**

Create `NodeToProtobufTranslator.java`:

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
package org.deltav.netmgt.provision.nodecontext;

import java.util.Map;
import java.util.TreeMap;

import org.deltav.timeseries.proto.InterfaceContext;
import org.deltav.timeseries.proto.NodeContext;
import org.deltav.timeseries.proto.ServiceContext;
import org.opennms.netmgt.model.OnmsCategory;
import org.opennms.netmgt.model.OnmsIpInterface;
import org.opennms.netmgt.model.OnmsMetaData;
import org.opennms.netmgt.model.OnmsMonitoredService;
import org.opennms.netmgt.model.OnmsNode;

/**
 * Pure function. Translates a fully-loaded {@link OnmsNode} (with its
 * interface / service / metadata associations fetched) into a
 * {@link NodeContext} protobuf record. No side effects, no Spring state,
 * no DB access, no logging — caller handles those concerns.
 *
 * <p>Must be called inside a Hibernate session / read-only transaction
 * so LAZY-fetch associations resolve without {@code LazyInitializationException}.</p>
 */
public class NodeToProtobufTranslator {

    /**
     * Translate a full OnmsNode into a NodeContext record with
     * {@code deleted=false}. Caller supplies {@code updatedAtMs} (normally
     * {@code System.currentTimeMillis()}).
     */
    public NodeContext translate(OnmsNode node, long updatedAtMs) {
        NodeContext.Builder b = NodeContext.newBuilder()
                .setNodeId(node.getId() != null ? node.getId() : 0)
                .setLocation(nullSafe(locationName(node)))
                .setNodeLabel(nullSafe(node.getLabel()))
                .setForeignSource(nullSafe(node.getForeignSource()))
                .setForeignId(nullSafe(node.getForeignId()))
                .setUpdatedAtMs(updatedAtMs)
                .setDeleted(false);

        if (node.getCategories() != null) {
            for (OnmsCategory cat : node.getCategories()) {
                if (cat.getName() != null) {
                    b.addCategories(cat.getName());
                }
            }
        }

        // Node-level metadata: keyed by "{context}:{key}" per OnmsMetaData convention
        if (node.getMetaData() != null) {
            for (OnmsMetaData m : node.getMetaData()) {
                b.putMetadata(formatMetadataKey(m), nullSafe(m.getValue()));
            }
        }

        // Interface-scoped metadata
        if (node.getIpInterfaces() != null) {
            for (OnmsIpInterface iface : node.getIpInterfaces()) {
                String ipKey = iface.getIpAddressAsString();
                if (ipKey == null || ipKey.isEmpty()) {
                    continue;
                }
                InterfaceContext.Builder ic = InterfaceContext.newBuilder();
                if (iface.getMetaData() != null) {
                    for (OnmsMetaData m : iface.getMetaData()) {
                        ic.putMetadata(formatMetadataKey(m), nullSafe(m.getValue()));
                    }
                }
                b.putInterfaceMetadata(ipKey, ic.build());

                // Service-scoped metadata: keyed by "{ip}/{serviceName}"
                if (iface.getMonitoredServices() != null) {
                    for (OnmsMonitoredService svc : iface.getMonitoredServices()) {
                        if (svc.getServiceName() == null) {
                            continue;
                        }
                        String svcKey = ipKey + "/" + svc.getServiceName();
                        ServiceContext.Builder sc = ServiceContext.newBuilder();
                        if (svc.getMetaData() != null) {
                            for (OnmsMetaData m : svc.getMetaData()) {
                                sc.putMetadata(formatMetadataKey(m), nullSafe(m.getValue()));
                            }
                        }
                        b.putServiceMetadata(svcKey, sc.build());
                    }
                }
            }
        }

        return b.build();
    }

    /**
     * Build a minimal tombstone NodeContext for a deleted node. Only
     * {@code node_id}, {@code location}, {@code updated_at_ms}, and
     * {@code deleted=true} are set; all other fields are left as proto3
     * defaults.
     */
    public NodeContext tombstone(int nodeId, String location, long updatedAtMs) {
        return NodeContext.newBuilder()
                .setNodeId(nodeId)
                .setLocation(nullSafe(location))
                .setUpdatedAtMs(updatedAtMs)
                .setDeleted(true)
                .build();
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static String locationName(OnmsNode node) {
        return node.getLocation() != null ? node.getLocation().getLocationName() : null;
    }

    private static String formatMetadataKey(OnmsMetaData m) {
        return nullSafe(m.getContext()) + ":" + nullSafe(m.getKey());
    }
}
```

- [ ] **Step 4: Run tombstone test to verify pass**

```bash
./mvnw -pl core/daemon-boot-provisiond test -Dtest=NodeToProtobufTranslatorTest#tombstone_returnsMinimalDeletedRecord
```

Expected: PASS.

- [ ] **Step 5: Add remaining translate-path tests to `NodeToProtobufTranslatorTest.java`**

Append these test methods inside the class. Each is self-contained — no reliance on setUp-level state.

```java
@Test
void translate_emptyNode_populatesIdLocationAndTimestamp() {
    OnmsNode node = new OnmsNode();
    node.setId(10);
    node.setLocation(location("Default"));

    NodeContext ctx = translator.translate(node, 1_700L);

    assertThat(ctx.getNodeId()).isEqualTo(10);
    assertThat(ctx.getLocation()).isEqualTo("Default");
    assertThat(ctx.getUpdatedAtMs()).isEqualTo(1_700L);
    assertThat(ctx.getDeleted()).isFalse();
    assertThat(ctx.getNodeLabel()).isEmpty();
    assertThat(ctx.getCategoriesCount()).isZero();
    assertThat(ctx.getMetadataCount()).isZero();
    assertThat(ctx.getInterfaceMetadataCount()).isZero();
    assertThat(ctx.getServiceMetadataCount()).isZero();
}

@Test
void translate_fullNode_populatesAllFields() {
    OnmsNode node = new OnmsNode();
    node.setId(7);
    node.setLocation(location("Site-A"));
    node.setLabel("host-7.example.com");
    node.setForeignSource("provision-prod");
    node.setForeignId("host-7");
    node.getCategories().add(category("prod"));
    node.getCategories().add(category("linux"));
    node.getMetaData().add(new OnmsMetaData("requisition", "sysLocation", "rack-42"));
    node.getMetaData().add(new OnmsMetaData("snmp", "sysContact", "ops@example.com"));

    NodeContext ctx = translator.translate(node, 1_000L);

    assertThat(ctx.getNodeLabel()).isEqualTo("host-7.example.com");
    assertThat(ctx.getForeignSource()).isEqualTo("provision-prod");
    assertThat(ctx.getForeignId()).isEqualTo("host-7");
    assertThat(ctx.getCategoriesList()).containsExactlyInAnyOrder("prod", "linux");
    assertThat(ctx.getMetadataMap())
            .containsEntry("requisition:sysLocation", "rack-42")
            .containsEntry("snmp:sysContact", "ops@example.com");
}

@Test
void translate_nullLabel_rendersEmptyString() {
    OnmsNode node = new OnmsNode();
    node.setId(1);
    node.setLocation(location("Default"));
    node.setLabel(null);

    NodeContext ctx = translator.translate(node, 0L);

    assertThat(ctx.getNodeLabel()).isEmpty();
}

@Test
void translate_nullLocation_rendersEmptyString() {
    OnmsNode node = new OnmsNode();
    node.setId(1);
    node.setLocation(null);

    NodeContext ctx = translator.translate(node, 0L);

    assertThat(ctx.getLocation()).isEmpty();
}

@Test
void translate_nullCategorySet_emitsNoCategories() {
    OnmsNode node = new OnmsNode();
    node.setId(1);
    node.setLocation(location("Default"));
    node.setCategories(null);

    NodeContext ctx = translator.translate(node, 0L);

    assertThat(ctx.getCategoriesCount()).isZero();
}

@Test
void translate_metadataKeys_formattedAsContextColonKey() throws Exception {
    OnmsNode node = new OnmsNode();
    node.setId(1);
    node.setLocation(location("Default"));
    node.getMetaData().add(new OnmsMetaData("requisition", "alpha", "A"));
    node.getMetaData().add(new OnmsMetaData("snmp", "beta", "B"));
    node.getMetaData().add(new OnmsMetaData("ip", "hostname", "h"));

    NodeContext ctx = translator.translate(node, 0L);

    assertThat(ctx.getMetadataMap())
            .containsEntry("requisition:alpha", "A")
            .containsEntry("snmp:beta", "B")
            .containsEntry("ip:hostname", "h");
}

@Test
void translate_ipv4Interface_keyedByIpString() throws Exception {
    OnmsNode node = new OnmsNode();
    node.setId(1);
    node.setLocation(location("Default"));
    OnmsIpInterface ip = new OnmsIpInterface();
    ip.setIpAddress(InetAddress.getByName("192.0.2.5"));
    ip.getMetaData().add(new OnmsMetaData("iface", "alias", "uplink"));
    node.addIpInterface(ip);

    NodeContext ctx = translator.translate(node, 0L);

    assertThat(ctx.getInterfaceMetadataMap()).containsKey("192.0.2.5");
    assertThat(ctx.getInterfaceMetadataMap().get("192.0.2.5").getMetadataMap())
            .containsEntry("iface:alias", "uplink");
}

@Test
void translate_ipv6Interface_keyedByCanonicalIpString() throws Exception {
    OnmsNode node = new OnmsNode();
    node.setId(1);
    node.setLocation(location("Default"));
    OnmsIpInterface ip = new OnmsIpInterface();
    ip.setIpAddress(InetAddress.getByName("2001:db8::1"));
    node.addIpInterface(ip);

    NodeContext ctx = translator.translate(node, 0L);

    assertThat(ctx.getInterfaceMetadataMap().keySet()).anyMatch(k -> k.contains(":"));
}

@Test
void translate_serviceMetadata_keyedByIpSlashServiceName() throws Exception {
    OnmsNode node = new OnmsNode();
    node.setId(1);
    node.setLocation(location("Default"));
    OnmsIpInterface ip = new OnmsIpInterface();
    ip.setIpAddress(InetAddress.getByName("192.0.2.1"));

    OnmsServiceType icmpType = new OnmsServiceType("ICMP");
    OnmsMonitoredService svc = new OnmsMonitoredService(ip, icmpType);
    svc.getMetaData().add(new OnmsMetaData("svc", "criticality", "high"));
    ip.addMonitoredService(svc);
    node.addIpInterface(ip);

    NodeContext ctx = translator.translate(node, 0L);

    assertThat(ctx.getServiceMetadataMap()).containsKey("192.0.2.1/ICMP");
    assertThat(ctx.getServiceMetadataMap().get("192.0.2.1/ICMP").getMetadataMap())
            .containsEntry("svc:criticality", "high");
}

@Test
void translate_unicodeLabelAndMetadata_roundTripsUtf8() {
    OnmsNode node = new OnmsNode();
    node.setId(1);
    node.setLocation(location("Default"));
    node.setLabel("server-\u00e9-\u4e2d\u6587");
    node.getMetaData().add(new OnmsMetaData("meta", "note", "\u00e9 \u4e2d\u6587"));

    NodeContext ctx = translator.translate(node, 0L);

    assertThat(ctx.getNodeLabel()).isEqualTo("server-\u00e9-\u4e2d\u6587");
    assertThat(ctx.getMetadataMap()).containsEntry("meta:note", "\u00e9 \u4e2d\u6587");
}

@Test
void translate_nullIdFallbackToZero() {
    OnmsNode node = new OnmsNode();
    node.setId(null);
    node.setLocation(location("Default"));

    NodeContext ctx = translator.translate(node, 0L);

    assertThat(ctx.getNodeId()).isZero();
}

@Test
void translate_nullInterfaceIp_skipsInterface() throws Exception {
    OnmsNode node = new OnmsNode();
    node.setId(1);
    node.setLocation(location("Default"));
    OnmsIpInterface ip = new OnmsIpInterface();
    ip.setIpAddress(null);
    node.addIpInterface(ip);

    NodeContext ctx = translator.translate(node, 0L);

    assertThat(ctx.getInterfaceMetadataCount()).isZero();
    assertThat(ctx.getServiceMetadataCount()).isZero();
}

@Test
void translate_largeNode_serializesSuccessfully() throws Exception {
    OnmsNode node = new OnmsNode();
    node.setId(99);
    node.setLocation(location("Default"));
    for (int i = 0; i < 50; i++) {
        node.getMetaData().add(new OnmsMetaData("ctx", "key-" + i, "value-" + i));
    }
    for (int i = 0; i < 100; i++) {
        OnmsIpInterface ip = new OnmsIpInterface();
        ip.setIpAddress(InetAddress.getByName("10.0." + (i / 256) + "." + (i % 256)));
        node.addIpInterface(ip);
    }

    NodeContext ctx = translator.translate(node, 0L);

    assertThat(ctx.toByteArray()).isNotEmpty();
    assertThat(ctx.getInterfaceMetadataCount()).isEqualTo(100);
    assertThat(ctx.getMetadataCount()).isEqualTo(50);
}

// --- helpers ---

private static OnmsMonitoringLocation location(String name) {
    OnmsMonitoringLocation loc = new OnmsMonitoringLocation();
    loc.setLocationName(name);
    return loc;
}

private static OnmsCategory category(String name) {
    OnmsCategory c = new OnmsCategory();
    c.setName(name);
    return c;
}
```

- [ ] **Step 6: Run all translator tests**

```bash
./mvnw -pl core/daemon-boot-provisiond test -Dtest=NodeToProtobufTranslatorTest
```

Expected: 14 tests pass, 0 failures. If any `OnmsNode` / `OnmsIpInterface` mutator does not exist with the expected signature, adapt the test (e.g., `setIpInterfaces(Set)` vs `addIpInterface`) — the horizon model surface is what it is.

- [ ] **Step 7: Commit**

```bash
git add core/daemon-boot-provisiond/src/main/java/org/deltav/netmgt/provision/nodecontext/NodeToProtobufTranslator.java \
        core/daemon-boot-provisiond/src/test/java/org/deltav/netmgt/provision/nodecontext/NodeToProtobufTranslatorTest.java
git commit -m "feat(provisiond/nodecontext): NodeToProtobufTranslator — pure OnmsNode → NodeContext

Walks OnmsNode + categories + metadata + interfaces + services into a NodeContext
protobuf. Service-metadata key format {ip}/{serviceName}. Interface-metadata key
is the canonical InetAddress string (IPv4 dotted-quad or IPv6 canonical form).
Empty strings for null fields (proto3 default semantics).

Also ships tombstone(nodeId, location, updatedAtMs) helper for nodeDeleted
emission.

14 unit tests covering full/empty/unicode/large nodes, metadata key formatting,
IPv4 and IPv6 interface keying, null-id fallback, tombstone shape."
```

---

## Task 6: `NodeContextPublisher` (TDD)

**Files:**
- Create: `core/daemon-boot-provisiond/src/main/java/org/deltav/netmgt/provision/nodecontext/NodeContextPublisher.java`
- Create: `core/daemon-boot-provisiond/src/test/java/org/deltav/netmgt/provision/nodecontext/NodeContextPublisherTest.java`

Orchestrates DB read → translate → serialize → send. Error-isolated; never throws to caller. Instruments every path with Micrometer meters.

- [ ] **Step 1: Write initial failing test**

Create `NodeContextPublisherTest.java`:

```java
/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 * [Standard AGPL v3 header — same 14 lines as the translator file]
 */
package org.deltav.netmgt.provision.nodecontext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.function.Supplier;

import org.deltav.timeseries.proto.NodeContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.dao.api.SessionUtils;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.monitoringLocations.OnmsMonitoringLocation;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;

class NodeContextPublisherTest {

    private StreamBridge streamBridge;
    private NodeDao nodeDao;
    private SessionUtils sessionUtils;
    private NodeToProtobufTranslator translator;
    private SimpleMeterRegistry meters;
    private NodeContextPublisher publisher;

    @BeforeEach
    void setUp() {
        streamBridge = mock(StreamBridge.class);
        nodeDao = mock(NodeDao.class);
        sessionUtils = mock(SessionUtils.class);
        // SessionUtils.withReadOnlyTransaction invokes the supplier inline in tests
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get())
                .when(sessionUtils).withReadOnlyTransaction(any());
        translator = new NodeToProtobufTranslator();
        meters = new SimpleMeterRegistry();
        publisher = new NodeContextPublisher(streamBridge, nodeDao, sessionUtils, translator, meters);
    }

    @Test
    void publishNode_happyPath_sendsOneMessageAndIncrementsChangeCounter() {
        OnmsNode node = new OnmsNode();
        node.setId(42);
        OnmsMonitoringLocation loc = new OnmsMonitoringLocation();
        loc.setLocationName("Default");
        node.setLocation(loc);
        node.setLabel("n42");
        when(nodeDao.get(42)).thenReturn(node);
        when(streamBridge.send(eq("publishNodeContext-out-0"), any(Message.class))).thenReturn(true);

        publisher.publishNode(42);

        ArgumentCaptor<Message<byte[]>> captor = ArgumentCaptor.forClass(Message.class);
        org.mockito.Mockito.verify(streamBridge).send(eq("publishNodeContext-out-0"), captor.capture());
        byte[] key = captor.getValue().getHeaders().get(org.springframework.kafka.support.KafkaHeaders.KEY, byte[].class);
        assertThat(new String(key, java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("Default@42");
        try {
            NodeContext sent = NodeContext.parseFrom(captor.getValue().getPayload());
            assertThat(sent.getNodeId()).isEqualTo(42);
            assertThat(sent.getLocation()).isEqualTo("Default");
            assertThat(sent.getNodeLabel()).isEqualTo("n42");
            assertThat(sent.getDeleted()).isFalse();
        } catch (Exception e) {
            throw new AssertionError(e);
        }

        assertThat(meters.counter("deltav_node_context_records_published_total",
                "location", "Default", "producer", "provisiond", "reason", "change").count())
                .isEqualTo(1.0);
    }
}
```

- [ ] **Step 2: Run to verify failure**

```bash
./mvnw -pl core/daemon-boot-provisiond test -Dtest=NodeContextPublisherTest#publishNode_happyPath_sendsOneMessageAndIncrementsChangeCounter
```

Expected: FAIL (class not found).

- [ ] **Step 3: Implement `NodeContextPublisher.java`**

```java
/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 * [Standard AGPL v3 header]
 */
package org.deltav.netmgt.provision.nodecontext;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.nio.charset.StandardCharsets;

import org.deltav.timeseries.proto.NodeContext;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.dao.api.SessionUtils;
import org.opennms.netmgt.model.OnmsNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 * Orchestrates DB read → translate → serialize → send for the deltav-node-context
 * producer. Error-isolated: every failure path logs + increments a failure
 * counter with a reason tag, and never rethrows to the caller.
 */
public class NodeContextPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(NodeContextPublisher.class);
    private static final String BINDING_NAME = "publishNodeContext-out-0";
    static final int SIZE_WARNING_THRESHOLD_BYTES = 800_000;

    private final StreamBridge streamBridge;
    private final NodeDao nodeDao;
    private final SessionUtils sessionUtils;
    private final NodeToProtobufTranslator translator;
    private final MeterRegistry meters;

    public NodeContextPublisher(StreamBridge streamBridge,
                                 NodeDao nodeDao,
                                 SessionUtils sessionUtils,
                                 NodeToProtobufTranslator translator,
                                 MeterRegistry meters) {
        this.streamBridge = streamBridge;
        this.nodeDao = nodeDao;
        this.sessionUtils = sessionUtils;
        this.translator = translator;
        this.meters = meters;
    }

    /** Publish current state of a node. Use reason="change" for event-driven publishes. */
    public void publishNode(int nodeId) {
        publishNode(nodeId, "change");
    }

    /** Publish with a specific reason tag. Used by bootstrap ("bootstrap") and relocation paths. */
    public void publishNode(int nodeId, String reason) {
        Timer.Sample sample = Timer.start(meters);
        String loc = "";
        try {
            OnmsNode node;
            try {
                node = sessionUtils.withReadOnlyTransaction(() -> nodeDao.get(nodeId));
            } catch (RuntimeException ex) {
                LOG.warn("DB read failed for nodeId={} reason={}", nodeId, reason, ex);
                meters.counter("deltav_node_context_records_failed_total",
                        "location", loc, "reason", "db_read_error").increment();
                return;
            }
            if (node == null) {
                LOG.debug("Node {} not found (likely deleted between event and read)", nodeId);
                meters.counter("deltav_node_context_records_failed_total",
                        "location", loc, "reason", "node_not_found").increment();
                return;
            }
            loc = node.getLocation() != null && node.getLocation().getLocationName() != null
                    ? node.getLocation().getLocationName() : "";

            NodeContext ctx;
            try {
                ctx = translator.translate(node, System.currentTimeMillis());
            } catch (RuntimeException ex) {
                LOG.warn("Translator failed for nodeId={} reason={}", nodeId, reason, ex);
                meters.counter("deltav_node_context_records_failed_total",
                        "location", loc, "reason", "translator_error").increment();
                return;
            }

            sendRecord(ctx, loc, reason);
        } finally {
            sample.stop(Timer.builder("deltav_node_context_publish_duration_seconds")
                    .tags("location", loc, "reason", reason)
                    .register(meters));
        }
    }

    /** Publish an explicit tombstone (deleted=true) for a node. */
    public void publishTombstone(int nodeId, String location) {
        String loc = location != null ? location : "";
        Timer.Sample sample = Timer.start(meters);
        try {
            NodeContext ctx = translator.tombstone(nodeId, loc, System.currentTimeMillis());
            sendRecord(ctx, loc, "tombstone");
        } finally {
            sample.stop(Timer.builder("deltav_node_context_publish_duration_seconds")
                    .tags("location", loc, "reason", "tombstone")
                    .register(meters));
        }
    }

    /**
     * Handle a location change: emit a tombstone at {@code oldLocation}@{nodeId}
     * and a fresh publish at {@code newLocation}@{nodeId}. The fresh-publish
     * reads the DB, which already reflects {@code newLocation} post-commit.
     */
    public void publishRelocation(int nodeId, String oldLocation, String newLocation) {
        String oldLoc = oldLocation != null ? oldLocation : "";
        NodeContext tomb = translator.tombstone(nodeId, oldLoc, System.currentTimeMillis());
        sendRecord(tomb, oldLoc, "relocation_old_key");
        // Fresh publish with the post-commit state at the new key
        publishNode(nodeId, "relocation_new_key");
    }

    private void sendRecord(NodeContext ctx, String loc, String reason) {
        byte[] payload;
        try {
            payload = ctx.toByteArray();
        } catch (RuntimeException ex) {
            LOG.warn("Serialization failed for nodeId={} reason={}", ctx.getNodeId(), reason, ex);
            meters.counter("deltav_node_context_records_failed_total",
                    "location", loc, "reason", "serialization_error").increment();
            return;
        }

        if (payload.length > SIZE_WARNING_THRESHOLD_BYTES) {
            LOG.warn("Oversized NodeContext: {} bytes for nodeId={} location={} (>{} byte warning threshold)",
                    payload.length, ctx.getNodeId(), loc, SIZE_WARNING_THRESHOLD_BYTES);
            meters.counter("deltav_node_context_record_size_warning_total",
                    "location", loc).increment();
        }

        DistributionSummary.builder("deltav_node_context_record_size_bytes")
                .tags("location", loc)
                .register(meters).record(payload.length);

        byte[] key = (loc + "@" + ctx.getNodeId()).getBytes(StandardCharsets.UTF_8);
        Message<byte[]> message = MessageBuilder.withPayload(payload)
                .setHeader(KafkaHeaders.KEY, key)
                .build();

        boolean sent;
        try {
            sent = streamBridge.send(BINDING_NAME, message);
        } catch (RuntimeException ex) {
            LOG.warn("streamBridge.send threw for nodeId={} reason={}", ctx.getNodeId(), reason, ex);
            meters.counter("deltav_node_context_records_failed_total",
                    "location", loc, "reason", "kafka_send_error").increment();
            return;
        }
        if (!sent) {
            LOG.warn("streamBridge.send returned false for nodeId={} reason={}", ctx.getNodeId(), reason);
            meters.counter("deltav_node_context_records_failed_total",
                    "location", loc, "reason", "kafka_send_error").increment();
            return;
        }

        meters.counter("deltav_node_context_records_published_total",
                "location", loc, "producer", "provisiond", "reason", reason).increment();
    }
}
```

- [ ] **Step 4: Run the happy-path test to verify pass**

```bash
./mvnw -pl core/daemon-boot-provisiond test -Dtest=NodeContextPublisherTest#publishNode_happyPath_sendsOneMessageAndIncrementsChangeCounter
```

Expected: PASS.

- [ ] **Step 5: Add remaining publisher tests**

Append these test methods to `NodeContextPublisherTest`:

```java
@Test
void publishNode_nodeNotFound_incrementsFailureCounterNoSend() {
    when(nodeDao.get(99)).thenReturn(null);

    publisher.publishNode(99);

    org.mockito.Mockito.verify(streamBridge, org.mockito.Mockito.never())
            .send(any(String.class), any(Message.class));
    assertThat(meters.counter("deltav_node_context_records_failed_total",
            "location", "", "reason", "node_not_found").count()).isEqualTo(1.0);
}

@Test
void publishNode_daoThrows_dbReadErrorCounter() {
    when(nodeDao.get(1)).thenThrow(new RuntimeException("boom"));

    publisher.publishNode(1);

    assertThat(meters.counter("deltav_node_context_records_failed_total",
            "location", "", "reason", "db_read_error").count()).isEqualTo(1.0);
}

@Test
void publishNode_translatorThrows_translatorErrorCounter() {
    OnmsNode node = new OnmsNode();
    node.setId(5);
    OnmsMonitoringLocation loc = new OnmsMonitoringLocation();
    loc.setLocationName("X");
    node.setLocation(loc);
    when(nodeDao.get(5)).thenReturn(node);

    NodeToProtobufTranslator throwing = mock(NodeToProtobufTranslator.class);
    when(throwing.translate(any(), any(Long.class).longValue() + 0L)).thenThrow(new RuntimeException("tx"));
    // Rewire with a throwing translator
    NodeContextPublisher p2 = new NodeContextPublisher(streamBridge, nodeDao, sessionUtils,
            new NodeToProtobufTranslator() {
                @Override public NodeContext translate(OnmsNode n, long t) {
                    throw new RuntimeException("tx");
                }
            }, meters);

    p2.publishNode(5);

    assertThat(meters.counter("deltav_node_context_records_failed_total",
            "location", "X", "reason", "translator_error").count()).isEqualTo(1.0);
}

@Test
void publishNode_sendReturnsFalse_kafkaSendErrorCounter() {
    OnmsNode node = new OnmsNode();
    node.setId(1);
    OnmsMonitoringLocation loc = new OnmsMonitoringLocation();
    loc.setLocationName("Default");
    node.setLocation(loc);
    when(nodeDao.get(1)).thenReturn(node);
    when(streamBridge.send(eq("publishNodeContext-out-0"), any(Message.class))).thenReturn(false);

    publisher.publishNode(1);

    assertThat(meters.counter("deltav_node_context_records_failed_total",
            "location", "Default", "reason", "kafka_send_error").count()).isEqualTo(1.0);
}

@Test
void publishNode_sendThrows_kafkaSendErrorCounter() {
    OnmsNode node = new OnmsNode();
    node.setId(1);
    OnmsMonitoringLocation loc = new OnmsMonitoringLocation();
    loc.setLocationName("Default");
    node.setLocation(loc);
    when(nodeDao.get(1)).thenReturn(node);
    when(streamBridge.send(eq("publishNodeContext-out-0"), any(Message.class)))
            .thenThrow(new RuntimeException("broker down"));

    publisher.publishNode(1);

    assertThat(meters.counter("deltav_node_context_records_failed_total",
            "location", "Default", "reason", "kafka_send_error").count()).isEqualTo(1.0);
}

@Test
void publishTombstone_sendsDeletedTrueRecord() throws Exception {
    when(streamBridge.send(eq("publishNodeContext-out-0"), any(Message.class))).thenReturn(true);

    publisher.publishTombstone(42, "Default");

    ArgumentCaptor<Message<byte[]>> captor = ArgumentCaptor.forClass(Message.class);
    org.mockito.Mockito.verify(streamBridge).send(eq("publishNodeContext-out-0"), captor.capture());
    NodeContext sent = NodeContext.parseFrom(captor.getValue().getPayload());
    assertThat(sent.getNodeId()).isEqualTo(42);
    assertThat(sent.getLocation()).isEqualTo("Default");
    assertThat(sent.getDeleted()).isTrue();
    assertThat(meters.counter("deltav_node_context_records_published_total",
            "location", "Default", "producer", "provisiond", "reason", "tombstone").count())
            .isEqualTo(1.0);
}

@Test
void publishRelocation_emitsTwoRecordsWithCorrectReasons() throws Exception {
    OnmsNode node = new OnmsNode();
    node.setId(7);
    OnmsMonitoringLocation loc = new OnmsMonitoringLocation();
    loc.setLocationName("Site-B");
    node.setLocation(loc);
    when(nodeDao.get(7)).thenReturn(node);
    when(streamBridge.send(eq("publishNodeContext-out-0"), any(Message.class))).thenReturn(true);

    publisher.publishRelocation(7, "Site-A", "Site-B");

    org.mockito.Mockito.verify(streamBridge, org.mockito.Mockito.times(2))
            .send(eq("publishNodeContext-out-0"), any(Message.class));
    assertThat(meters.counter("deltav_node_context_records_published_total",
            "location", "Site-A", "producer", "provisiond", "reason", "relocation_old_key").count())
            .isEqualTo(1.0);
    assertThat(meters.counter("deltav_node_context_records_published_total",
            "location", "Site-B", "producer", "provisiond", "reason", "relocation_new_key").count())
            .isEqualTo(1.0);
}

@Test
void publishNode_oversizedPayload_warnsButStillSends() {
    // Build a node with enough metadata to exceed 800 KB when serialized
    OnmsNode node = new OnmsNode();
    node.setId(1);
    OnmsMonitoringLocation loc = new OnmsMonitoringLocation();
    loc.setLocationName("Big");
    node.setLocation(loc);
    String filler = "x".repeat(10_000);
    for (int i = 0; i < 100; i++) {
        node.getMetaData().add(new org.opennms.netmgt.model.OnmsMetaData("ctx", "k" + i, filler));
    }
    when(nodeDao.get(1)).thenReturn(node);
    when(streamBridge.send(eq("publishNodeContext-out-0"), any(Message.class))).thenReturn(true);

    publisher.publishNode(1);

    assertThat(meters.counter("deltav_node_context_record_size_warning_total",
            "location", "Big").count()).isEqualTo(1.0);
    org.mockito.Mockito.verify(streamBridge, org.mockito.Mockito.times(1))
            .send(eq("publishNodeContext-out-0"), any(Message.class));
}
```

- [ ] **Step 6: Run all publisher tests**

```bash
./mvnw -pl core/daemon-boot-provisiond test -Dtest=NodeContextPublisherTest
```

Expected: 9 tests pass.

- [ ] **Step 7: Commit**

```bash
git add core/daemon-boot-provisiond/src/main/java/org/deltav/netmgt/provision/nodecontext/NodeContextPublisher.java \
        core/daemon-boot-provisiond/src/test/java/org/deltav/netmgt/provision/nodecontext/NodeContextPublisherTest.java
git commit -m "feat(provisiond/nodecontext): NodeContextPublisher orchestration layer

DB read via SessionUtils.withReadOnlyTransaction → translate → serialize →
streamBridge.send on publishNodeContext-out-0 binding. Error-isolated: every
failure path (DB error, translator error, serialization error, Kafka send
error, node-not-found) logs + increments reason-tagged failure counter and
returns without throwing.

Public API: publishNode(id [, reason]), publishTombstone(id, location),
publishRelocation(id, oldLoc, newLoc). Oversized-record policy matches Phase
0: WARN + counter at >800 KB, still publish (don't drop).

9 unit tests covering every reason tag."
```

---

## Task 7: `NodeContextDebouncer` (TDD)

**Files:**
- Create: `core/daemon-boot-provisiond/src/main/java/org/deltav/netmgt/provision/nodecontext/NodeContextDebouncer.java`
- Create: `core/daemon-boot-provisiond/src/test/java/org/deltav/netmgt/provision/nodecontext/NodeContextDebouncerTest.java`

Per-nodeId cancel-and-reschedule debouncer. Collapses rapid-fire UEIs for the same node into one publish per debounce window.

- [ ] **Step 1: Write the failing debouncer test**

Create `NodeContextDebouncerTest.java`:

```java
/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 * [Standard AGPL v3 header]
 */
package org.deltav.netmgt.provision.nodecontext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NodeContextDebouncerTest {

    private NodeContextPublisher publisher;
    private SimpleMeterRegistry meters;
    private NodeContextDebouncer debouncer;

    @BeforeEach
    void setUp() {
        publisher = mock(NodeContextPublisher.class);
        meters = new SimpleMeterRegistry();
        // Fast debounce (50 ms) so tests run quickly
        debouncer = new NodeContextDebouncer(publisher, 50, 2, meters);
    }

    @AfterEach
    void tearDown() {
        debouncer.flushAndClose();
    }

    @Test
    void enqueueUpdate_firesAfterWindow() {
        debouncer.enqueueUpdate(7);

        verify(publisher, timeout(500)).publishNode(7);
    }

    @Test
    void burstEnqueues_coalescedToOnePublish() {
        for (int i = 0; i < 10; i++) {
            debouncer.enqueueUpdate(42);
        }

        verify(publisher, timeout(500)).publishNode(42);
        // Ensure no second call happens within a generous window
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        verify(publisher, org.mockito.Mockito.times(1)).publishNode(42);

        assertThat(meters.counter("deltav_node_context_debounce_coalesced_total").count())
                .isGreaterThanOrEqualTo(9.0);
    }

    @Test
    void differentNodeIds_independentPublishes() {
        debouncer.enqueueUpdate(1);
        debouncer.enqueueUpdate(2);
        debouncer.enqueueUpdate(3);

        verify(publisher, timeout(500)).publishNode(1);
        verify(publisher, timeout(500)).publishNode(2);
        verify(publisher, timeout(500)).publishNode(3);
    }

    @Test
    void evict_cancelsPendingFuture() throws InterruptedException {
        debouncer.enqueueUpdate(5);
        debouncer.evict(5);

        Thread.sleep(200);  // well past debounce window

        verifyNoInteractions(publisher);
    }

    @Test
    void flushAndClose_firesAllPendingSynchronously() {
        debouncer.enqueueUpdate(10);
        debouncer.enqueueUpdate(11);

        debouncer.flushAndClose();

        verify(publisher).publishNode(10);
        verify(publisher).publishNode(11);
    }

    @Test
    void pendingGauge_reflectsPendingCount() {
        debouncer.enqueueUpdate(1);
        debouncer.enqueueUpdate(2);
        double gauge = meters.find("deltav_node_context_debounce_pending_gauge").gauge().value();
        assertThat(gauge).isBetween(1.0, 2.0);

        await().atMost(Duration.ofSeconds(1)).until(() ->
                meters.find("deltav_node_context_debounce_pending_gauge").gauge().value() == 0.0);
    }

    @Test
    void enqueueAfterClose_noPublish() {
        debouncer.flushAndClose();

        debouncer.enqueueUpdate(99);

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        verifyNoInteractions(publisher);
    }
}
```

- [ ] **Step 2: Run to verify failure**

```bash
./mvnw -pl core/daemon-boot-provisiond test -Dtest=NodeContextDebouncerTest
```

Expected: FAIL (class not found).

- [ ] **Step 3: Implement `NodeContextDebouncer.java`**

```java
/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 * [Standard AGPL v3 header]
 */
package org.deltav.netmgt.provision.nodecontext;

import io.micrometer.core.instrument.MeterRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-nodeId cancel-and-reschedule debouncer. Each {@link #enqueueUpdate(int)}
 * call atomically cancels any pending future for that nodeId and schedules a
 * new one {@code debounceMs} out that invokes {@code publisher.publishNode(nodeId)}.
 * Rapid bursts collapse to a single publish per debounce-quiet window per node.
 *
 * <p>Deletes and relocations do NOT go through the debouncer — call
 * {@link #evict(int)} to cancel any pending work for a node that's being
 * tombstoned or relocated.</p>
 */
public class NodeContextDebouncer {

    private static final Logger LOG = LoggerFactory.getLogger(NodeContextDebouncer.class);

    private final NodeContextPublisher publisher;
    private final long debounceMs;
    private final ScheduledExecutorService executor;
    private final ConcurrentHashMap<Integer, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();
    private final MeterRegistry meters;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public NodeContextDebouncer(NodeContextPublisher publisher,
                                 long debounceMs,
                                 int threads,
                                 MeterRegistry meters) {
        this.publisher = publisher;
        this.debounceMs = debounceMs;
        this.executor = Executors.newScheduledThreadPool(threads, r -> {
            Thread t = new Thread(r, "node-context-debounce");
            t.setDaemon(true);
            return t;
        });
        this.meters = meters;
        meters.gauge("deltav_node_context_debounce_pending_gauge", pending,
                ConcurrentHashMap::size);
    }

    /** Enqueue a debounced publish for the given nodeId. No-op after close. */
    public void enqueueUpdate(int nodeId) {
        if (closed.get()) {
            return;
        }
        pending.compute(nodeId, (id, existing) -> {
            if (existing != null) {
                existing.cancel(false);
                meters.counter("deltav_node_context_debounce_coalesced_total").increment();
            }
            try {
                return executor.schedule(() -> {
                    pending.remove(id);
                    try {
                        publisher.publishNode(id);
                    } catch (Throwable t) {
                        LOG.warn("Debounced publish failed for nodeId={}", id, t);
                    }
                }, debounceMs, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException rex) {
                LOG.debug("Debouncer executor rejected task for nodeId={} (shutting down)", id);
                meters.counter("deltav_node_context_records_failed_total",
                        "location", "", "reason", "debouncer_rejected").increment();
                return null;
            }
        });
    }

    /** Cancel any pending future for the given nodeId. */
    public void evict(int nodeId) {
        ScheduledFuture<?> f = pending.remove(nodeId);
        if (f != null) {
            f.cancel(false);
        }
    }

    /** Flush all pending futures synchronously and shut down the executor. */
    public void flushAndClose() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        // Snapshot pending keys and fire synchronously
        List<Integer> keys = new ArrayList<>(pending.keySet());
        for (Integer id : keys) {
            ScheduledFuture<?> f = pending.remove(id);
            if (f != null) {
                f.cancel(false);
            }
            try {
                publisher.publishNode(id);
            } catch (Throwable t) {
                LOG.warn("Flush publish failed for nodeId={}", id, t);
            }
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
```

- [ ] **Step 4: Run all debouncer tests**

```bash
./mvnw -pl core/daemon-boot-provisiond test -Dtest=NodeContextDebouncerTest
```

Expected: 7 tests pass.

- [ ] **Step 5: Commit**

```bash
git add core/daemon-boot-provisiond/src/main/java/org/deltav/netmgt/provision/nodecontext/NodeContextDebouncer.java \
        core/daemon-boot-provisiond/src/test/java/org/deltav/netmgt/provision/nodecontext/NodeContextDebouncerTest.java
git commit -m "feat(provisiond/nodecontext): NodeContextDebouncer per-nodeId cancel-and-reschedule

ConcurrentHashMap<Integer, ScheduledFuture> + ScheduledExecutorService.
enqueueUpdate atomically cancels any existing future for that nodeId and
schedules a fresh one debounceMs out. Resetting debounce: a stream of events
keeps pushing the fire time forward; only an idle gap of debounceMs releases
the publish.

evict(id) cancels without rescheduling — called by delete/relocation handlers.
flushAndClose fires all pending synchronously on shutdown.

Instruments deltav_node_context_debounce_coalesced_total (cancel+reschedule
count) and deltav_node_context_debounce_pending_gauge (current pending count)
so operators can measure amplification reduction.

7 unit tests covering burst coalescing, multi-node independence, evict,
flush, gauge tracking."
```

---

## Task 8: `NodeContextBootstrapRunner` (TDD)

**Files:**
- Create: `core/daemon-boot-provisiond/src/main/java/org/deltav/netmgt/provision/nodecontext/NodeContextBootstrapRunner.java`
- Create: `core/daemon-boot-provisiond/src/test/java/org/deltav/netmgt/provision/nodecontext/NodeContextBootstrapRunnerTest.java`

`SmartLifecycle` that enumerates every `OnmsNode` on startup and publishes each with `reason="bootstrap"`. Runs after the provisionerLifecycle so the DAOs are ready.

- [ ] **Step 1: Write the failing bootstrap test**

Create `NodeContextBootstrapRunnerTest.java`:

```java
/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 * [Standard AGPL v3 header]
 */
package org.deltav.netmgt.provision.nodecontext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.dao.api.SessionUtils;
import org.opennms.netmgt.model.OnmsNode;

class NodeContextBootstrapRunnerTest {

    private final NodeContextPublisher publisher = mock(NodeContextPublisher.class);
    private final NodeDao nodeDao = mock(NodeDao.class);
    private final SessionUtils sessionUtils = mock(SessionUtils.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    @Test
    void start_publishesAllNodesWithBootstrapReason() {
        // Arrange: 3 nodes, SessionUtils runs supplier inline
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get())
                .when(sessionUtils).withReadOnlyTransaction(any());
        OnmsNode a = node(1); OnmsNode b = node(2); OnmsNode c = node(3);
        when(nodeDao.findAll()).thenReturn(List.of(a, b, c));

        NodeContextBootstrapRunner runner = new NodeContextBootstrapRunner(
                publisher, nodeDao, sessionUtils, meters);
        runner.start();

        verify(publisher).publishNode(1, "bootstrap");
        verify(publisher).publishNode(2, "bootstrap");
        verify(publisher).publishNode(3, "bootstrap");
        assertThat(runner.isRunning()).isTrue();
    }

    @Test
    void start_nodeWithNullId_skipped() {
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get())
                .when(sessionUtils).withReadOnlyTransaction(any());
        OnmsNode withId = node(42);
        OnmsNode withoutId = new OnmsNode();
        when(nodeDao.findAll()).thenReturn(List.of(withId, withoutId));

        new NodeContextBootstrapRunner(publisher, nodeDao, sessionUtils, meters).start();

        verify(publisher).publishNode(42, "bootstrap");
        org.mockito.Mockito.verify(publisher, org.mockito.Mockito.never())
                .publishNode(0, "bootstrap");
    }

    @Test
    void start_enumerationThrows_failureCounterIncremented() {
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get())
                .when(sessionUtils).withReadOnlyTransaction(any());
        when(nodeDao.findAll()).thenThrow(new RuntimeException("db down"));

        NodeContextBootstrapRunner runner = new NodeContextBootstrapRunner(
                publisher, nodeDao, sessionUtils, meters);
        runner.start();

        assertThat(meters.counter("deltav_node_context_records_failed_total",
                "location", "", "reason", "bootstrap_error").count()).isEqualTo(1.0);
    }

    @Test
    void stop_isNoOp() {
        NodeContextBootstrapRunner runner = new NodeContextBootstrapRunner(
                publisher, nodeDao, sessionUtils, meters);
        runner.stop();  // should not throw
        assertThat(runner.isRunning()).isFalse();
    }

    private static OnmsNode node(int id) {
        OnmsNode n = new OnmsNode();
        n.setId(id);
        return n;
    }
}
```

- [ ] **Step 2: Run to verify failure**

```bash
./mvnw -pl core/daemon-boot-provisiond test -Dtest=NodeContextBootstrapRunnerTest
```

Expected: FAIL (class not found).

- [ ] **Step 3: Implement `NodeContextBootstrapRunner.java`**

```java
/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 * [Standard AGPL v3 header]
 */
package org.deltav.netmgt.provision.nodecontext;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.dao.api.SessionUtils;
import org.opennms.netmgt.model.OnmsNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Publishes a NodeContext for every existing node on provisiond startup.
 * Blocks {@code start()} until enumeration completes so the topic reflects
 * the DB before provisiond reports ready.
 *
 * <p>Phase numerically greater than {@code provisiondLifecycle} so DAO and
 * transaction beans are fully initialized before the bootstrap runs.</p>
 */
public class NodeContextBootstrapRunner implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(NodeContextBootstrapRunner.class);
    private static final int PHASE = Integer.MAX_VALUE - 100;

    private final NodeContextPublisher publisher;
    private final NodeDao nodeDao;
    private final SessionUtils sessionUtils;
    private final MeterRegistry meters;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public NodeContextBootstrapRunner(NodeContextPublisher publisher,
                                       NodeDao nodeDao,
                                       SessionUtils sessionUtils,
                                       MeterRegistry meters) {
        this.publisher = publisher;
        this.nodeDao = nodeDao;
        this.sessionUtils = sessionUtils;
        this.meters = meters;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        LOG.info("Node-context bootstrap starting");
        Timer.Sample sample = Timer.start(meters);
        int published = 0;
        try {
            List<OnmsNode> nodes = sessionUtils.withReadOnlyTransaction(() -> nodeDao.findAll());
            for (OnmsNode node : nodes) {
                if (node.getId() == null) {
                    continue;
                }
                try {
                    publisher.publishNode(node.getId(), "bootstrap");
                    published++;
                    if (published % 1000 == 0) {
                        LOG.info("Node-context bootstrap progress: {} records published", published);
                    }
                } catch (Throwable t) {
                    LOG.warn("Bootstrap publish failed for nodeId={}; continuing", node.getId(), t);
                }
            }
            LOG.info("Node-context bootstrap complete: {} records published", published);
        } catch (Throwable t) {
            LOG.error("Node-context bootstrap enumeration failed after {} publishes", published, t);
            meters.counter("deltav_node_context_records_failed_total",
                    "location", "", "reason", "bootstrap_error").increment();
        } finally {
            sample.stop(Timer.builder("deltav_node_context_bootstrap_duration_seconds")
                    .register(meters));
        }
    }

    @Override
    public void stop() {
        running.set(false);
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return PHASE;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }
}
```

- [ ] **Step 4: Run all tests**

```bash
./mvnw -pl core/daemon-boot-provisiond test -Dtest=NodeContextBootstrapRunnerTest
```

Expected: 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add core/daemon-boot-provisiond/src/main/java/org/deltav/netmgt/provision/nodecontext/NodeContextBootstrapRunner.java \
        core/daemon-boot-provisiond/src/test/java/org/deltav/netmgt/provision/nodecontext/NodeContextBootstrapRunnerTest.java
git commit -m "feat(provisiond/nodecontext): NodeContextBootstrapRunner SmartLifecycle

start() enumerates OnmsNode.findAll() inside a read-only transaction and calls
publisher.publishNode(id, \"bootstrap\") for each row. Phase MAX_VALUE-100 so it
runs after provisionerLifecycle completes. Progress log every 1000 nodes.
Bootstrap failures don't halt enumeration (per-node try/catch); enumeration
failure bumps bootstrap_error counter but doesn't throw.

Timer deltav_node_context_bootstrap_duration_seconds records total wall-clock
for operator sizing.

4 unit tests including null-id skip and enumeration-failure counter."
```

---

## Task 9: `NodeContextChangeFeedListener` (TDD)

**Files:**
- Create: `core/daemon-boot-provisiond/src/main/java/org/deltav/netmgt/provision/nodecontext/NodeContextChangeFeedListener.java`
- Create: `core/daemon-boot-provisiond/src/test/java/org/deltav/netmgt/provision/nodecontext/NodeContextChangeFeedListenerTest.java`

Horizon-style `@EventListener` with 13 `@EventHandler` methods. Most UEIs enqueue to the debouncer; delete / relocation bypass and go straight to publisher; `IMPORT_SUCCESSFUL` fans out to every node in the foreign source.

- [ ] **Step 1: Write the failing listener test**

Create `NodeContextChangeFeedListenerTest.java`:

```java
/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 * [Standard AGPL v3 header]
 */
package org.deltav.netmgt.provision.nodecontext;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.events.api.EventConstants;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.xml.event.Event;
import org.opennms.netmgt.xml.event.Parm;
import org.opennms.netmgt.xml.event.Parms;
import org.opennms.netmgt.xml.event.Value;

class NodeContextChangeFeedListenerTest {

    private NodeContextDebouncer debouncer;
    private NodeContextPublisher publisher;
    private NodeDao nodeDao;
    private NodeContextChangeFeedListener listener;

    @BeforeEach
    void setUp() {
        debouncer = mock(NodeContextDebouncer.class);
        publisher = mock(NodeContextPublisher.class);
        nodeDao = mock(NodeDao.class);
        listener = new NodeContextChangeFeedListener(debouncer, publisher, nodeDao,
                new SimpleMeterRegistry());
    }

    @Test
    void onNodeAdded_enqueuesDebouncer() {
        listener.onNodeAdded(eventForNode(EventConstants.NODE_ADDED_EVENT_UEI, 7));
        verify(debouncer).enqueueUpdate(7);
    }

    @Test
    void onNodeUpdated_enqueuesDebouncer() {
        listener.onNodeUpdated(eventForNode(EventConstants.NODE_UPDATED_EVENT_UEI, 8));
        verify(debouncer).enqueueUpdate(8);
    }

    @Test
    void onNodeDeleted_publishesTombstoneAndEvicts() {
        Event e = eventForNode(EventConstants.NODE_DELETED_EVENT_UEI, 9);
        addParm(e, "nodelabel", "n9");  // event parm for context, not the location
        e.setInterfaceAddress(null);
        // location must come from event parms or the listener skips
        addParm(e, "location", "Default");

        listener.onNodeDeleted(e);

        verify(publisher).publishTombstone(9, "Default");
        verify(debouncer).evict(9);
    }

    @Test
    void onNodeDeleted_missingLocation_incrementsFailureCounter() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        NodeContextChangeFeedListener l = new NodeContextChangeFeedListener(
                debouncer, publisher, nodeDao, meters);

        l.onNodeDeleted(eventForNode(EventConstants.NODE_DELETED_EVENT_UEI, 9));

        verifyNoInteractions(publisher);
        assertThat(meters.counter("deltav_node_context_records_failed_total",
                "location", "", "reason", "missing_location").count()).isEqualTo(1.0);
    }

    @Test
    void onNodeLocationChanged_publishesRelocationAndEvicts() {
        Event e = eventForNode(EventConstants.NODE_LOCATION_CHANGED_EVENT_UEI, 10);
        addParm(e, "oldLocation", "Site-A");
        addParm(e, "newLocation", "Site-B");

        listener.onNodeLocationChanged(e);

        verify(publisher).publishRelocation(10, "Site-A", "Site-B");
        verify(debouncer).evict(10);
    }

    @Test
    void onImportSuccessful_enqueuesEveryNodeInForeignSource() {
        Event e = new Event();
        e.setUei(EventConstants.IMPORT_SUCCESSFUL_UEI);
        addParm(e, "foreignSource", "fs-1");
        when(nodeDao.findByForeignSource("fs-1")).thenReturn(List.of(
                nodeWithId(100), nodeWithId(101), nodeWithId(102)));

        listener.onImportSuccessful(e);

        verify(debouncer).enqueueUpdate(100);
        verify(debouncer).enqueueUpdate(101);
        verify(debouncer).enqueueUpdate(102);
    }

    @Test
    void onNodeUpdated_nullNodeid_skippedWithMalformedEventCounter() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        NodeContextChangeFeedListener l = new NodeContextChangeFeedListener(
                debouncer, publisher, nodeDao, meters);
        Event e = new Event();
        e.setUei(EventConstants.NODE_UPDATED_EVENT_UEI);
        e.setNodeid(null);

        l.onNodeUpdated(e);

        verifyNoInteractions(debouncer);
        assertThat(meters.counter("deltav_node_context_records_failed_total",
                "location", "", "reason", "malformed_event").count()).isEqualTo(1.0);
    }

    // --- helpers ---

    private static Event eventForNode(String uei, long nodeid) {
        Event e = new Event();
        e.setUei(uei);
        e.setNodeid(nodeid);
        return e;
    }

    private static void addParm(Event e, String name, String value) {
        Parms parms = e.getParms();
        if (parms == null) {
            parms = new Parms();
            e.setParms(parms);
        }
        Value v = new Value();
        v.setContent(value);
        Parm p = new Parm();
        p.setParmName(name);
        p.setValue(v);
        parms.getParmCollection().add(p);
    }

    private static OnmsNode nodeWithId(int id) {
        OnmsNode n = new OnmsNode();
        n.setId(id);
        return n;
    }
}
```

Add the missing import at the top:
```java
import static org.assertj.core.api.Assertions.assertThat;
```

- [ ] **Step 2: Run to verify failure**

```bash
./mvnw -pl core/daemon-boot-provisiond test -Dtest=NodeContextChangeFeedListenerTest
```

Expected: FAIL (class not found).

- [ ] **Step 3: Implement `NodeContextChangeFeedListener.java`**

```java
/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 * [Standard AGPL v3 header]
 */
package org.deltav.netmgt.provision.nodecontext;

import io.micrometer.core.instrument.MeterRegistry;

import java.util.List;

import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.events.api.EventConstants;
import org.opennms.netmgt.events.api.annotations.EventHandler;
import org.opennms.netmgt.events.api.annotations.EventListener;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.xml.event.Event;
import org.opennms.netmgt.xml.event.Parm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Subscribes to the 13 node-lifecycle UEIs and drives the node-context producer:
 * - Most UEIs enqueue to the debouncer (burst-coalescing).
 * - nodeDeleted bypasses the debouncer and publishes an explicit tombstone;
 *   also evicts any pending debouncer entry so a queued update can't overwrite
 *   the tombstone.
 * - nodeLocationChanged emits a tombstone at the old-location key and a fresh
 *   publish at the new-location key, both synchronous; evicts any pending entry.
 * - IMPORT_SUCCESSFUL_UEI is a catch-all: enqueues every node belonging to the
 *   imported foreignSource so metadata writes that bypassed the other UEIs
 *   still get picked up.
 */
@EventListener(name = "NodeContextChangeFeed", logPrefix = "node-context")
public class NodeContextChangeFeedListener {

    private static final Logger LOG = LoggerFactory.getLogger(NodeContextChangeFeedListener.class);

    private final NodeContextDebouncer debouncer;
    private final NodeContextPublisher publisher;
    private final NodeDao nodeDao;
    private final MeterRegistry meters;

    public NodeContextChangeFeedListener(NodeContextDebouncer debouncer,
                                          NodeContextPublisher publisher,
                                          NodeDao nodeDao,
                                          MeterRegistry meters) {
        this.debouncer = debouncer;
        this.publisher = publisher;
        this.nodeDao = nodeDao;
        this.meters = meters;
    }

    @EventHandler(uei = EventConstants.NODE_ADDED_EVENT_UEI)
    public void onNodeAdded(Event e) { enqueue(e); }

    @EventHandler(uei = EventConstants.NODE_UPDATED_EVENT_UEI)
    public void onNodeUpdated(Event e) { enqueue(e); }

    @EventHandler(uei = EventConstants.NODE_LABEL_CHANGED_EVENT_UEI)
    public void onNodeLabelChanged(Event e) { enqueue(e); }

    @EventHandler(uei = EventConstants.NODE_INFO_CHANGED_EVENT_UEI)
    public void onNodeInfoChanged(Event e) { enqueue(e); }

    @EventHandler(uei = EventConstants.NODE_CATEGORY_MEMBERSHIP_CHANGED_EVENT_UEI)
    public void onNodeCategoryMembershipChanged(Event e) { enqueue(e); }

    @EventHandler(uei = EventConstants.ASSET_INFO_CHANGED_EVENT_UEI)
    public void onAssetInfoChanged(Event e) { enqueue(e); }

    @EventHandler(uei = EventConstants.NODE_GAINED_INTERFACE_EVENT_UEI)
    public void onNodeGainedInterface(Event e) { enqueue(e); }

    @EventHandler(uei = EventConstants.INTERFACE_DELETED_EVENT_UEI)
    public void onInterfaceDeleted(Event e) { enqueue(e); }

    @EventHandler(uei = EventConstants.NODE_GAINED_SERVICE_EVENT_UEI)
    public void onNodeGainedService(Event e) { enqueue(e); }

    @EventHandler(uei = EventConstants.SERVICE_DELETED_EVENT_UEI)
    public void onServiceDeleted(Event e) { enqueue(e); }

    @EventHandler(uei = EventConstants.NODE_DELETED_EVENT_UEI)
    public void onNodeDeleted(Event e) {
        Integer nodeId = nodeIdOrSkip(e);
        if (nodeId == null) return;
        String location = parm(e, "location");
        if (location == null || location.isEmpty()) {
            LOG.warn("nodeDeleted event for nodeId={} missing 'location' parm; skipping tombstone", nodeId);
            meters.counter("deltav_node_context_records_failed_total",
                    "location", "", "reason", "missing_location").increment();
            return;
        }
        publisher.publishTombstone(nodeId, location);
        debouncer.evict(nodeId);
    }

    @EventHandler(uei = EventConstants.NODE_LOCATION_CHANGED_EVENT_UEI)
    public void onNodeLocationChanged(Event e) {
        Integer nodeId = nodeIdOrSkip(e);
        if (nodeId == null) return;
        String oldLocation = parm(e, "oldLocation");
        String newLocation = parm(e, "newLocation");
        if (oldLocation == null || newLocation == null) {
            LOG.warn("nodeLocationChanged event for nodeId={} missing location parms; skipping", nodeId);
            meters.counter("deltav_node_context_records_failed_total",
                    "location", "", "reason", "missing_location").increment();
            return;
        }
        publisher.publishRelocation(nodeId, oldLocation, newLocation);
        debouncer.evict(nodeId);
    }

    @EventHandler(uei = EventConstants.IMPORT_SUCCESSFUL_UEI)
    public void onImportSuccessful(Event e) {
        String foreignSource = parm(e, "foreignSource");
        if (foreignSource == null) {
            LOG.debug("IMPORT_SUCCESSFUL without foreignSource parm; skipping fan-out");
            return;
        }
        try {
            List<OnmsNode> nodes = nodeDao.findByForeignSource(foreignSource);
            for (OnmsNode n : nodes) {
                if (n.getId() != null) {
                    debouncer.enqueueUpdate(n.getId());
                }
            }
        } catch (RuntimeException ex) {
            LOG.warn("IMPORT_SUCCESSFUL fan-out failed for foreignSource={}", foreignSource, ex);
            meters.counter("deltav_node_context_records_failed_total",
                    "location", "", "reason", "db_read_error").increment();
        }
    }

    private void enqueue(Event e) {
        Integer nodeId = nodeIdOrSkip(e);
        if (nodeId == null) return;
        debouncer.enqueueUpdate(nodeId);
    }

    private Integer nodeIdOrSkip(Event e) {
        if (e.getNodeid() == null) {
            LOG.warn("Event {} has null nodeid; skipping", e.getUei());
            meters.counter("deltav_node_context_records_failed_total",
                    "location", "", "reason", "malformed_event").increment();
            return null;
        }
        long longId = e.getNodeid();
        if (longId < Integer.MIN_VALUE || longId > Integer.MAX_VALUE) {
            LOG.warn("Event {} nodeid {} out of int range; skipping", e.getUei(), longId);
            meters.counter("deltav_node_context_records_failed_total",
                    "location", "", "reason", "malformed_event").increment();
            return null;
        }
        return (int) longId;
    }

    private static String parm(Event e, String name) {
        if (e.getParms() == null) return null;
        for (Parm p : e.getParms().getParmCollection()) {
            if (name.equals(p.getParmName()) && p.getValue() != null) {
                return p.getValue().getContent();
            }
        }
        return null;
    }
}
```

- [ ] **Step 4: Run all listener tests**

```bash
./mvnw -pl core/daemon-boot-provisiond test -Dtest=NodeContextChangeFeedListenerTest
```

Expected: 7 tests pass.

- [ ] **Step 5: Commit**

```bash
git add core/daemon-boot-provisiond/src/main/java/org/deltav/netmgt/provision/nodecontext/NodeContextChangeFeedListener.java \
        core/daemon-boot-provisiond/src/test/java/org/deltav/netmgt/provision/nodecontext/NodeContextChangeFeedListenerTest.java
git commit -m "feat(provisiond/nodecontext): NodeContextChangeFeedListener with 13 UEI handlers

@EventListener(name=\"NodeContextChangeFeed\") + @EventHandler methods for the
Q4-settled UEI set: nodeAdded, nodeUpdated, nodeLabelChanged, nodeInfoChanged,
nodeCategoryMembershipChanged, assetInfoChanged, nodeGainedInterface,
interfaceDeleted, nodeGainedService, serviceDeleted (all → debouncer.enqueueUpdate),
plus nodeDeleted (→ publishTombstone + evict), nodeLocationChanged
(→ publishRelocation + evict), and IMPORT_SUCCESSFUL_UEI (→ fan-out via
nodeDao.findByForeignSource).

Null/out-of-range nodeid increments malformed_event counter. nodeDeleted
missing location parm increments missing_location counter.

7 unit tests covering each handler path."
```

---

## Task 10: `NodeContextProducerConfiguration` + Collectd handoff

**Files:**
- Create: `core/daemon-boot-provisiond/src/main/java/org/deltav/netmgt/provision/nodecontext/NodeContextProducerConfiguration.java`
- Modify: `core/daemon-boot-collectd/src/main/java/org/deltav/collectd/timeseries/TimeseriesKafkaPublisherConfiguration.java` — delete `deltavNodeContextTopic` bean

- [ ] **Step 1: Create `NodeContextProducerConfiguration.java`**

```java
/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 * [Standard AGPL v3 header]
 */
package org.deltav.netmgt.provision.nodecontext;

import io.micrometer.core.instrument.MeterRegistry;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.dao.api.SessionUtils;
import org.opennms.netmgt.events.api.AnnotationBasedEventListenerAdapter;
import org.opennms.netmgt.events.api.EventSubscriptionService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Feature-flagged configuration for the provisiond → deltav-node-context producer.
 *
 * <p>When {@code deltav.node-context.enabled=true} (default — the flag is
 * matchIfMissing=true so the feature is on unless explicitly disabled), the
 * six producer beans wire up and provisiond starts publishing on every
 * node-lifecycle UEI plus one bootstrap pass.</p>
 */
@Configuration
@ConditionalOnProperty(name = "deltav.node-context.enabled", havingValue = "true", matchIfMissing = true)
public class NodeContextProducerConfiguration {

    @Bean
    public NodeToProtobufTranslator nodeToProtobufTranslator() {
        return new NodeToProtobufTranslator();
    }

    @Bean
    public NodeContextPublisher nodeContextPublisher(StreamBridge streamBridge,
                                                     NodeDao nodeDao,
                                                     SessionUtils sessionUtils,
                                                     NodeToProtobufTranslator translator,
                                                     MeterRegistry meterRegistry) {
        return new NodeContextPublisher(streamBridge, nodeDao, sessionUtils, translator, meterRegistry);
    }

    @Bean
    public NodeContextDebouncer nodeContextDebouncer(
            NodeContextPublisher publisher,
            @Value("${deltav.node-context.debounce-ms:250}") long debounceMs,
            @Value("${deltav.node-context.debounce-threads:2}") int debounceThreads,
            MeterRegistry meterRegistry) {
        return new NodeContextDebouncer(publisher, debounceMs, debounceThreads, meterRegistry);
    }

    @Bean
    public NodeContextChangeFeedListener nodeContextChangeFeedListener(
            NodeContextDebouncer debouncer,
            NodeContextPublisher publisher,
            NodeDao nodeDao,
            MeterRegistry meterRegistry) {
        return new NodeContextChangeFeedListener(debouncer, publisher, nodeDao, meterRegistry);
    }

    @Bean
    public AnnotationBasedEventListenerAdapter nodeContextChangeFeedEventListener(
            NodeContextChangeFeedListener listener,
            @Qualifier("kafkaEventSubscriptionService") EventSubscriptionService eventSubscriptionService) {
        AnnotationBasedEventListenerAdapter adapter = new AnnotationBasedEventListenerAdapter();
        adapter.setAnnotatedListener(listener);
        adapter.setEventSubscriptionService(eventSubscriptionService);
        return adapter;
    }

    @Bean
    public NodeContextBootstrapRunner nodeContextBootstrapRunner(
            NodeContextPublisher publisher,
            NodeDao nodeDao,
            SessionUtils sessionUtils,
            MeterRegistry meterRegistry) {
        return new NodeContextBootstrapRunner(publisher, nodeDao, sessionUtils, meterRegistry);
    }

    /**
     * Moved from daemon-boot-collectd/TimeseriesKafkaPublisherConfiguration.
     * {@code KafkaAdmin.createTopics} is idempotent so the handoff is race-free.
     */
    @Bean
    public NewTopic deltavNodeContextTopic(
            @Value("${deltav.node-context.partitions:8}") int partitions,
            @Value("${deltav.node-context.replication-factor:1}") short replicationFactor) {
        return TopicBuilder.name("deltav-node-context")
                .partitions(partitions)
                .replicas(replicationFactor)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT)
                .config(TopicConfig.RETENTION_MS_CONFIG, "-1")
                .config(TopicConfig.MIN_COMPACTION_LAG_MS_CONFIG, "60000")
                .config(TopicConfig.DELETE_RETENTION_MS_CONFIG, "86400000")
                .build();
    }
}
```

- [ ] **Step 2: Delete the `deltavNodeContextTopic` bean from Collectd**

In `core/daemon-boot-collectd/src/main/java/org/deltav/collectd/timeseries/TimeseriesKafkaPublisherConfiguration.java`, delete lines ~84-103 (the `@Bean public NewTopic deltavNodeContextTopic(...)` method and its Javadoc). Keep everything else untouched.

- [ ] **Step 3: Verify Collectd still builds and tests pass**

```bash
./mvnw -pl core/daemon-boot-collectd test
```

Expected: all tests pass. (The Collectd Testcontainers IT may still reference `deltav-node-context` — if so, adjust the test to expect only the `deltav-timeseries` topic is provisioned by Collectd. Grep for `deltav-node-context` under `core/daemon-boot-collectd/src/test/` first; if present, remove that assertion since the topic now provisions from provisiond.)

```bash
grep -r "deltav-node-context" core/daemon-boot-collectd/src/test/ || echo "no references"
```

If references are found, remove those assertions from the affected test.

- [ ] **Step 4: Build provisiond to catch any missing wiring**

```bash
./mvnw -pl core/daemon-boot-provisiond compile -DskipTests
```

Expected: `BUILD SUCCESS`. No unresolved bean refs.

- [ ] **Step 5: Commit**

```bash
git add core/daemon-boot-provisiond/src/main/java/org/deltav/netmgt/provision/nodecontext/NodeContextProducerConfiguration.java \
        core/daemon-boot-collectd/src/main/java/org/deltav/collectd/timeseries/TimeseriesKafkaPublisherConfiguration.java
git commit -m "feat(provisiond/nodecontext): NodeContextProducerConfiguration + topic handoff

@Configuration class wires all seven Phase 1 beans (translator, publisher,
debouncer, listener, event-subscription adapter, bootstrap runner, NewTopic)
behind @ConditionalOnProperty(\"deltav.node-context.enabled\",
matchIfMissing=true).

The deltavNodeContextTopic NewTopic bean moves here from Collectd's
TimeseriesKafkaPublisherConfiguration (Phase 0 had it dual-owned; Phase 1
consolidates ownership in provisiond). KafkaAdmin.createTopics is idempotent
so the handoff is race-free."
```

---

## Task 11: Provisiond application.yml + scanBasePackages

**Files:**
- Modify: `core/daemon-boot-provisiond/src/main/java/org/deltav/netmgt/provision/boot/ProvisiondApplication.java`
- Modify: `core/daemon-boot-provisiond/src/main/resources/application.yml`

- [ ] **Step 1: Add the new package to `scanBasePackages`**

In `ProvisiondApplication.java`, change the existing `scanBasePackages` array to include the new package:

```java
@SpringBootApplication(
    scanBasePackages = {
        "org.deltav.core.daemon.common",
        "org.opennms.netmgt.model.jakarta.dao",
        "org.deltav.netmgt.provision.boot",
        "org.deltav.netmgt.provision.nodecontext"
    }
    // Do NOT exclude HibernateJpaAutoConfiguration — Provisiond needs JPA
)
@EnableScheduling
public class ProvisiondApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProvisiondApplication.class, args);
    }
}
```

Per `feedback_spring_boot_scan_package_trap`: this one-line change is the core fix. Without it, `@Configuration` classes under `org.deltav.netmgt.provision.nodecontext` are invisible and the producer silently does nothing.

- [ ] **Step 2: Add Kafka + SCS config + node-context properties to application.yml**

In `core/daemon-boot-provisiond/src/main/resources/application.yml`, append after the existing `opennms:` / `logging:` blocks (preserve existing structure):

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

If the top-level `spring:` block already exists in the file (which it does — it holds `datasource`, `jpa`, `quartz`), merge the `kafka` + `cloud` keys INTO that existing block rather than duplicating the `spring:` key.

- [ ] **Step 3: Verify provisiond assembles**

```bash
./mvnw -pl core/daemon-boot-provisiond package -DskipTests
```

Expected: `BUILD SUCCESS`; YAML parses; boot jar is produced.

- [ ] **Step 4: Commit**

```bash
git add core/daemon-boot-provisiond/src/main/java/org/deltav/netmgt/provision/boot/ProvisiondApplication.java \
        core/daemon-boot-provisiond/src/main/resources/application.yml
git commit -m "feat(provisiond/nodecontext): wire scanBasePackages + application.yml

Adds org.deltav.netmgt.provision.nodecontext to scanBasePackages (mandatory
per feedback_spring_boot_scan_package_trap; without it the producer would be
silently disabled even with DELTAV_NODE_CONTEXT_ENABLED=true).

Adds spring.kafka.bootstrap-servers (#170 cascade lesson — KafkaAdmin can't
provision the topic without it), spring.cloud.stream binding for
publishNodeContext-out-0, and deltav.node-context.* tunables (enabled
default true, partitions 8, debounce-ms 250, debounce-threads 2)."
```

---

## Task 12: Real-main-class IT — scan-package trap coverage

**Files:**
- Create: `core/daemon-boot-provisiond/src/test/java/org/deltav/netmgt/provision/nodecontext/NodeContextProducerSpringContextIT.java`

Mandatory per `feedback_spring_boot_scan_package_trap`. Asserts every Phase 1 bean exists when `ProvisiondApplication` is the declared config class.

- [ ] **Step 1: Write the IT**

```java
/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 * [Standard AGPL v3 header]
 */
package org.deltav.netmgt.provision.nodecontext;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.opennms.netmgt.events.api.AnnotationBasedEventListenerAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

/**
 * Real-main-class Spring context IT. Declares ProvisiondApplication as the
 * @SpringBootTest.classes so SpringApplication.run()'s component scan walks
 * the exact same path as production — if scanBasePackages is missing the
 * "org.deltav.netmgt.provision.nodecontext" entry, the assertions below fail
 * and the scan-package trap is caught at build time rather than at runtime
 * (cf. feedback_spring_boot_scan_package_trap).
 */
@SpringBootTest(
        classes = org.deltav.netmgt.provision.boot.ProvisiondApplication.class,
        properties = {
                "deltav.node-context.enabled=true",
                "spring.autoconfigure.exclude=" +
                        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration",
                "spring.kafka.bootstrap-servers=localhost:1"  // irrelevant for context load
        }
)
@TestPropertySource(properties = "spring.cloud.stream.kafka.binder.brokers=localhost:1")
class NodeContextProducerSpringContextIT {

    @Autowired
    private ApplicationContext ctx;

    @Test
    void allProducerBeansWiredWhenFlagOn() {
        assertThat(ctx.getBean(NodeToProtobufTranslator.class)).isNotNull();
        assertThat(ctx.getBean(NodeContextPublisher.class)).isNotNull();
        assertThat(ctx.getBean(NodeContextDebouncer.class)).isNotNull();
        assertThat(ctx.getBean(NodeContextChangeFeedListener.class)).isNotNull();
        assertThat(ctx.getBean(NodeContextBootstrapRunner.class)).isNotNull();
        assertThat(ctx.getBean("nodeContextChangeFeedEventListener", AnnotationBasedEventListenerAdapter.class))
                .isNotNull();
        assertThat(ctx.getBean("deltavNodeContextTopic", NewTopic.class)).isNotNull();
    }
}
```

- [ ] **Step 2: Run**

```bash
./mvnw -pl core/daemon-boot-provisiond test -Dtest=NodeContextProducerSpringContextIT
```

Expected: PASS. If any bean is missing from the context, the test fails immediately — that's the scan-package trap being caught. If the context fails to load due to missing DAO beans (NodeDao, SessionUtils), add their auto-configuration exclusions or mocks as needed. Realistically, since we excluded `HibernateJpaAutoConfiguration`, `NodeDao` won't auto-wire — the test will need a minimal `@TestConfiguration` that stubs `NodeDao`, `SessionUtils`, `EventSubscriptionService`.

If the naive form fails, add this inner configuration inside the IT class:

```java
import org.mockito.Mockito;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.dao.api.SessionUtils;
import org.opennms.netmgt.events.api.EventSubscriptionService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Import(NodeContextProducerSpringContextIT.StubBeans.class)
// ... existing @SpringBootTest annotation ...
class NodeContextProducerSpringContextIT {

    @TestConfiguration
    static class StubBeans {
        @Bean @Primary NodeDao nodeDao() { return Mockito.mock(NodeDao.class); }
        @Bean @Primary SessionUtils sessionUtils() { return Mockito.mock(SessionUtils.class); }
        @Bean(name = "kafkaEventSubscriptionService") EventSubscriptionService subService() {
            return Mockito.mock(EventSubscriptionService.class);
        }
    }

    // ... test methods ...
}
```

- [ ] **Step 3: Commit**

```bash
git add core/daemon-boot-provisiond/src/test/java/org/deltav/netmgt/provision/nodecontext/NodeContextProducerSpringContextIT.java
git commit -m "test(provisiond/nodecontext): real-main-class Spring context IT

Mandatory scan-package trap coverage (feedback_spring_boot_scan_package_trap).
Uses @SpringBootTest(classes = ProvisiondApplication.class) so SpringApplication.run()
walks the exact same component-scan path as production. Asserts every Phase 1
bean exists when deltav.node-context.enabled=true.

Stubs NodeDao, SessionUtils, and kafkaEventSubscriptionService so the context
loads without a real DB or Kafka broker. Test isolates the scan-package +
bean-wiring question from the DAO/Kafka infrastructure tests."
```

---

## Task 13: Testcontainers Kafka IT

**Files:**
- Create: `core/daemon-boot-provisiond/src/test/java/org/deltav/netmgt/provision/nodecontext/NodeContextKafkaIT.java`

Real Kafka broker. Validates topic config at the broker level + compaction round-trip + partition-key stickiness + tombstone flow.

- [ ] **Step 1: Write the IT**

```java
/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 * [Standard AGPL v3 header]
 */
package org.deltav.netmgt.provision.nodecontext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.deltav.timeseries.proto.NodeContext;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class NodeContextKafkaIT {

    @Container
    private static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.6.1"));

    @Test
    void topicProvisionsWithCompactionConfig() throws Exception {
        provisionTopic();

        try (AdminClient admin = adminClient()) {
            DescribeTopicsResult describe = admin.describeTopics(List.of("deltav-node-context"));
            TopicDescription td = describe.topicNameValues().get("deltav-node-context").get();
            assertThat(td.partitions()).hasSize(8);

            ConfigResource cr = new ConfigResource(ConfigResource.Type.TOPIC, "deltav-node-context");
            Config cfg = admin.describeConfigs(List.of(cr)).all().get().get(cr);
            assertThat(entryValue(cfg, "cleanup.policy")).isEqualTo("compact");
            assertThat(entryValue(cfg, "min.compaction.lag.ms")).isEqualTo("60000");
            assertThat(entryValue(cfg, "delete.retention.ms")).isEqualTo("86400000");
        }
    }

    @Test
    void compactionRetainsLatestPerKey() throws Exception {
        provisionTopic();

        NodeContext v1 = NodeContext.newBuilder().setNodeId(42).setLocation("Default")
                .setNodeLabel("v1").setUpdatedAtMs(1L).build();
        NodeContext v2 = NodeContext.newBuilder().setNodeId(42).setLocation("Default")
                .setNodeLabel("v2").setUpdatedAtMs(2L).build();

        try (KafkaProducer<byte[], byte[]> p = producer()) {
            byte[] key = "Default@42".getBytes(StandardCharsets.UTF_8);
            p.send(new ProducerRecord<>("deltav-node-context", key, v1.toByteArray())).get();
            p.send(new ProducerRecord<>("deltav-node-context", key, v2.toByteArray())).get();
        }

        // Consume from offset 0 — both records still visible (compaction lag 60s, we don't wait)
        try (KafkaConsumer<byte[], byte[]> c = consumer()) {
            c.subscribe(Collections.singleton("deltav-node-context"));
            Set<String> labels = new HashSet<>();
            await().atMost(Duration.ofSeconds(15)).until(() -> {
                ConsumerRecords<byte[], byte[]> records = c.poll(Duration.ofMillis(200));
                for (ConsumerRecord<byte[], byte[]> r : records) {
                    labels.add(NodeContext.parseFrom(r.value()).getNodeLabel());
                }
                return labels.contains("v1") && labels.contains("v2");
            });
            assertThat(labels).containsExactlyInAnyOrder("v1", "v2");
        }
    }

    @Test
    void tombstoneIsExplicitDeletedRecord() throws Exception {
        provisionTopic();

        NodeContext live = NodeContext.newBuilder().setNodeId(7).setLocation("Default")
                .setNodeLabel("live").setUpdatedAtMs(1L).build();
        NodeContext tomb = NodeContext.newBuilder().setNodeId(7).setLocation("Default")
                .setUpdatedAtMs(2L).setDeleted(true).build();

        try (KafkaProducer<byte[], byte[]> p = producer()) {
            byte[] key = "Default@7".getBytes(StandardCharsets.UTF_8);
            p.send(new ProducerRecord<>("deltav-node-context", key, live.toByteArray())).get();
            p.send(new ProducerRecord<>("deltav-node-context", key, tomb.toByteArray())).get();
        }

        try (KafkaConsumer<byte[], byte[]> c = consumer()) {
            c.subscribe(Collections.singleton("deltav-node-context"));
            boolean sawTombstone = await().atMost(Duration.ofSeconds(15))
                    .until(() -> {
                        ConsumerRecords<byte[], byte[]> records = c.poll(Duration.ofMillis(200));
                        for (ConsumerRecord<byte[], byte[]> r : records) {
                            NodeContext ctx = NodeContext.parseFrom(r.value());
                            if (ctx.getDeleted()) return true;
                        }
                        return false;
                    }, java.util.function.Predicate.isEqual(true));
            assertThat(sawTombstone).isTrue();
        }
    }

    @Test
    void partitionAssignmentUsesAllPartitions() throws Exception {
        provisionTopic();

        try (KafkaProducer<byte[], byte[]> p = producer()) {
            for (int i = 0; i < 64; i++) {
                byte[] key = ("Default@" + i).getBytes(StandardCharsets.UTF_8);
                NodeContext ctx = NodeContext.newBuilder().setNodeId(i).build();
                p.send(new ProducerRecord<>("deltav-node-context", key, ctx.toByteArray())).get();
            }
        }

        Set<Integer> partitionsSeen = new HashSet<>();
        try (KafkaConsumer<byte[], byte[]> c = consumer()) {
            c.subscribe(Collections.singleton("deltav-node-context"));
            await().atMost(Duration.ofSeconds(15)).until(() -> {
                ConsumerRecords<byte[], byte[]> records = c.poll(Duration.ofMillis(200));
                records.forEach(r -> partitionsSeen.add(r.partition()));
                return partitionsSeen.size() >= 4;  // expect at least 4 of 8 partitions used for 64 keys
            });
        }
        assertThat(partitionsSeen).hasSizeGreaterThanOrEqualTo(4);
    }

    // --- helpers ---

    private static void provisionTopic() throws Exception {
        Map<String, String> configs = new HashMap<>();
        configs.put("cleanup.policy", "compact");
        configs.put("retention.ms", "-1");
        configs.put("min.compaction.lag.ms", "60000");
        configs.put("delete.retention.ms", "86400000");
        try (AdminClient admin = adminClient()) {
            admin.createTopics(List.of(new org.apache.kafka.clients.admin.NewTopic(
                    "deltav-node-context", 8, (short) 1).configs(configs))).all().get();
        }
    }

    private static AdminClient adminClient() {
        Properties p = new Properties();
        p.put("bootstrap.servers", KAFKA.getBootstrapServers());
        return AdminClient.create(p);
    }

    private static KafkaProducer<byte[], byte[]> producer() {
        Properties p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        p.put(ProducerConfig.ACKS_CONFIG, "all");
        p.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        return new KafkaProducer<>(p);
    }

    private static KafkaConsumer<byte[], byte[]> consumer() {
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        p.put(ConsumerConfig.GROUP_ID_CONFIG, "node-context-it-" + System.nanoTime());
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new KafkaConsumer<>(p);
    }

    private static String entryValue(Config cfg, String name) {
        ConfigEntry e = cfg.get(name);
        return e != null ? e.value() : null;
    }
}
```

- [ ] **Step 2: Run**

```bash
./mvnw -pl core/daemon-boot-provisiond test -Dtest=NodeContextKafkaIT
```

Expected: 4 tests pass. First run takes ~30 s due to Testcontainers pulling the Kafka image.

- [ ] **Step 3: Commit**

```bash
git add core/daemon-boot-provisiond/src/test/java/org/deltav/netmgt/provision/nodecontext/NodeContextKafkaIT.java
git commit -m "test(provisiond/nodecontext): Testcontainers Kafka IT

Real broker validation:
- Topic provisions with cleanup.policy=compact, min.compaction.lag.ms=60000,
  delete.retention.ms=86400000, 8 partitions (describeConfigs assertion)
- Multiple records same key both reachable within compaction-lag window
- Explicit deleted=true tombstone visible to consumers
- 64 keys across 8 partitions exercises at least 4 partitions

4 tests. First run pulls apache/kafka:3.6.1 image (~30 s)."
```

---

## Task 14: Collectd retention default 7 → 1 day

**Files:**
- Modify: `core/daemon-boot-collectd/src/main/resources/application.yml`
- Modify: `core/daemon-boot-collectd/src/main/java/org/deltav/collectd/timeseries/TimeseriesKafkaPublisherConfiguration.java`

- [ ] **Step 1: Flip the YAML default**

In `core/daemon-boot-collectd/src/main/resources/application.yml`, locate the `deltav.timeseries.retention-days` line. Change:
```yaml
retention-days: ${DELTAV_TIMESERIES_RETENTION_DAYS:7}
```
to:
```yaml
retention-days: ${DELTAV_TIMESERIES_RETENTION_DAYS:1}
```

- [ ] **Step 2: Flip the `@Value` default in the config class**

In `TimeseriesKafkaPublisherConfiguration.java`, change:
```java
@Value("${deltav.timeseries.retention-days:7}") int retentionDays
```
to:
```java
@Value("${deltav.timeseries.retention-days:1}") int retentionDays
```

- [ ] **Step 3: Verify Collectd tests still pass**

```bash
./mvnw -pl core/daemon-boot-collectd test
```

Expected: all tests pass. If any Collectd test asserts the 7-day value literally, update it to 1.

- [ ] **Step 4: Commit**

```bash
git add core/daemon-boot-collectd/src/main/resources/application.yml \
        core/daemon-boot-collectd/src/main/java/org/deltav/collectd/timeseries/TimeseriesKafkaPublisherConfiguration.java
git commit -m "fix(collectd/timeseries): default deltav-timeseries retention 7 → 1 day

At production scale (10k nodes × 288 polls/day × ~2 KB/record) the difference
is 40 GB → 5.76 GB steady state. Operator override via
DELTAV_TIMESERIES_RETENTION_DAYS still works. Phase 0 shipped today so no
operator has yet relied on the 7-day default."
```

---

## Task 15: Operator README update

**Files:**
- Modify: `opennms-container/delta-v/README.md`

- [ ] **Step 1: Add a new section after the existing `deltav-timeseries` documentation**

Search for the `deltav-timeseries` section heading in `opennms-container/delta-v/README.md`. Immediately after it (or at the end of the "Environment Variables" / equivalent section), add:

```markdown
### Node-Context Change Feed (Phase 1)

Provisiond publishes a compacted `deltav-node-context` Kafka topic keyed
`{location}@{node_id}`. Every node-lifecycle event (add, update, delete,
label/location/category/info/asset change) produces a `NodeContext`
protobuf record; `nodeDeleted` produces an explicit `deleted=true`
tombstone. On every provisiond restart, the full node set is re-published
(compaction absorbs duplicates).

**Feature flag:**

| Env var | Default | Purpose |
|---|---|---|
| `DELTAV_NODE_CONTEXT_ENABLED` | `true` | Kill switch. Flip to `false` + restart provisiond to remove all producer beans. |
| `DELTAV_NODE_CONTEXT_PARTITIONS` | `8` | Topic partition count. Used at first topic creation only (KafkaAdmin is idempotent; existing topic keeps its original partition count). |
| `DELTAV_NODE_CONTEXT_REPLICATION_FACTOR` | `1` (dev) / `3` (prod) | Topic replication factor. |
| `DELTAV_NODE_CONTEXT_DEBOUNCE_MS` | `250` | Per-nodeId debounce window. Collapses bursts of UEIs for the same node into one publish. |
| `DELTAV_NODE_CONTEXT_DEBOUNCE_THREADS` | `2` | Debouncer executor pool size. |

**Metrics (on `/actuator/prometheus`):**

- `deltav_node_context_records_published_total{location, producer="provisiond", reason}` — `reason` ∈ `change`, `bootstrap`, `tombstone`, `relocation_old_key`, `relocation_new_key`
- `deltav_node_context_records_failed_total{location, reason}` — `db_read_error`, `translator_error`, `serialization_error`, `kafka_send_error`, `node_not_found`, `malformed_event`, `missing_location`, `bootstrap_error`, `debouncer_rejected`
- `deltav_node_context_record_size_bytes{location}` — protobuf size distribution (pre-compression)
- `deltav_node_context_record_size_warning_total{location}` — count of records >800 KB (still published)
- `deltav_node_context_publish_duration_seconds{location, reason}` — end-to-end publish latency
- `deltav_node_context_debounce_coalesced_total` — count of events that hit an existing pending future
- `deltav_node_context_debounce_pending_gauge` — current pending-future count
- `deltav_node_context_bootstrap_duration_seconds` — total wall-clock for the startup enumeration pass

**Topic properties:**

- `cleanup.policy=compact` (log-compacted — latest-per-key forever)
- `min.compaction.lag.ms=60000` (1 min; gives bootstrapping consumers a chance to see recent updates)
- `delete.retention.ms=86400000` (24 h tombstone retention)
- `retention.ms=-1` (compaction, not time-based)

**Known limitations:**

- No horizon `metadataChanged` UEI. Metadata writes that don't also fire `nodeUpdated`/`nodeInfoChanged`/`nodeLabelChanged` are invisible until the next UEI or restart-bootstrap. `IMPORT_SUCCESSFUL_UEI` catch-all + restart-bootstrap cover most windows.
- Schema is **not frozen** during Phase 1. Breaking changes to `NodeContext` are permitted until the first consumer ships.
- Every provisiond restart republishes all nodes; log compaction absorbs the duplicates within 60 s. At 10k nodes this is ~20 MB of transient Kafka writes per restart.

### Related — `deltav-timeseries` retention default changed

Default changed from **7 days → 1 day** in Phase 1. Override via `DELTAV_TIMESERIES_RETENTION_DAYS`. Rationale: at production scale, 7-day retention for high-volume metric records consumes significantly more disk than necessary (≈40 GB steady state at 10k nodes vs ≈5.76 GB with 1-day).
```

- [ ] **Step 2: Commit**

```bash
git add opennms-container/delta-v/README.md
git commit -m "docs(delta-v): Phase 1 node-context + timeseries retention change

Document the new DELTAV_NODE_CONTEXT_* env vars, metric catalog, topic
properties, and known limitations (metadata-drift gap, breakable Phase 1
schema, restart republish). Also note the deltav-timeseries retention
default flip from 7 to 1 day."
```

---

## Task 16: E2E script

**Files:**
- Create: `opennms-container/delta-v/test-node-context-e2e.sh`

Golden-path smoke test following the `test-timeseries-e2e.sh` pattern. Not run in unit-test CI; exercised locally or in the smoke-test runner.

- [ ] **Step 1: Check the existing pattern**

```bash
ls opennms-container/delta-v/test-*.sh
wc -l opennms-container/delta-v/test-timeseries-e2e.sh
```

Open `opennms-container/delta-v/test-timeseries-e2e.sh` to mirror its docker-compose lifecycle + readiness waiting + assertion style.

- [ ] **Step 2: Write the E2E script**

Create `opennms-container/delta-v/test-node-context-e2e.sh` with content:

```bash
#!/usr/bin/env bash
#
# Phase 1 E2E smoke test for the provisiond → deltav-node-context change feed.
#
# 1. Bring up delta-v with DELTAV_NODE_CONTEXT_ENABLED=true (default).
# 2. Wait for provisiond /actuator/health to report UP.
# 3. Consume deltav-node-context from offset 0; expect bootstrap records.
# 4. Provision a new node via provisiond REST; expect a change record.
# 5. Delete the node; expect a deleted=true tombstone.
# 6. Assert Prometheus counters are healthy.
#
# Exit non-zero on any failure.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

FOREIGN_SOURCE="phase1-e2e"
FOREIGN_ID="node-e2e-1"
NODE_LABEL="phase1-e2e-node"

echo "[1/6] Starting Docker Compose"
docker compose up -d

echo "[2/6] Waiting for provisiond readiness"
for i in $(seq 1 60); do
  if curl -sf http://localhost:8080/actuator/health | grep -q '"status":"UP"'; then
    echo "  provisiond UP after ${i}s"
    break
  fi
  sleep 1
  if [[ $i -eq 60 ]]; then
    echo "  FAIL: provisiond not ready after 60s"
    docker compose logs provisiond | tail -50
    exit 1
  fi
done

echo "[3/6] Verifying bootstrap records on deltav-node-context"
# Use kcat if available, else fall back to docker exec into the kafka container
KCAT=$(command -v kcat || true)
if [[ -z "$KCAT" ]]; then
  KCAT="docker compose exec -T kafka kafka-console-consumer.sh --bootstrap-server kafka:9092"
fi

echo "[4/6] Provisioning test node"
cat > /tmp/$$-requisition.xml <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<model-import xmlns="http://xmlns.opennms.org/xsd/config/model-import"
              date-stamp="2026-04-16T00:00:00.000-05:00"
              foreign-source="${FOREIGN_SOURCE}">
  <node foreign-id="${FOREIGN_ID}" node-label="${NODE_LABEL}" location="Default">
    <interface ip-addr="127.0.0.1" status="1" snmp-primary="N"/>
    <meta-data context="requisition" key="env" value="e2e"/>
  </node>
</model-import>
EOF

curl -sf -X POST -H "Content-Type: application/xml" \
  --data-binary "@/tmp/$$-requisition.xml" \
  "http://localhost:8080/rest/requisitions"

curl -sf -X PUT "http://localhost:8080/rest/requisitions/${FOREIGN_SOURCE}/import?rescanExisting=false"

echo "[5/6] Waiting for change record on deltav-node-context (max 30s)"
for i in $(seq 1 30); do
  if curl -sf "http://localhost:8080/actuator/prometheus" \
     | grep -E "deltav_node_context_records_published_total\{.*reason=\"change\".*\} [1-9]" \
     > /dev/null; then
    echo "  change record observed after ${i}s"
    break
  fi
  sleep 1
  if [[ $i -eq 30 ]]; then
    echo "  FAIL: no change record within 30s"
    docker compose logs provisiond | tail -100
    exit 1
  fi
done

echo "[6/6] Deleting node, verifying tombstone counter increments"
NODE_ID=$(curl -sf "http://localhost:8080/rest/nodes?foreignSource=${FOREIGN_SOURCE}&foreignId=${FOREIGN_ID}" \
  | grep -oE 'id="[0-9]+"' | head -1 | grep -oE '[0-9]+')
echo "  node id: ${NODE_ID}"
curl -sf -X DELETE "http://localhost:8080/rest/nodes/${NODE_ID}"

for i in $(seq 1 30); do
  if curl -sf "http://localhost:8080/actuator/prometheus" \
     | grep -E "deltav_node_context_records_published_total\{.*reason=\"tombstone\".*\} [1-9]" \
     > /dev/null; then
    echo "  tombstone record observed after ${i}s"
    break
  fi
  sleep 1
  if [[ $i -eq 30 ]]; then
    echo "  FAIL: no tombstone record within 30s"
    exit 1
  fi
done

# Failure counter should be zero
FAILED=$(curl -sf "http://localhost:8080/actuator/prometheus" \
  | grep -E "^deltav_node_context_records_failed_total\{" \
  | awk '{sum+=$2} END {print sum+0}')
if [[ "$FAILED" != "0" ]]; then
  echo "  FAIL: deltav_node_context_records_failed_total = ${FAILED}"
  curl -sf "http://localhost:8080/actuator/prometheus" \
    | grep "deltav_node_context_records_failed_total"
  exit 1
fi

echo "PASS — node-context E2E"
rm -f /tmp/$$-requisition.xml
```

- [ ] **Step 3: Make executable**

```bash
chmod +x opennms-container/delta-v/test-node-context-e2e.sh
```

- [ ] **Step 4: Commit**

```bash
git add opennms-container/delta-v/test-node-context-e2e.sh
git commit -m "test(delta-v/e2e): test-node-context-e2e.sh — provisiond change feed smoke test

Docker Compose end-to-end validation:
1. Start delta-v stack
2. Wait for provisiond /actuator/health UP
3. Provision a test node via REST
4. Assert deltav_node_context_records_published_total{reason=\"change\"} > 0
5. Delete the node
6. Assert {reason=\"tombstone\"} > 0
7. Assert records_failed_total sums to 0

Golden-path smoke test, exercised locally or by the smoke-test runner.
Not part of unit-test CI."
```

---

## Task 17: Full-reactor verification + PR

**Files:** none (verification + commit-push-PR)

Per `feedback_delta_v_full_reactor_verify`: after cross-module changes (new contracts module + two daemon-boot deps), a full-reactor verify is mandatory before PR.

- [ ] **Step 1: Full reactor clean build**

```bash
./mvnw clean install -DskipTests
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Full reactor test**

```bash
./mvnw test
```

Expected: every module's tests pass. Testcontainers tests may skip in environments without Docker; that's fine.

- [ ] **Step 3: Rebuild all daemon boot jars before compose**

Per `feedback_rebuild_all_daemons`, before any `build.sh deltav` or compose-up that exercises the feature, rebuild the boot jars:

```bash
./mvnw -pl core/daemon-boot-alarmd,core/daemon-boot-bsmd,core/daemon-boot-collectd,core/daemon-boot-discovery,core/daemon-boot-enlinkd,core/daemon-boot-event-translator,core/daemon-boot-pollerd,core/daemon-boot-provisiond,core/daemon-boot-reportd,core/daemon-boot-scriptd,core/daemon-boot-syslogd,core/daemon-boot-trapd -am package -DskipTests
```

Expected: 12 boot jars built successfully.

- [ ] **Step 4: Run the E2E script**

```bash
./opennms-container/delta-v/test-node-context-e2e.sh
```

Expected: `PASS — node-context E2E`.

- [ ] **Step 5: Push + open PR against `pbrane/delta-v`**

Per `feedback_never_pr_opennms`:

```bash
git push -u origin feature/kafka-ts-phase-1-node-context
gh pr create --repo pbrane/delta-v --base develop \
  --title "Phase 1: provisiond node-context change feed + shared kafka contracts" \
  --body "$(cat <<'EOF'
## Summary

- New `core/deltav-kafka-contracts` shared Maven module owning `deltav-timeseries.proto` and `deltav-node-context.proto` — both daemon-boot producers depend on it (single source of truth, no schema drift).
- New `org.deltav.netmgt.provision.nodecontext` package in `daemon-boot-provisiond` with six classes (translator, publisher, debouncer, listener, bootstrap runner, config) implementing the Phase 1 producer per `docs/superpowers/specs/2026-04-16-kafka-time-series-phase-1-node-context-design.md`.
- `deltavNodeContextTopic` `NewTopic` bean moves from Collectd to Provisiond (handoff; idempotent).
- Collectd `deltav-timeseries` retention default drops from 7 → 1 day.
- `NodeDao.findByForeignSource` implemented (was `UnsupportedOperationException`) for `IMPORT_SUCCESSFUL_UEI` fan-out.
- Real-main-class Spring context IT catches the `scanBasePackages` trap at build time.
- Testcontainers Kafka IT validates topic config + compaction round-trip at the broker.
- E2E script `test-node-context-e2e.sh` for golden-path smoke testing.

## Test plan

- [ ] `./mvnw clean install` green across the full reactor
- [ ] `./mvnw test -pl core/daemon-boot-provisiond` green (~60 unit + IT tests)
- [ ] `NodeContextKafkaIT` passes against Testcontainers
- [ ] `NodeContextProducerSpringContextIT` passes
- [ ] `test-node-context-e2e.sh` PASS on local Docker Compose
- [ ] `deltav_node_context_records_published_total{reason="bootstrap"}` > 0 on first restart post-merge
- [ ] `deltav_node_context_records_failed_total` sums to 0 after smoke test
- [ ] Manual provision/update/delete cycle shows correct records on `deltav-node-context`
EOF
)"
```

Expected: PR URL printed. Copy it into the chat.

---

## Self-review

Spec coverage — each requirement from `docs/superpowers/specs/2026-04-16-kafka-time-series-phase-1-node-context-design.md` maps to a task:

| Spec requirement | Task |
|---|---|
| `core/deltav-kafka-contracts` shared module | 1, 2 |
| Provisiond pom deps (SCS, protobuf, testcontainers, etc.) | 3 |
| `NodeDao.findByForeignSource` implementation | 4 |
| `NodeToProtobufTranslator` | 5 |
| `NodeContextPublisher` with reason-tagged publishes + tombstone + relocation | 6 |
| `NodeContextDebouncer` with 250 ms default + evict + flush | 7 |
| `NodeContextBootstrapRunner` blocking enumeration on `SmartLifecycle.start` | 8 |
| `NodeContextChangeFeedListener` with 13 UEI handlers | 9 |
| `NodeContextProducerConfiguration` + Collectd NewTopic handoff | 10 |
| `scanBasePackages` update + `application.yml` | 11 |
| Mandatory real-main-class IT (scan-package trap) | 12 |
| Testcontainers Kafka IT (topic config + compaction) | 13 |
| Collectd retention default 7 → 1 day | 14 |
| Operator README updates | 15 |
| E2E script `test-node-context-e2e.sh` | 16 |
| Full-reactor verify + PR | 17 |

Placeholder scan: grep the plan file for red flags:

```bash
grep -nE "TBD|TODO|FIXME|\?\?\?" docs/superpowers/plans/2026-04-16-kafka-time-series-phase-1-node-context.md
```

Should return nothing.

Type consistency spot-check: the publisher's `publishNode` is called with `(int nodeId, String reason)` in Task 6, `(int nodeId, "bootstrap")` in Task 8, `(int nodeId)` in Task 7, and `(int nodeId, String reason)` again in Task 10's wiring. Both overloads exist in the Task 6 implementation. Method names `enqueueUpdate`, `evict`, `flushAndClose` are consistent across Tasks 7-10. `publishTombstone(int, String)` and `publishRelocation(int, String, String)` consistent across Task 6 definition and Task 9 invocations.

---

Plan complete and saved to `docs/superpowers/plans/2026-04-16-kafka-time-series-phase-1-node-context.md`. Two execution options:

**1. Subagent-Driven (recommended)** — dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
