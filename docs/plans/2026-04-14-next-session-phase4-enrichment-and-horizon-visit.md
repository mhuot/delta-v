# Next Session: Fix `test-flows-e2e.sh` Phase 4 enrichment regression + patch horizon `FlowRecord.visit()`

> Copy everything below the line into the next Claude Code conversation.

---

## Context

As of **2026-04-14**, the flow-enricher pipeline is producing both Netflow v9 and sFlow rows steadily into `deltav.flows_raw`. Today's two PRs fixed the long-standing sFlow silent drop and the Netflow9 thread-safety races:

- **PR #156** (`af138d4611f`) — sFlow silent drop. Three coupled fixes:
  1. Disable DNS lookups on the `SFlowUdpParser` bean to sidestep a latent horizon `FlowRecord.visit()` NPE.
  2. Force per-speed sampling overrides (`sampling.10G = 1` etc.) in the hsflowd test exporter so Docker veth interfaces don't fall through to the 1:10000 default.
  3. Add `-P` to the hsflowd invocation so it stays as root and `mod_pcap` can open eth0.
- **PR #157** (`47ed7a3c1e2`) — `CapturingDispatcher` thread safety. Both the Netflow9 NPE at `synthesizeParsedLog:204` and the `ArrayList.add` AIOOBE shared one root cause: a plain `ArrayList` racing under horizon's concurrent dispatch. Fixed via synchronised methods + immutable `List.copyOf` snapshots + null rejection. 16-thread × 2 000-send stress test added.

After both PRs merged, `./test-flows-e2e.sh` shows **17 of 18 assertions passing**:

| Phase | Assertion | Status |
|---|---|---|
| 1 | ClickHouse healthy + DDL | ✅ |
| 2 | `flows_raw` rows in last 10 min | ✅ ~63K |
| 2 | `flows_raw` V9 rows | ✅ ~750 |
| 2 | `flows_raw` SFLOW rows | ✅ ~63K |
| 3 | All 4 dimension MVs populated | ✅ |
| 4 | **Enriched flows (`exporter_node_id > 0`)** | ❌ **0 rows** |
| 4 | Source addresses populated | ✅ |
| 4 | Application classification active | ✅ |

**Phase 4 has been broken since 2026-04-12 17:25:34** — the most recent row in `flows_raw` with `exporter_node_id > 0` is from ~36 hours before today's session. PR #156 did not cause this regression; it just unmasked it (Phase 4 only runs after Phase 2 passes, and Phase 2 sFlow was failing for the entire 36-hour window). Tracking it as a separate primary task.

There is also a **secondary defensive cleanup** in horizon: `FlowRecord.visit()` at line 108 dereferences `this.data.value` without the null guard its sibling `writeBson()` has. Today's PR #156 sidesteps this by disabling DNS lookups on the parser, but the underlying horizon bug is still there waiting to bite any future code path that walks via `SampleDatagram.visit()`. A one-line null guard upstream removes the trap and lets us eventually delete the workaround.

## Primary Task 1: Restore `test-flows-e2e.sh` Phase 4 enrichment

**Estimated effort:** 30 – 90 minutes. Either a one-line query fix in `JdbcNodeInfoLookup`, a missing requisition entry, or a schema column rename.

### Reproducer

From a clean stack:

```bash
cd /Users/david/development/src/opennms/delta-v
git fetch origin && git checkout develop && git pull origin develop
cd opennms-container/delta-v
docker compose --profile full up -d
./test-flows-e2e.sh 2>&1 | tail -20
```

Expected today: 17 passed, 1 failed:
```
[FAIL] No enriched flows (exporter_node_id > 0) in the last 10 minutes
```

### Diagnosis order (cheap → expensive)

**Step 1: Is the test exporter even in the database?**

The two flow exporters in `docker-compose.yml` are `flow-default-testnode-1` (172.18.0.21, softflowd) and `flow-sflow-testnode-1` (172.18.0.20, hsflowd). Check whether either is in the OpenNMS `ipinterface` table:

```bash
docker compose exec -T postgres psql -U opennms -d opennms \
  -c "SELECT n.nodeid, n.nodelabel, ip.ipaddr FROM node n
      JOIN ipinterface ip ON ip.nodeid = n.nodeid
      WHERE ip.ipaddr IN ('172.18.0.20', '172.18.0.21')"
```

