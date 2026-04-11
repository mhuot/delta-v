# Next Session: flows.processing Cleanup + Phase 2 ClickHouse Implementation

> Copy everything below the line into the next Claude Code conversation.

---

## Context

As of 2026-04-10 end-of-day, the delta-v flow-processor story has both its
strategic direction locked in and its first major implementation phase
shipped:

- **PR #141 merged (2026-04-10):** Phase 2 flow-processor spec pivoted from
  Kafka Streams + Elasticsearch to ClickHouse + materialized views. The
  updated spec is at `docs/superpowers/specs/2026-04-09-flow-processor-design.md`
  and is the canonical reference for Phase 2 implementation.
- **PR #142 merged (2026-04-10):** Phase 1.5 horizon adapter integration.
  The `flow-enricher` service is now fully functional — parses horizon
  `FlowMessage` protobufs via Netflow5/9/IPFIX/sFlow adapters, enriches
  each flow with node lookups + locality + interface marking +
  port-based classification, and emits one enriched `FlowDocumentProtos.FlowDocument`
  record per flow to the `deltav-flows` Kafka topic via a Spring Cloud
  Stream splitter. 96 tests passing.

**The `deltav-flows` topic is now carrying real enriched flow data.** Any
downstream consumer (a ClickHouse Kafka engine, a Prometheus writer,
community-built sinks) can consume it immediately. Phase 2 is the first
production consumer and makes the enriched flows actually visible to
operators via ClickHouse-backed dashboards.

## Two-Part Work Plan for This Session

Two related items, in sequence. Each should land as its own PR against
`pbrane/delta-v` develop.

**Part 1 (prerequisite, ~30-60 min):** Fix the `flows.processing`
classpath regression that PR #142's final code review flagged as Important.
This is a small, focused cleanup that should land before Part 2 starts.

**Part 2 (main goal, substantial):** Implement Phase 2 — ClickHouse
container + init sidecar + 8 SQL files + 4 materialized views + E2E test
assertion updates. Also explicitly REMOVE the existing Elasticsearch
container from `docker-compose.yml` (the Phase 2 spec documented the
removal but the actual Compose file change wasn't part of PR #141's
docs-only scope).

## Part 1: `flows.processing` Classpath Cleanup

### Why

PR #142 added `org.opennms.features.flows:org.opennms.features.flows.processing`
as a dependency to `core/flow-enricher/pom.xml` so the enricher could
import the `Pipeline`, `ProcessingOptions`, and `FlowSource` interfaces
for the `CapturingPipeline` integration seam. The final code review
discovered that this single dependency transitively pulls in:

- `org.opennms:opennms-config:1.0.7` (the whole config bundle PRs #91-100
  worked to remove from every other delta-v daemon)
- `com.atomikos:transactions-jta:3.9.2` and the full Atomikos JTA stack
- `org.eclipse.persistence:eclipselink:2.5.1` (ancient JPA provider via
  `opennms-util → jaxb-dependencies`)
- Full JAXB XJC code generator: `jaxb-xjc`, `xsom`, `codemodel`,
  `istack-commons-tools`, `dtd-parser`
- `xalan:serializer`, `org.dom4j:dom4j`, `org.mozilla:rhino:1.7.7.2`
- `javax.mail:mail:1.4.7`, `commons-lang:2.6`
- A large JSON schema / swagger-validator / libphonenumber stack

The path is `flows.processing → collection.thresholding.api → opennms-config
→ jaxb-dependencies / atomikos-dependencies`. The runtime paths are never
exercised (flow-enricher only uses three interfaces from `flows.processing`),
but the dead JARs inflate the Docker image and walk during Spring Boot
classpath scanning. Most importantly, this directly contradicts the
`feedback_karaf_is_dead` memory-index item and the Karaf-removal decisions
in PRs #91-100. It needs to be fixed before any other Spring Boot daemon
imports `flows.processing`.

See `project_flows_processing_classpath_regression.md` in the memory
index for the full context.

### Scope

