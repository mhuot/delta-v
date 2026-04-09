# RPC Timeout Audit + Alarm E2E Verification

**Date:** 2026-04-08
**Branch:** `fix/rpc-timeout-audit-alarm-e2e`
**Scope:** Horizon 1.0.4 bump, RPC timeout audit report, E2E alarm lifecycle checks, RPC timeout resilience E2E test

## Problem

Four gaps exist in Delta-V's RPC timeout handling and E2E test coverage:

1. **Horizon 1.0.3 doesn't include the PerspectivePollJob.onTimedOut() fix** (`d5a7e81e`). The fix was committed after the 1.0.3 tag, so running Docker images may still create false perspective outages on RPC timeout.

2. **No documented audit** of RPC timeout handling across all daemons. The code is compliant, but there's no artifact proving it or catching future regressions.

3. **E2E tests that verify outages don't verify corresponding alarms.** `test-perspective-e2e.sh` checks outage creation/clearing but skips alarm lifecycle (create, clear, Drools delete). Also, Phase 3 doesn't verify poll completion via `lastgood`/`lastfail` timestamps.

4. **No E2E test for RPC timeout resilience.** No test verifies that pausing a Minion (simulating complete RPC timeout) produces zero false outages or alarms across Pollerd, Collectd, and Provisiond.

## Architectural Constraint: No Events Table

Delta-V has no events table and no Eventd. Events flow exclusively through Kafka (`opennms-fault-events` topic). This means:

- **Positive event assertions** (event occurred) must grep the Kafka consumer log file, not query SQL.
- **Negative event assertions** (event did NOT occur) must verify absence from the Kafka topic during a time window.
- **Alarm assertions** query PostgreSQL `alarms` table (Alarmd writes alarms from Kafka events).
- **Outage assertions** query PostgreSQL `outages` table.

All E2E tests already run background `kafka-console-consumer.sh` processes that tee `opennms-fault-events` into a log file. The `wait_for_kafka_event()` helper greps that log.

## Solution

Four deliverables, one feature branch, one PR.

### 1. Horizon 1.0.4 Bump

**In delta-v-horizon:**
- Bump POM version from 1.0.3 to 1.0.4
- Tag `1.0.4` and push to trigger GitHub Actions publish to GitHub Packages

**In delta-v:**
- Update `deltav.horizon.version` property in root POM from 1.0.3 to 1.0.4
- Purge stale 1.0.3 artifacts from `~/.m2/repository/org/opennms/` to ensure clean resolution
- Rebuild all daemon boot JARs and Docker images

### 2. RPC Timeout Audit Report

Commit `docs/audits/2026-04-08-rpc-timeout-handling-audit.md` documenting:

**Policy:** `onTimedOut()` must never create outages or fault events. RPC timeout = infrastructure problem, not service problem.

**Handler matrix (all compliant):**

| Daemon | Class | onTimedOut() behavior | Status |
|--------|-------|----------------------|--------|
| Pollerd | PollableServiceConfig | Returns `PollStatus.unknown()` | Compliant |
| Collectd (SNMP) | SnmpCollectionSet | Returns `CollectionUnknown` (logged only) | Compliant |
| Collectd (core) | CollectionSpecification | Returns `CollectionUnknown` (logged only) | Compliant |
| PerspectivePollerd | PerspectivePollJob | Log-only, explicit policy comment | Exemplary |
| Collectd (TCA) | TcaCollectionHandler | Manual `CollectionUnknown` (no RpcExceptionHandler) | Compliant |
| Pollerd (path) | DefaultPollContext | Marks path AVAILABLE on timeout | Compliant |
| Trapd | InterfaceToNodeCache RPC | Lookup failure logged, trap processing continues | Compliant |

**Not applicable:** Provisiond, Discovery, Enlinkd have no `onTimedOut()` overrides (use framework defaults which are safe).

### 3. Alarm Lifecycle Checks in test-perspective-e2e.sh

**Perspective alarm UEIs:**
- Lost: `uei.opennms.org/perspective/nodes/nodeLostService`
- Regained: `uei.opennms.org/perspective/nodes/nodeRegainedService`