- **If 0 rows:** the requisition needs a node entry for the test exporters. Look at `opennms-container/delta-v/provisiond-overlay/etc/imports/delta-v.xml` (or whichever requisition is the seed for the test bed) and add the missing nodes. Then trigger an import.
- **If 1–2 rows:** the requisition is fine; the bug is in `JdbcNodeInfoLookup`. Continue to Step 2.

**Step 2: Is `JdbcNodeInfoLookup` getting called and returning the right thing?**

```bash
# Bump the relevant logger to DEBUG in flow-enricher application.yml:
#   org.deltav.flows.enricher.enrichment: DEBUG
# Rebuild & restart the enricher:
SKIP_TESTS=true ./build.sh deltav 2>&1 | tail -5
docker compose up -d --force-recreate flow-enricher
docker compose logs -f flow-enricher | grep -i "JdbcNodeInfoLookup\|node lookup\|exporter_node"
```

You should see one DEBUG line per unique exporter IP showing what query ran and what came back.

**Step 3: Hibernate 7 / jakarta.persistence column rename?**

The Hibernate 7 / jakarta migration touched many entity classes. If a column referenced by `JdbcNodeInfoLookup`'s SQL got renamed (or the table's case sensitivity changed under the new schema validator), the query may run silently against zero rows.

```bash
# What columns does flow-enricher's lookup query actually expect?
grep -rn "JdbcNodeInfoLookup\|FROM ipinterface\|FROM node " core/flow-enricher/src/main/java/

# What does the live schema look like?
docker compose exec -T postgres psql -U opennms -d opennms \
  -c "\d ipinterface" \
  -c "\d node"
```

Compare column names. Pay particular attention to `ifindex` vs `snmpifindex`, `nodeid` casing, `nodelabel` vs `node_label`.

**Step 4: Is the issue a stale `flow-enricher` cache?**

`JdbcNodeInfoLookup` has a TTL cache (default 5 minutes). If it cached a `null` lookup result for the test exporter IPs at startup before the requisition imported them, every subsequent call returns the cached `null` for 5 minutes. A flow-enricher restart should clear it; if Phase 4 starts passing for the first 5 minutes after every restart and then breaks again, this is the cause.

### Fix

Depending on root cause:

- **Missing requisition node:** add the node entry to the appropriate requisition seed file. Write the change so the seed is committed (do NOT commit the runtime `last-import` drift — see guardrails). Trigger import via the provisiond REST API or restart the provisiond container.
- **Wrong query:** patch `core/flow-enricher/src/main/java/org/deltav/flows/enricher/enrichment/JdbcNodeInfoLookup.java`. There's already a unit test (`JdbcNodeInfoLookupTest`) — extend it with whatever case the bug exposes.
- **Schema rename:** update the SQL in `JdbcNodeInfoLookup` to match the current schema. Add a regression test that fails the old query.
- **Stale-cache TOCTOU:** add a manual cache eviction on startup, or shrink the negative-result TTL.

### Prove the fix

```bash
SKIP_TESTS=true ./build.sh deltav 2>&1 | tail -5
docker compose up -d --force-recreate flow-enricher
./test-flows-e2e.sh 2>&1 | tail -20
```

Expected: **all 18 assertions pass.**

## Primary Task 2: Patch horizon `FlowRecord.visit()` null guard

**Estimated effort:** 30 minutes including horizon CI cycle.

### The bug

In the horizon worktree (`/Users/david/development/src/opennms/delta-v-horizon`), file
`features/telemetry/protocols/sflow/parser/src/main/java/org/opennms/netmgt/telemetry/protocols/sflow/parser/proto/flows/FlowRecord.java` at line 108:

```java
@Override
public void visit(SampleDatagramVisitor visitor) {
    visitor.accept(this);
    if (this.data != null) {
        this.data.value.visit(visitor);   // ← NPE when data.value is null
    }
}
```

Compare with the sibling `writeBson()` at line 96 which correctly null-guards:

```java
public void writeBson(final BsonWriter bsonWriter, final SampleDatagramEnrichment enr) {
    if (data.value != null) {
        this.data.value.writeBson(bsonWriter, enr);
    } else {
        bsonWriter.writeNull();
    }
}
```

`data.value` is null whenever the parsed `data_format` is not in horizon's `flowDataFormats` map (see `FlowRecord.java:43-85`). Real-world hsflowd 2.1.23 emits flow records with at least one such unknown format.

### Fix

```java
@Override
public void visit(SampleDatagramVisitor visitor) {
    visitor.accept(this);
    if (this.data != null && this.data.value != null) {
        this.data.value.visit(visitor);
    }
}
```