Create local stubs for the three interfaces used by flow-enricher and
drop the `flows.processing` Maven dependency entirely. This matches the
pattern established by `project_onmscriteria_blocker.md` (PR #130) where
a similar stub was created for horizon's `OnmsCriteria`.

### Files to create

- `core/flow-enricher/src/main/java/org/deltav/flows/enricher/pipeline/Pipeline.java` — local interface mirroring horizon's `org.opennms.netmgt.flows.processing.Pipeline`
- `core/flow-enricher/src/main/java/org/deltav/flows/enricher/pipeline/ProcessingOptions.java` — local type mirroring horizon's `org.opennms.netmgt.flows.processing.ProcessingOptions`
- `core/flow-enricher/src/main/java/org/deltav/flows/enricher/pipeline/FlowSource.java` — local type mirroring horizon's `org.opennms.netmgt.flows.api.FlowSource`

Use `javap` to inspect the actual interface/class shapes in the horizon
JARs before creating the stubs. The stubs only need the methods that
`AbstractFlowAdapter` actually calls on them (at minimum `Pipeline.process(List<Flow>, FlowSource, ProcessingOptions)`),
plus whatever the `CapturingPipeline` needs to expose via getters.

### Files to modify

- `core/flow-enricher/pom.xml` — remove the `flows.processing` dependency block and its ~13-line exclusion list. Also remove the `org.opennms.features.flows:org.opennms.features.flows.processing` entry from `pom.xml`'s `<dependencyManagement>` (parent POM line ~226) if nothing else on the reactor uses it.
- `core/flow-enricher/src/main/java/org/deltav/flows/enricher/pipeline/CapturingPipeline.java` — change `implements org.opennms.netmgt.flows.processing.Pipeline` to `implements org.deltav.flows.enricher.pipeline.Pipeline`. The method signature of `process()` will likely need to match exactly — `javap` the horizon interface to confirm the throws clause (horizon's throws `FlowException` from the integration API; the stub should throw a delta-v-local exception or drop the throws if nothing threw it in practice).
- `core/flow-enricher/src/main/java/org/deltav/flows/enricher/protocol/AbstractProtocolMessageProcessor.java` — update imports if the Pipeline/ProcessingOptions/FlowSource types are now in the new delta-v package.
- Any test file that imports the horizon types — switch to the new local types.

### Catch: `AbstractFlowAdapter` is in horizon and calls `pipeline.process()` on horizon's interface

**This is the subtle part.** `AbstractFlowAdapter<?>` is in horizon's
`netflow.adapter` JAR and holds a reference to horizon's
`org.opennms.netmgt.flows.processing.Pipeline` type as a constructor field.
Our local stub interface `org.deltav.flows.enricher.pipeline.Pipeline` is
structurally identical but a DIFFERENT type — a `CapturingPipeline`
implementing our local stub CANNOT be passed to an `AbstractFlowAdapter`
constructor that expects horizon's type.

Two possible approaches:

**A. Keep using horizon's interface for the adapter integration seam, but
create local stubs for everything else.** This means the `CapturingPipeline`
still has to implement horizon's `Pipeline` interface (dragging
`flows.processing` onto the classpath), but it's a narrower surface. The
cleanup would then be about trimming the exclusion block on `flows.processing`
to specifically exclude `opennms-config`, `collection.thresholding.api`,
`atomikos-dependencies`, `jaxb-dependencies`, `jasypt-dependencies`,
`features.config.*`. This is Recommendation C from the final code review.
**This is the simpler, less-risky path** — it keeps the horizon integration
unchanged and just adds ~6 more exclusion entries.

**B. Create a local `Pipeline` interface AND a bridge adapter that wraps
our local `CapturingPipeline` in a thing that implements horizon's interface.**
The bridge is a ~20-line adapter pattern class. More complex but fully
decouples delta-v from `flows.processing`.

**Recommended approach: start with (A).** It's lower risk, gets the
classpath clean in one focused PR, and option (B) can be a future
improvement if we want to fully sever the dependency. Specifically, add
these exclusions to the existing `flows.processing` dependency block in
`core/flow-enricher/pom.xml`:

```xml
<exclusion><groupId>org.opennms</groupId><artifactId>opennms-config</artifactId></exclusion>
<exclusion><groupId>org.opennms.features.collection.thresholding</groupId><artifactId>org.opennms.features.collection.thresholding.api</artifactId></exclusion>
<exclusion><groupId>org.opennms.dependencies</groupId><artifactId>atomikos-dependencies</artifactId></exclusion>
<exclusion><groupId>org.opennms.dependencies</groupId><artifactId>jaxb-dependencies</artifactId></exclusion>
<exclusion><groupId>org.opennms.dependencies</groupId><artifactId>jasypt-dependencies</artifactId></exclusion>
<exclusion><groupId>org.opennms.features.config</groupId><artifactId>*</artifactId></exclusion>
<exclusion><groupId>org.opennms</groupId><artifactId>opennms-util</artifactId></exclusion>
```

### Verification

After the exclusion changes:

```bash
./mvnw -pl :org.deltav.flows.flow-enricher -DskipTests compile
./mvnw -pl :org.deltav.flows.flow-enricher -o dependency:tree | grep -c servicemix.bundles     # expect 0
./mvnw -pl :org.deltav.flows.flow-enricher -o dependency:tree | grep -c opennms-config         # expect 0
./mvnw -pl :org.deltav.flows.flow-enricher -o dependency:tree | grep -c atomikos              # expect 0
./mvnw -pl :org.deltav.flows.flow-enricher -o dependency:tree | grep -c eclipselink           # expect 0
./mvnw -pl :org.deltav.flows.flow-enricher -o dependency:tree | grep -c jaxb-xjc              # expect 0
./mvnw -pl :org.deltav.flows.flow-enricher test                                                # expect 96 tests passing
```

If the full test suite still passes after the exclusions, the cleanup is
complete. If it fails with `ClassNotFoundException` for something under
`org.opennms.netmgt.flows.processing` or `org.opennms.netmgt.flows.api`,
adjust the exclusion block to be slightly narrower and re-run.

### Commit shape

Single commit:

```
fix(flow-enricher): exclude opennms-config from flows.processing to prevent Karaf-era classpath regression
```

The commit body should cite PR #142's final code review and the
`project_flows_processing_classpath_regression.md` memory entry. Open
as a small focused PR against `pbrane/delta-v` develop.

## Part 2: Phase 2 ClickHouse Implementation

### Spec reference

The canonical spec for this phase is
`docs/superpowers/specs/2026-04-09-flow-processor-design.md` on develop
(merged via PR #141 on 2026-04-10). Read the entire "ClickHouse Persistence"
section top-to-bottom before starting implementation. The spec is concrete
enough to implement directly from — every DDL statement is written out,
every environment variable is named, every file location is specified.

### Scope

Six logical tasks, each landing as a separate commit on a single feature
branch (matching the commit-structure pattern from PR #142):

1. **Add ClickHouse container + init sidecar to `docker-compose.yml`**
   Add the two new services exactly as shown in the spec's "Docker Compose
   Integration" section. Volume-mount
   `../../core/flow-enricher/src/main/proto` into the ClickHouse container
   at `/var/lib/clickhouse/format_schemas/` so the database reads the
   `.proto` contract directly from the source tree (zero-drift policy).
   Also declare the `clickhouse-data` named volume.

2. **🔴 REMOVE the Elasticsearch container from `docker-compose.yml`.**
   The `elasticsearch` service is still in the Compose file at lines
   71-80 (as of 2026-04-10). PR #141 updated the spec to document the
   removal but never touched the actual Compose file — that's a docs-only
   PR. **Phase 2 implementation must delete the `elasticsearch` service
   block, delete the `esdata` volume declaration, and confirm no other
   service depends on it.** Verify with `grep -c elasticsearch docker-compose.yml`
   before and after — expect non-zero before, zero after.

3. **Create the `clickhouse/init-runner.sh` bash bootstrap script.**
   Per the spec: verifies the `.proto` file is mounted (fail-fast with
   a clear error if not), uses `sed` (NOT `envsubst` — not in the
   clickhouse-server image) for TTL placeholder substitution, applies
   `init/*.sql` then `user-init/*.sql` in lexical order via
   `clickhouse-client --multiquery`. Make it executable (`chmod +x`).

4. **Create the 8 `clickhouse/init/*.sql` files** per the spec:
   - `01-database.sql` — CREATE DATABASE deltav
   - `02-flows-raw.sql` — wide MergeTree table with all 45 non-reserved
     proto fields, IPv6 address columns, Nullable wrapper types for
     `google.protobuf.*Value` fields, Array(LowCardinality(String)) for
     node categories, ORDER BY `(exporter_node_id, timestamp, input_snmp_ifindex)`,
     PARTITION BY `toDate(timestamp)`, TTL
     `timestamp + INTERVAL ${DELTAV_CLICKHOUSE_FLOWS_RAW_TTL_DAYS} DAY`
   - `03-flows-kafka.sql` — Kafka engine table with Tuple columns for the
     three NodeInfo messages, Format=Protobuf, kafka_schema pointing at
     `deltav-flows.proto:FlowDocument`, kafka_num_consumers=2
   - `04-flows-ingest.sql` — Materialized view bridging Kafka engine
     table → flows_raw with `CAST(x AS Nullable(T))` type narrowing and
     `toIPv6OrNull(next_hop_address)` safe conversion
   - `10-mv-application.sql` — SummingMergeTree + MV on application
     dimension, 1-minute buckets, sumIf for ingress/egress split
   - `11-mv-source-ip.sql` — source-IP dimension (NOTE: renamed from
     "host" in the spec to accurately reflect "Top Sources" semantics)
   - `12-mv-conversation.sql` — src/dst/application conversation dimension
   - `13-mv-dscp.sql` — DSCP dimension with `255` sentinel for unknown

   Create a `clickhouse/user-init/.gitkeep` file so the override directory
   exists at runtime.

5. **Wire the init sidecar into `build.sh`** if needed — the init runner
   script needs to be reachable from the Compose file's volume mounts.
   Per the spec, the sidecar reuses the `clickhouse/clickhouse-server:25.8`
   image (no custom image build), so no new entries in `build.sh`'s
   `do_flow_enricher_image()` style functions. The init sidecar just
   mounts the bash script + SQL files from the working directory.

6. **Update `test-flows-e2e.sh`** to use `clickhouse-client --format TabSeparated`
   queries against `flows_raw` and the four dimension MVs instead of the
   prior `curl`-based ES assertions. Spec has the exact `clickhouse-client`
   invocations for each assertion.

### Important architectural reminders (carried forward from Phase 1.5)

These are validated facts from the Phase 1.5 implementation that apply
directly to Phase 2:

1. **Horizon's flow adapters consume pre-decoded `FlowMessage` protobufs,
   not raw wire bytes.** The `deltav-flows` topic carries our delta-v
   `FlowDocumentProtos.FlowDocument` wire format, which is tag-aligned
   with horizon's older `FlowDocument` but is a distinct class
   (`org.deltav.flows.proto.FlowDocumentProtos.FlowDocument`). ClickHouse's
   Kafka engine parses this directly via the mounted `.proto` file.

2. **`Function<Message<byte[]>, List<byte[]>>` requires `use-native-encoding=true`
   to behave as a splitter.** Phase 1.5 landed this fix in `application.yml`
   for the `enrichFlows-out-0` binding. Do NOT remove it under any
   circumstances — without it, every `deltav-flows` record is a JSON array
   of the full batch instead of one record per flow. See
   `feedback_spring_cloud_stream_splitter.md` in the memory index.

3. **The `deltav-flows` Kafka topic partition key is the exporter node ID**
   (derived inside Spring Cloud Stream from the Message headers during
   publication). ClickHouse's Kafka engine doesn't care about partition
   keys — it consumes from all partitions via its internal consumer group.
   `kafka_num_consumers=2` means ClickHouse creates two consumer threads;
   scaling beyond that is handled by running multiple ClickHouse replicas
   in a cluster configuration, which Phase 2 does not ship (single-node
   default per spec decision).

4. **The `.proto` file at `core/flow-enricher/src/main/proto/deltav-flows.proto`
   is the public contract.** Phase 2's ClickHouse container mounts it
   read-only directly from the source tree. Do not copy it or generate it
   anywhere else — single source of truth.

### Verification

After all 6 tasks:

```bash
# Build check
./mvnw -pl :org.deltav.flows.flow-enricher test   # expect 96 tests still passing

# Compose check
grep -c elasticsearch opennms-container/delta-v/docker-compose.yml   # expect 0
grep -c clickhouse    opennms-container/delta-v/docker-compose.yml   # expect >= 10

# Start the stack
cd opennms-container/delta-v
docker compose --profile full up -d clickhouse clickhouse-init flow-enricher kafka postgres minion

# Wait for init sidecar to finish
docker compose logs clickhouse-init | grep -i "DDL bootstrap complete"

# Verify the tables exist
docker compose exec clickhouse clickhouse-client --user deltav --password deltav \
    -q "SHOW TABLES FROM deltav"
# Expect: flows_kafka, flows_raw, flows_ingest, flows_by_application_1m,
#         flows_by_source_ip_1m, flows_by_conversation_1m, flows_by_dscp_1m
#         plus the four materialized view metadata tables

# Generate some flow traffic via softflowd, then assert records appear
docker compose exec clickhouse clickhouse-client --user deltav --password deltav \
    --format TabSeparated \
    -q "SELECT count() FROM deltav.flows_raw WHERE timestamp > now() - INTERVAL 5 MINUTE"
# Expect: non-zero

# Assert each dimension MV has rows
for dim in application source_ip conversation dscp; do
    docker compose exec clickhouse clickhouse-client --user deltav --password deltav \
        --format TabSeparated \
        -q "SELECT count() FROM deltav.flows_by_${dim}_1m WHERE t_minute > now() - INTERVAL 5 MINUTE"
done
# Expect: each non-zero
```

### Commit shape

Six commits in one PR, one per task:

```
feat(clickhouse): add container and init sidecar to Docker Compose
feat(clickhouse): remove legacy Elasticsearch container and esdata volume
feat(clickhouse): add init-runner.sh bootstrap script with sed TTL templating
feat(clickhouse): add default SQL schema (raw table, Kafka engine, ingest MV)
feat(clickhouse): add four default dimension materialized views
test(flow-enricher): update test-flows-e2e.sh assertions to query ClickHouse
```

PR title: `feat(clickhouse): Phase 2 ClickHouse persistence implementation`

## Known Followups (to keep in mind but not block on)

These were flagged during Phase 1.5 reviews and deferred. Don't bundle
them into Part 1 or Part 2 unless they become blocking:

- **Plan documentation fix** — `docs/superpowers/plans/2026-04-10-flow-enricher-phase15.md`
  references `./compile.pl` throughout; should be `./mvnw`. Separate docs-only PR.
- **Minor code polish from Phase 1.5 reviews** — stale "later commit"
  Javadoc in `DeserializedSinkMessage`, premature `@Deprecated` on single-arg
  deserializer, Locality enum/string round-trip in FlowEnrichmentFunction,
  BSON fixture loader duplication between `SFlowMessageProcessorTest` and
  `FlowEnrichmentStreamBinderIT`, `TestAdapterDefinitions` could use
  `SimpleAdapterDefinition` directly, `int`/`long` nodeId type inconsistency
  in `InterfaceMarkingCache.markIfNeeded`, `InterfaceMarkingCacheCleaner`
  scheduled bean has no direct test coverage.
- **Phase 1.6** — horizon's `DefaultClassificationEngine` to replace the
  port-based stub, clock-skew correction, `nodeDeleted` Kafka event
  listener for cache eviction.
- **CLAUDE.md update** — CLAUDE.md still references `./compile.pl` and
  `maven/bin/mvn` which don't exist in delta-v. The actual build wrapper
  is `./mvnw`. A docs PR to fix CLAUDE.md would benefit every future
  Claude Code session.

## Important Reminders

- **Never create PRs against `OpenNMS/opennms`** — always use
  `--repo pbrane/delta-v --base develop`. The `gh` CLI defaults to the
  fork parent which is wrong.
- **Always `git pull origin develop`** before creating each feature branch.
  Develop now includes PR #141 (spec) and PR #142 (Phase 1.5) so the
  starting point is fresh.
- **Use `./mvnw` not `./compile.pl`** — the latter does not exist in
  delta-v despite what CLAUDE.md says. Build commands look like:
  `./mvnw -pl :org.deltav.flows.flow-enricher -DskipTests compile` and
  `./mvnw -pl :org.deltav.flows.flow-enricher test`.
- **Feature branches only** — never commit directly to develop. Use
  distinct branches per PR (e.g., `fix/flow-enricher-classpath-cleanup`
  for Part 1 and `feature/clickhouse-phase2-impl` for Part 2).
- **User's uncommitted XML imports** under
  `opennms-container/delta-v/etc/imports/*.xml` and
  `opennms-container/delta-v/provisiond-overlay/etc/imports/*.xml`
  (plus the untracked `delta-v-hosts.xml`) are in-progress work and
  must not be staged or touched.
- **The `.proto` file at `core/flow-enricher/src/main/proto/deltav-flows.proto`
  is the public community contract.** Do NOT rename or renumber any
  existing field. New fields may be added at new tag numbers. Reserved
  tags 25 and 44 must stay reserved.
- **Package policy:** new delta-v code uses `org.deltav.*` packages with
  the BeaconStrategists 2026 AGPL v3 copyright header (not `org.opennms`).
- **ClickHouse version:** the spec pins `clickhouse/clickhouse-server:25.8`
  as the latest LTS. If 25.8 is no longer the latest LTS when you start
  implementation, check the ClickHouse release page and update both the
  spec and the Compose file consistently.