**Reduction key format:** `{uei}:{location}:{nodeId}:{ipAddr}:{serviceName}`

**Phase 3 enhancement — poll completion verification:**
- Query `ifservices` for services in the perspective-test foreign source
- Wait for `lastgood IS NOT NULL` on at least one service (proves polls completed on Minion, not just dispatched)

**Phase 4 additions — after outage creation verified:**
- Query `alarms` for `eventuei LIKE '%perspective%nodeLostService%'` matching the perspective-test node
- Verify alarm exists with `alarmtype = 1` (problem) and `severity >= 4` (WARNING or higher)

**Phase 5 additions — after outage cleared:**
- **Kafka verification:** grep the fault-events log for `nodeRegainedService` matching the perspective-test node (proves the resolution event flowed through Kafka)
- **Alarm state:** query `alarms` for the perspective alarm:
  - If alarm exists: severity must be CLEARED (2)
  - If alarm is missing (count=0): acceptable — Drools already deleted the cleared alarm. The Kafka `nodeRegainedService` event above proves the resolution occurred.
- This two-layer check (Kafka event + alarm state) handles the fast Drools delete race without false negatives.

### 4. RPC Timeout Resilience in test-minion-rpc-e2e.sh

New **Phase 4: RPC timeout resilience** appended after existing Phase 3.

**Timing analysis:**
- Default Kafka RPC TTL: 20,000ms (20s) — `KafkaRpcConstants.DEFAULT_TTL_CONFIGURED`
- Default poll retry: 2, timeout: 3000ms
- Worst case per poll: 3 attempts × 20s RPC TTL = 60s
- **Pause window: 120s** — covers worst-case retry chain plus 60s margin

**Steps:**

1. **Start Kafka consumer** for fault-events (if not already running) to capture events during pause window
2. **Record line count** of fault-events log (baseline for negative assertion)
3. **Snapshot** current outage and alarm counts for canary node
4. **Pause Minion:** `docker pause delta-v-minion`
5. **Wait 120 seconds** — all RPC requests will timeout
6. **Unpause Minion:** `docker unpause delta-v-minion`
7. **Negative Kafka assertion:** grep fault-events log from baseline forward for `nodeLostService` or `dataCollectionFailed` UEIs matching canary node — must find NONE
8. **Assert no new outages:** query `outages` for canary node, count must equal snapshot
9. **Assert no new problem alarms:** query `alarms` for canary node where `alarmtype = 1`, count must equal snapshot
10. **Wait for Minion health recovery** (timeout: 120s)
11. **Verify polling resumes:** wait for `lastgood` timestamp to advance beyond the pause window start time

**Expected test count changes:**
- test-perspective-e2e.sh: 18 → ~23 (5 new assertions: lastgood, alarm create, Kafka regained event, alarm cleared/deleted)
- test-minion-rpc-e2e.sh: 11 → ~18 (7 new Phase 4 assertions)

## Files Changed

| File | Change |
|------|--------|
| `pom.xml` | `deltav.horizon.version` 1.0.3 → 1.0.4 |
| `opennms-container/delta-v/test-perspective-e2e.sh` | Phase 3 lastgood check, Phase 4/5 alarm + Kafka lifecycle |
| `opennms-container/delta-v/test-minion-rpc-e2e.sh` | Phase 4 RPC timeout resilience with Kafka negative assertions |
| `docs/audits/2026-04-08-rpc-timeout-handling-audit.md` | New audit report |

## Dependencies

- delta-v-horizon 1.0.4 must be published to GitHub Packages before delta-v can consume it
- Stale 1.0.3 artifacts must be purged from local .m2 cache
- Docker images must be rebuilt after POM version update
- E2E tests require running Docker Compose stack with both Minions (Default + mhuot-labs)

## Test Plan

- Rebuild Docker images with horizon 1.0.4 JARs
- Run all 8 E2E suites (regression check)
- Verify new perspective alarm + Kafka assertions pass in test-perspective-e2e.sh
- Verify Phase 4 RPC timeout resilience passes in test-minion-rpc-e2e.sh (including negative Kafka assertions)
- Target: 115+ tests across 8 suites (up from 107)