One line. Symmetric with `writeBson()`.

### Optional: also add coverage in horizon

Horizon has zero test coverage of `FlowRecord.visit()` against real-world wire bytes. `BlackboxTest` constructs `SampleDatagram` instances but never calls `visit()` on them. A `BlackboxVisitTest` that walks each fixture via the visitor and asserts no NPE would prevent regression. Optional — the null guard alone closes the bug.

### Cycle

```bash
cd /Users/david/development/src/opennms/delta-v-horizon
git fetch origin && git checkout main && git pull origin main
git checkout -b fix/sflow-flowrecord-visit-null-guard
# edit FlowRecord.java
./mvnw -pl features/telemetry/protocols/sflow/parser -am test
git commit -am "fix(sflow): null-guard FlowRecord.visit() data.value to match writeBson()"
git push -u origin fix/sflow-flowrecord-visit-null-guard
gh pr create --repo pbrane/delta-v-horizon --base main \
  --title "fix(sflow): null-guard FlowRecord.visit() data.value to match writeBson()" \
  --body "..."
```

After the horizon PR merges, bump horizon version + republish to GitHub Packages, then bump the horizon dependency version in delta-v's `pom.xml`. Once delta-v consumes the fixed horizon, you can delete the `setDnsLookupsEnabled(false)` workaround in `FlowEnricherConfiguration.sflowUdpParser()` (and the matching call in `SFlowParserBridgeIT.setUp()`) and verify the regression test still passes — that would prove the upstream fix is load-bearing.

**Defer the workaround removal to a separate small PR after horizon is bumped.** Don't bundle it with the horizon fix itself.

## Out of scope (track separately, do not bundle)

- `JdbcNodeInfoLookup` cache invalidation strategy beyond a one-line fix
- Refactor `JdbcNodeInfoLookup` away from JDBC entirely (some other session)
- Hibernate 7 column rename audit across other daemons
- The two harmless Netflow9 parser warnings (`Undeclared field type: 136`, `139`) — log noise, not a bug

## Guardrails from prior sessions

- **Never create PRs against `OpenNMS/opennms`** — always `--repo pbrane/delta-v --base develop` (or `--repo pbrane/delta-v-horizon --base main` for horizon).
- **Always `git pull origin develop`** before creating each feature branch.
- **Feature branches only** — never commit directly to develop.
- **Build tool is `./mvnw`** (delta-v) or `./mvnw` from horizon root. `compile.pl` only exists in the legacy horizon worktree.
- **Discard `provisiond-overlay/etc/imports/*.xml` `last-import` drift** with `git checkout --` — runtime state, never commit. (Unless this session is the one that adds a requisition node entry — in that case, only commit the *node entry* hunks, not the `last-import` hunks.)
- **`./build.sh deltav` is self-healing** for stale daemon-boot JARs (PR #154). For the flow-enricher specifically, `do_flow_enricher_image` runs `./mvnw ... package` inline every time, so fresh edits propagate without extra steps.
- **New delta-v code uses `org.deltav.*` packages** with the BeaconStrategists 2026 AGPL v3 header.
- **CapturingDispatcher is now thread-safe (PR #157).** Don't accidentally revert the synchronised methods or the `List.copyOf` snapshot pattern — the 16-thread stress test in `CapturingDispatcherTest.concurrentSendsAreThreadSafe` will fail loudly if you do.
- **SFlowUdpParser DNS lookups must stay disabled (PR #156).** The `parser.setDnsLookupsEnabled(false)` call in `FlowEnricherConfiguration.sflowUdpParser()` is load-bearing until the horizon `FlowRecord.visit()` null guard ships and propagates. The matching call in `SFlowParserBridgeIT.setUp()` is the regression test for that contract — `@Timeout(10s)` means a hang fails the test fast.

## Quick Start

```bash
cd /Users/david/development/src/opennms/delta-v
git fetch origin && git checkout develop && git pull origin develop
git log --oneline -5  # confirm PR #156 (af138d4611f) and PR #157 (47ed7a3c1e2) are present
git checkout -b feature/flow-enricher-phase4-enrichment-fix
# ... start with Primary Task 1, Step 1 (database query) ...
```

For the horizon work, switch worktrees:

```bash
cd /Users/david/development/src/opennms/delta-v-horizon
git fetch origin && git checkout main && git pull origin main
git checkout -b fix/sflow-flowrecord-visit-null-guard
# ... start with Primary Task 2 ...
```
