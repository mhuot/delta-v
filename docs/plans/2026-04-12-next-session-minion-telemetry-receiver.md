# Next Session: Minion Boot4 Telemetry Receiver + Flow E2E Validation

> Copy everything below the line into the next Claude Code conversation.

---

## Context

As of 2026-04-12 end-of-day, the flow processing pipeline has two major
milestones shipped and a critical gap identified:

- **PR #144 merged (2026-04-12):** flow-enricher classpath cleanup —
  eliminated EclipseLink, Atomikos, JAXB-XJC, and opennms-config from
  all horizon dependency blocks.
- **PR #145 merged (2026-04-12):** Phase 2 ClickHouse persistence —
  Kafka engine + 4 dimension SummingMergeTree MVs + init sidecar + E2E
  test script. Live-validated with 250 synthetic protobuf flows: all 4
  dimension MVs populated, top-talker queries working.

**The gap:** During live validation, we discovered that the **Boot4
Minion has no telemetry UDP receivers**. The `features.boot` entry
`minion-telemetryd-receivers` is legacy Karaf config that is inert in
the Spring Boot runtime. Neither Minion nor Telemetryd can open UDP
listeners for Netflow/sFlow/IPFIX. This blocks the full end-to-end
flow pipeline test:

```
softflowd (UDP) → [BLOCKED HERE] → Kafka Sink topic → flow-enricher → deltav-flows → ClickHouse
```

The ClickHouse side (Kafka→ClickHouse) is fully validated via direct
protobuf production to the `deltav-flows` topic. The missing piece is
UDP packet ingestion on the Minion.

## Infrastructure Already In Place

The following test infrastructure was committed in PR #145 and is ready
to use once the Minion can receive flow packets:

- **`flow-exporter` service** in `docker-compose.yml`: Alpine container
  with `softflowd` (Netflow-9) + `net-snmp` (snmpd), generating traffic
  via ping/curl loops. Currently sends to `telemetryd:4729` (should be
  updated to target the Minion once it has a listener).
- **`telemetryd-configuration.xml`** has a Netflow-9 UDP listener on
  port 4729 (added but currently inert since neither daemon processes it).
- **`flow-test.xml`** provisioning requisition for node
  `flow-default-testnode-1` (the softflowd exporter).
- **`test-flows-e2e.sh`** with ClickHouse assertions for flows_raw and
  all 4 dimension MVs.

## Work Plan for This Session

**Goal:** Implement a Netflow-9 UDP receiver in the Boot4 Minion so the
softflowd test container can send flows through the full pipeline, then
run `test-flows-e2e.sh` for complete E2E validation.

### Part 1: Boot4 Minion Telemetry UDP Receiver

The Boot4 Minion needs a Spring Boot-native component that:

1. Opens a UDP socket on a configurable port (default 4729 for Netflow-9)
2. Receives raw Netflow-9 UDP packets
3. Wraps each packet in a `SinkMessage` protobuf (matching the format
   that Telemetryd's Kafka Sink bridge expects)
4. Publishes the SinkMessage to the
   `OpenNMS.Sink.Telemetry-Netflow-9` Kafka topic

**Architecture approach:** This should be a Spring Boot
`@Component`/`@Service` that:
- Uses Java NIO `DatagramChannel` or Netty for non-blocking UDP reception
- Configurable via `application.yml` properties (port, buffer size, etc.)
- Starts via `SmartLifecycle` alongside the existing Minion services
- Serializes using the existing SinkMessage protobuf from
  `org.opennms.core.ipc.sink.common`

**Key references:**
- The old Karaf `UdpListener` class:
  `org.opennms.netmgt.telemetry.listeners.UdpListener` in horizon's
  `features/telemetry/listeners/` module
- The SinkMessage protobuf: `core/ipc/sink/common/` (already a
  dependency of the Minion)
- The Minion Boot4 application: `core/minion-boot/` (the main module)
  or a new `core/minion-telemetry-receiver/` module

**Scope decision:** Start with Netflow-9 only. The same UDP receiver
can handle Netflow-5, IPFIX (also UDP), and sFlow in future PRs by
making the queue name / Sink topic configurable per listener. For this
session, hardcode or configure a single listener for Netflow-9.

### Part 2: Wire the Flow Exporter to the Minion

Once the Minion has a UDP listener:

1. Update `docker-compose.yml`: expose port 4729/udp on the Minion
   service (not Telemetryd)
2. Update `flow-default-testnode-1` service to send to
   `minion-default-01:4729` instead of `telemetryd:4729`
3. Remove the (now-unnecessary) port 4729 from Telemetryd

### Part 3: Full E2E Flow Pipeline Validation

1. Rebuild the Minion image with the new telemetry receiver
2. Restart the Minion and flow-exporter containers
3. Wait for flows to appear in ClickHouse:
   ```bash
   docker compose exec clickhouse clickhouse-client --user deltav --password deltav \
       --format TabSeparated \
       -q "SELECT count() FROM deltav.flows_raw WHERE timestamp > now() - INTERVAL 5 MINUTE"
   ```
4. Run the full E2E test:
   ```bash
   ./test-flows-e2e.sh --verbose
   ```
5. Verify all phases pass — prerequisites, flows_raw data, 4 dimension
   MVs, data integrity checks

### Part 4: Provision the Flow Exporter Node

Verify that Provisiond picks up the `flow-test.xml` requisition and
provisions `flow-default-testnode-1` with SNMP data. Check that:
- The node appears in the `node` table
- SNMP interfaces are discovered
- The flow-enricher's node lookup matches the exporter IP to this node
  (exporter_node_id should be non-zero in flows_raw)

## ClickHouse Runtime Patterns (Learned 2026-04-12)

These were discovered during live validation and must be followed in
any future ClickHouse DDL work:

1. **`Tuple(value T)` for protobuf wrappers** — ClickHouse cannot
   auto-unwrap `google.protobuf.*Value` messages. Use `Tuple(value T)`
   in Kafka engine tables, extract `.value` in the ingest MV.
2. **`ProtobufSingle` format** — Kafka records are one-message-per-
   record. Use `kafka_format = 'ProtobufSingle'` (not `'Protobuf'`).
3. **DDL ordering** — The bridge MV (`20-flows-ingest.sql`) must be
   created LAST because it starts Kafka consumption on creation.
   Dimension MVs must exist first.
4. **`ifNull(sumIf(...), 0)`** — `sumIf(Nullable, condition)` returns
   NULL when no rows match. Wrap in `ifNull(..., 0)` for
   SummingMergeTree non-Nullable columns.
5. **Proto mount path** — Mount outside `/var/lib/clickhouse/` to avoid
   the entrypoint's `chown -R`. Use `config.d/format-schema-path.xml`
   to set the format_schema_path.

## Known Followups (not blocking this session)

- **CLAUDE.md update** — still references `./compile.pl` and
  `maven/bin/mvn`; actual build wrapper is `./mvnw`.
- **Phase 1.5 minor code polish** — stale Javadoc, premature
  `@Deprecated`, Locality round-trip, BSON fixture duplication, int/long
  nodeId inconsistency, InterfaceMarkingCacheCleaner test gap.
- **Phase 1.6** — horizon's `DefaultClassificationEngine`, clock-skew
  correction, `nodeDeleted` Kafka event listener for cache eviction.
- **Spec update** — update the Phase 2 spec to reflect the 5 runtime
  fixes discovered during live validation (Tuple types, ProtobufSingle,
  DDL ordering, ifNull wrapping, proto mount path).

## Important Reminders

- **Never create PRs against `OpenNMS/opennms`** — always use
  `--repo pbrane/delta-v --base develop`.
- **Always `git pull origin develop`** before creating each feature
  branch. Develop now includes PRs #141-#145.
- **Use `./mvnw` not `./compile.pl`** — the latter does not exist in
  delta-v.
- **Feature branches only** — never commit directly to develop.
- **User's uncommitted XML imports** under
  `opennms-container/delta-v/etc/imports/*.xml` and
  `opennms-container/delta-v/provisiond-overlay/etc/imports/*.xml`
  are in-progress work and must not be staged or touched.
- **Package policy:** new delta-v code uses `org.deltav.*` packages
  with the BeaconStrategists 2026 AGPL v3 copyright header.
- **Worktree cleanup:** The Part 2 worktree at
  `/Users/david/development/src/opennms/delta-v-part2` could not be
  auto-removed (Docker bind mount ownership). Run:
  ```bash
  sudo rm -rf /Users/david/development/src/opennms/delta-v-part2
  git worktree prune
  ```
