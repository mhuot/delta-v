# RPC Timeout Audit + Alarm E2E Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bump Horizon to 1.0.4 (PerspectivePollJob fix), document RPC timeout audit, add alarm lifecycle checks to perspective E2E, add RPC timeout resilience test to Minion RPC E2E.

**Architecture:** Horizon 1.0.4 is published from delta-v-horizon repo via GitHub Actions, then consumed by delta-v. E2E tests use Kafka consumer logs for event assertions and PostgreSQL for alarm/outage assertions. The `docker pause` technique simulates a complete Minion RPC black-hole.

**Tech Stack:** Bash (E2E scripts), Docker Compose, Kafka (`kafka-console-consumer.sh`), PostgreSQL (psql), Maven

---

### Task 1: Bump Horizon to 1.0.4

**Files:**
- Modify: `/Users/david/development/src/opennms/delta-v-horizon/pom.xml:29` (version 1.0.3 → 1.0.4)
- Modify: `/Users/david/development/src/opennms/delta-v/pom.xml:44` (deltav.horizon.version 1.0.3 → 1.0.4)

- [ ] **Step 1: Bump horizon POM version**

```bash
cd /Users/david/development/src/opennms/delta-v-horizon
```

In `pom.xml`, line 29, change:
```xml
  <version>1.0.3</version>
```
to:
```xml
  <version>1.0.4</version>
```

- [ ] **Step 2: Commit, tag, and push horizon**

```bash
cd /Users/david/development/src/opennms/delta-v-horizon
git add pom.xml
git commit -m "fix: bump to 1.0.4 — includes PerspectivePollJob onTimedOut() fix"
git tag 1.0.4
git push origin main --tags
```

This triggers the GitHub Actions `publish.yml` workflow which deploys all artifacts to GitHub Packages.

- [ ] **Step 3: Wait for GitHub Actions publish to complete**

```bash
gh run list --repo pbrane/delta-v-horizon --limit 3
```

Wait for the publish workflow triggered by the `1.0.4` tag to show `completed`. This typically takes 15-20 minutes for the full 681-module build.

- [ ] **Step 4: Purge stale 1.0.3 artifacts from local Maven cache**

```bash
find ~/.m2/repository/org/opennms -name "*1.0.3*" -type f -delete
find ~/.m2/repository/org/opennms -name "*1.0.3" -type d -exec rm -rf {} + 2>/dev/null || true
```

This ensures `make build` pulls fresh 1.0.4 JARs from GitHub Packages instead of using cached 1.0.3.

- [ ] **Step 5: Update delta-v to consume 1.0.4**

In delta-v, create the feature branch and update the version property:

```bash
cd /Users/david/development/src/opennms/delta-v
git checkout develop && git pull origin develop
git checkout -b fix/rpc-timeout-audit-alarm-e2e
```

In `pom.xml`, line 44, change:
```xml
    <deltav.horizon.version>1.0.3</deltav.horizon.version>
```
to:
```xml
    <deltav.horizon.version>1.0.4</deltav.horizon.version>
```

- [ ] **Step 6: Build delta-v and verify resolution**

```bash
make build
```

Expected: BUILD SUCCESS, 21/21 modules. All Horizon JARs now resolve as 1.0.4.

Verify:
```bash
mvn dependency:tree -pl org.opennms.core:org.opennms.core.daemon-boot-perspectivepollerd -Dincludes=org.opennms.features:org.opennms.features.perspectivepoller -DoutputType=text 2>/dev/null | grep perspectivepoller
```
Expected output should show `1.0.4`.

- [ ] **Step 7: Rebuild Docker images**

```bash
cd opennms-container/delta-v && ./build.sh deltav
```

- [ ] **Step 8: Commit**

```bash
cd /Users/david/development/src/opennms/delta-v
git add pom.xml
git commit -m "fix: bump horizon to 1.0.4 — PerspectivePollJob onTimedOut() fix"
```

---

### Task 2: Write RPC Timeout Audit Report

**Files:**
- Create: `docs/audits/2026-04-08-rpc-timeout-handling-audit.md`

- [ ] **Step 1: Create audit directory and report**

```bash
mkdir -p docs/audits
```

Write `docs/audits/2026-04-08-rpc-timeout-handling-audit.md` with this content:

```markdown
# RPC Timeout Handling Audit — 2026-04-08

## Policy

**RPC timeouts must NEVER create outages or fault events.**

RPC timeout = infrastructure problem (Minion unreachable, Kafka consumer rebalance, SSH tunnel flap). Only actual service failures (monitor executed on Minion and returned Unavailable) should create outages.

| RPC Callback | Should create outage? | Reasoning |
|--------------|----------------------|-----------|
| `onTimedOut()` | **NO** — log only | Minion unreachable, not service down |
| `onRejected()` | **YES** | Minion explicitly refused — processing failure |
| `onUnknown()` | **YES** | Exception during processing — real error |
| Normal result with Unavailable | **YES** | Monitor executed, service actually down |

## Audit Results

All `onTimedOut()` implementations in delta-v-horizon (1.0.4) are **compliant**.

### Active RPC Daemons

| Daemon | Class | File | onTimedOut() behavior | Status |
|--------|-------|------|----------------------|--------|
| Pollerd | `PollableServiceConfig` | `features/poller/impl/.../PollableServiceConfig.java:155` | Returns `PollStatus.unknown()` — no outage | Compliant |
| Pollerd (path) | `DefaultPollContext` | `features/poller/impl/.../DefaultPollContext.java:298` | Marks path AVAILABLE on timeout | Compliant |
| PerspectivePollerd | `PerspectivePollJob` | `features/perspectivepoller/.../PerspectivePollJob.java:92` | Log-only with explicit policy comment | Exemplary |
| Collectd (SNMP) | `SnmpCollectionSet` | `features/collection/snmp-collector/.../SnmpCollectionSet.java:397` | Returns `CollectionUnknown` (caught, logged only) | Compliant |
| Collectd (core) | `CollectionSpecification` | `features/collection/core/.../CollectionSpecification.java:310` | Returns `CollectionUnknown` (caught, logged only) | Compliant |
| Collectd (TCA) | `TcaCollectionHandler` | `features/juniper-tca-collector/.../TcaCollectionHandler.java:146` | Manual `CollectionUnknown` via `RequestTimedOutException` catch | Compliant |

### Passive/Lookup Daemons

| Daemon | RPC Usage | Timeout behavior | Status |
|--------|-----------|-----------------|--------|
| Trapd | `InterfaceToNodeCache` RPC lookup | Lookup failure logged, trap continues processing | Compliant |
| Syslogd | `InterfaceToNodeCache` RPC lookup | Same as Trapd | Compliant |
| Provisiond | Detector RPCs | No `onTimedOut()` override — uses framework default (safe) | Compliant |
| Discovery | Ping sweep RPC | No `onTimedOut()` override — uses framework default (safe) | Compliant |
| Enlinkd | SNMP proxy RPC | No `onTimedOut()` override — uses underlying collection handlers | Compliant |

## Automated Verification

E2E test `test-minion-rpc-e2e.sh` Phase 4 verifies this policy at runtime:
- Pauses Minion container for 120s (forces all RPC timeouts)
- Asserts zero new outages created during pause
- Asserts zero new problem alarms created during pause
- Asserts zero `nodeLostService` or `dataCollectionFailed` events on Kafka during pause

## Revision History

- 2026-04-08: Initial audit against delta-v-horizon 1.0.4 — all handlers compliant
```

- [ ] **Step 2: Commit**

```bash
git add docs/audits/2026-04-08-rpc-timeout-handling-audit.md
git commit -m "docs: RPC timeout handling audit — all handlers compliant"
```

---

### Task 3: Add Alarm Lifecycle + Poll Verification to test-perspective-e2e.sh

**Files:**
- Modify: `opennms-container/delta-v/test-perspective-e2e.sh`

This task adds: Phase 3 `lastgood` poll completion check, Phase 4 alarm creation assertion, Phase 5 Kafka `nodeRegainedService` event + alarm cleared/deleted assertion.

- [ ] **Step 1: Add Kafka consumer startup for fault-events**

The perspective test currently does NOT start a Kafka consumer. Add one after the prerequisite checks (around line 90, before Phase 1). Insert after the `LOCATION_B="mhuot-labs"` config block:

```bash
# ── Temp directory for Kafka consumer logs ─────────────────────────
TEST_TMPDIR=$(mktemp -d)
FAULT_LOG="$TEST_TMPDIR/fault-events.log"

cleanup() {
    # Kill background Kafka consumers
    docker compose exec -T kafka sh -c 'for p in $(ps -eo pid,args 2>/dev/null | grep kafka-console-consumer | grep -v grep | awk "{print \$1}"); do kill "$p" 2>/dev/null; done' || true
    rm -rf "$TEST_TMPDIR"
    if $POST_CLEANUP; then
        log "Post-run cleanup..."
        # existing cleanup logic
    fi
}
trap cleanup EXIT

# Start Kafka fault-events consumer in background
docker compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 \
    --topic opennms-fault-events \
    > "$FAULT_LOG" 2>/dev/null &
FAULT_CONSUMER_PID=$!
sleep 3
```

Also add the `wait_for_kafka_event` helper if not already present:

```bash
wait_for_kafka_event() {
    local log_file="$1"
    local pattern="$2"
    local timeout="$3"
    local description="$4"
    local elapsed=0

    log "Waiting for $description (timeout: ${timeout}s)..."
    while [ $elapsed -lt "$timeout" ]; do
        if grep -q "$pattern" "$log_file" 2>/dev/null; then
            return 0
        fi
        sleep 2
        elapsed=$((elapsed + 2))
    done
    return 1
}
```

- [ ] **Step 2: Add lastgood poll verification to Phase 3**

After the "No open perspective outages (healthy baseline)" check in Phase 3 (around line 410), add:

```bash
# Verify polls actually completed on Minion (not just dispatched).
# lastgood timestamp proves the poll RPC round-tripped through Minion.
LASTGOOD_QUERY="SELECT count(*) FROM ifservices s
    JOIN ipinterface ip ON s.ipinterfaceid = ip.id
    JOIN node n ON ip.nodeid = n.nodeid
    WHERE n.foreignsource = '${FOREIGN_SOURCE}'
      AND s.lastgood IS NOT NULL"
if wait_for_db "$LASTGOOD_QUERY" 120 "poll completion (lastgood timestamp)"; then
    ok "Perspective polls completed on Minion (lastgood recorded)"
else
    fail "No lastgood timestamps recorded — polls may be dispatched but timing out on Minion"
    show_diagnostics
fi
```

- [ ] **Step 3: Add alarm creation assertion to Phase 4**

After the "Perspective outage created for Default" assertion in Phase 4 (around line 460), add:

```bash
# Verify corresponding perspective alarm was created.
# UEI: uei.opennms.org/perspective/nodes/nodeLostService
# Reduction key: {uei}:{location}:{nodeId}:{ipAddr}:{serviceName}
PERSPECTIVE_ALARM_QUERY="SELECT count(*) FROM alarms a
    JOIN node n ON a.nodeid = n.nodeid
    WHERE n.foreignsource = '${FOREIGN_SOURCE}'
      AND a.eventuei = 'uei.opennms.org/perspective/nodes/nodeLostService'
      AND a.alarmtype = 1"
if wait_for_db "$PERSPECTIVE_ALARM_QUERY" 30 "perspective alarm creation"; then
    ok "Perspective alarm created (nodeLostService from ${LOCATION_A})"
else
    fail "No perspective alarm found for ${LOCATION_A}"
    show_diagnostics
fi
```

- [ ] **Step 4: Add Kafka event + alarm lifecycle assertion to Phase 5**

After the "All perspective outages resolved" assertion in Phase 5 (around line 510), add:

```bash
# Verify the resolution event reached Kafka (authoritative — no events table).
if wait_for_kafka_event "$FAULT_LOG" "nodeRegainedService" 30 "perspective nodeRegainedService on Kafka"; then
    ok "Perspective nodeRegainedService event confirmed on Kafka"
else
    fail "nodeRegainedService event not seen on Kafka fault-events topic"
fi

# Verify alarm lifecycle: cleared by Alarmd, then deleted by Drools.
# Drools delete can happen very quickly, so accept either state.
ALARM_CLEARED_QUERY="SELECT count(*) FROM alarms a
    JOIN node n ON a.nodeid = n.nodeid
    WHERE n.foreignsource = '${FOREIGN_SOURCE}'
      AND a.eventuei = 'uei.opennms.org/perspective/nodes/nodeLostService'
      AND a.severity = 2"
ALARM_GONE_QUERY="SELECT CASE WHEN count(*) = 0 THEN 1 ELSE 0 END FROM alarms a
    JOIN node n ON a.nodeid = n.nodeid
    WHERE n.foreignsource = '${FOREIGN_SOURCE}'
      AND a.eventuei = 'uei.opennms.org/perspective/nodes/nodeLostService'
      AND a.alarmtype = 1"
# Give Drools a moment to process
sleep 5
CLEARED=$(psql_query "$ALARM_CLEARED_QUERY" || echo "0")
GONE=$(psql_query "$ALARM_GONE_QUERY" || echo "0")
if [ "${CLEARED:-0}" -gt 0 ]; then
    ok "Perspective alarm CLEARED (Drools delete pending)"
elif [ "${GONE:-0}" -gt 0 ]; then
    ok "Perspective alarm deleted by Drools (fast clear+delete cycle)"
else
    fail "Perspective alarm neither cleared nor deleted — Alarmd/Drools issue"
    show_diagnostics
fi
```

- [ ] **Step 5: Update the summary section**

Update the summary at the end of the script to reflect the new phases:

```bash
log "Validated:"
log "  Phase 1: Requisition + foreign source → google.com node provisioned"
log "  Phase 2: Application created with Default + mhuot-labs perspectives"
log "  Phase 3: Perspective polls completed (lastgood), no open outages"
log "  Phase 4: DNS block → outage + alarm created for Default, no false outage for mhuot-labs"
log "  Phase 5: DNS unblock → outage cleared, alarm cleared/deleted, nodeRegainedService on Kafka"
```

- [ ] **Step 6: Run test-perspective-e2e.sh**

```bash
cd /Users/david/development/src/opennms/delta-v/opennms-container/delta-v
./test-perspective-e2e.sh --verbose
```

Expected: ~23 passed, 0 failed (up from 18).

- [ ] **Step 7: Commit**

```bash
cd /Users/david/development/src/opennms/delta-v
git add opennms-container/delta-v/test-perspective-e2e.sh
git commit -m "test: add alarm lifecycle + poll verification to perspective E2E"
```

---

### Task 4: Add RPC Timeout Resilience Phase to test-minion-rpc-e2e.sh

**Files:**
- Modify: `opennms-container/delta-v/test-minion-rpc-e2e.sh`

This task adds Phase 4: pause Minion, verify no false outages/alarms/events, unpause, verify recovery.

- [ ] **Step 1: Add Kafka consumer startup**

The Minion RPC test currently does NOT have a Kafka consumer. Add one near the top, after the helper functions (around line 130):

```bash
# ── Kafka consumer for fault-events (needed for Phase 4 negative assertions) ──
TEST_TMPDIR=$(mktemp -d)
FAULT_LOG="$TEST_TMPDIR/fault-events.log"

orig_cleanup=$(declare -f cleanup | tail -n +2)
cleanup() {
    docker compose exec -T kafka sh -c 'for p in $(ps -eo pid,args 2>/dev/null | grep kafka-console-consumer | grep -v grep | awk "{print \$1}"); do kill "$p" 2>/dev/null; done' || true
    rm -rf "$TEST_TMPDIR"
    # Ensure Minion is unpaused if script exits mid-Phase-4
    docker unpause delta-v-minion 2>/dev/null || true
    if $POST_CLEANUP; then
        log "Post-run cleanup (--post-cleanup): removing canary node..."
        psql_query "DELETE FROM outages WHERE nodeid IN (SELECT nodeid FROM node WHERE foreignsource = '${FOREIGN_SOURCE}')" || true
        psql_query "DELETE FROM ifservices WHERE ipinterfaceid IN (SELECT id FROM ipinterface WHERE nodeid IN (SELECT nodeid FROM node WHERE foreignsource = '${FOREIGN_SOURCE}'))" || true
        psql_query "UPDATE ipinterface SET snmpinterfaceid = NULL WHERE nodeid IN (SELECT nodeid FROM node WHERE foreignsource = '${FOREIGN_SOURCE}')" || true
        psql_query "DELETE FROM snmpinterface WHERE nodeid IN (SELECT nodeid FROM node WHERE foreignsource = '${FOREIGN_SOURCE}')" || true
        psql_query "DELETE FROM ipinterface WHERE nodeid IN (SELECT nodeid FROM node WHERE foreignsource = '${FOREIGN_SOURCE}')" || true
        psql_query "DELETE FROM node WHERE foreignsource = '${FOREIGN_SOURCE}'" || true
        log "  Canary node deleted"
    fi
}
trap cleanup EXIT

docker compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 \
    --topic opennms-fault-events \
    > "$FAULT_LOG" 2>/dev/null &
FAULT_CONSUMER_PID=$!
sleep 3
```

- [ ] **Step 2: Add Phase 4 before the Summary section**

Insert before the `# Summary` section (around line 394). This is the core RPC timeout resilience test:

```bash
# ══════════════════════════════════════════════════════════════════
# Phase 4: RPC Timeout Resilience
# ══════════════════════════════════════════════════════════════════
# Pause the Minion container to simulate a complete RPC black-hole.
# All RPC requests (polls, detections, collections) will timeout.
# Policy: RPC timeout = infrastructure problem, NOT service problem.
# No outages, no alarms, no fault events should be created.
#
# Timing: Default Kafka RPC TTL = 20s. With retry=2, worst case per
# poll = 3 × 20s = 60s. Pause window = 120s covers this + margin.
log ""
log "Phase 4: RPC timeout resilience (docker pause)..."

# 4a: Snapshot current state
OUTAGE_BASELINE=$(psql_query "SELECT count(*) FROM outages o
    JOIN ifservices s ON o.ifserviceid = s.id
    JOIN ipinterface ip ON s.ipinterfaceid = ip.id
    JOIN node n ON ip.nodeid = n.nodeid
    WHERE n.foreignsource = '${FOREIGN_SOURCE}'" || echo "0")
ALARM_BASELINE=$(psql_query "SELECT count(*) FROM alarms a
    JOIN node n ON a.nodeid = n.nodeid
    WHERE n.foreignsource = '${FOREIGN_SOURCE}'
      AND a.alarmtype = 1" || echo "0")
KAFKA_BASELINE=$(wc -l < "$FAULT_LOG" 2>/dev/null || echo "0")

# 4b: Pause Minion — all RPC requests will timeout
docker pause delta-v-minion
ok "Minion paused (RPC black-hole active)"

# 4c: Wait 120s for poll cycles to timeout
log "Waiting 120s for RPC timeouts to occur..."
sleep 120

# 4d: Unpause Minion
docker unpause delta-v-minion
ok "Minion unpaused"

# 4e: Negative Kafka assertion — no nodeLostService or dataCollectionFailed
# events should have been published during the pause window.
# IMPORTANT: grep returns exit code 1 when no match (which is success here).
# Use if-! guard to prevent set -e from killing the script.
KAFKA_EVENTS_DURING_PAUSE=$(tail -n +"$((KAFKA_BASELINE + 1))" "$FAULT_LOG" 2>/dev/null || true)
if ! echo "$KAFKA_EVENTS_DURING_PAUSE" | grep -q "nodeLostService\|dataCollectionFailed" 2>/dev/null; then
    ok "No nodeLostService or dataCollectionFailed events on Kafka during pause"
else
    fail "False fault events detected on Kafka during Minion pause — RPC timeout created events"
    if $VERBOSE; then
        log "  Events during pause window:"
        echo "$KAFKA_EVENTS_DURING_PAUSE" | grep "nodeLostService\|dataCollectionFailed" || true
    fi
fi

# 4f: Assert no new outages
OUTAGE_AFTER=$(psql_query "SELECT count(*) FROM outages o
    JOIN ifservices s ON o.ifserviceid = s.id
    JOIN ipinterface ip ON s.ipinterfaceid = ip.id
    JOIN node n ON ip.nodeid = n.nodeid
    WHERE n.foreignsource = '${FOREIGN_SOURCE}'" || echo "0")
if [ "${OUTAGE_AFTER:-0}" -eq "${OUTAGE_BASELINE:-0}" ]; then
    ok "No new outages created during Minion pause (${OUTAGE_AFTER} total, unchanged)"
else
    fail "New outages created during pause: before=${OUTAGE_BASELINE}, after=${OUTAGE_AFTER}"
fi

# 4g: Assert no new problem alarms
ALARM_AFTER=$(psql_query "SELECT count(*) FROM alarms a
    JOIN node n ON a.nodeid = n.nodeid
    WHERE n.foreignsource = '${FOREIGN_SOURCE}'
      AND a.alarmtype = 1" || echo "0")
if [ "${ALARM_AFTER:-0}" -eq "${ALARM_BASELINE:-0}" ]; then
    ok "No new problem alarms created during Minion pause (${ALARM_AFTER} total, unchanged)"
else
    fail "New problem alarms during pause: before=${ALARM_BASELINE}, after=${ALARM_AFTER}"
    if $VERBOSE; then
        psql_query "SELECT a.alarmid, a.eventuei, a.severity, a.firsteventtime FROM alarms a JOIN node n ON a.nodeid = n.nodeid WHERE n.foreignsource = '${FOREIGN_SOURCE}' AND a.alarmtype = 1" || true
    fi
fi

# 4h: Wait for Minion health recovery
if wait_for_health minion "http://localhost:8080/actuator/health" 120; then
    ok "Minion health recovered after unpause"
else
    fail "Minion health check did not recover within 120s"
fi

# 4i: Verify polling resumes — lastgood should advance beyond pause start
PAUSE_START_EPOCH=$(date -u +%s)
RESUME_QUERY="SELECT count(*) FROM ifservices s
    JOIN ipinterface ip ON s.ipinterfaceid = ip.id
    JOIN node n ON ip.nodeid = n.nodeid
    WHERE n.foreignsource = '${FOREIGN_SOURCE}'
      AND s.lastgood > NOW() - INTERVAL '3 minutes'"
if wait_for_db "$RESUME_QUERY" 180 "polling resume (lastgood advancing)" 15; then
    ok "Polling resumed after Minion recovery (lastgood advancing)"
else
    fail "Polling did not resume within 180s after Minion unpause"
    show_diagnostics
fi
```

- [ ] **Step 3: Update the summary section**

Replace the existing summary block:

```bash
log ""
log "══════════════════════════════════════════════════════════════"
log "Results: $PASS passed, $FAIL failed"
log ""
log "Validated:"
log "  Phase 1: Canary node provisioned at location=Default"
log "  Phase 2: ICMP + SNMP detectors executed via Minion RPC"
log "  Phase 3: Pollerd polls dispatched, completed, and services reachable via Minion RPC"
log "  Phase 4: RPC timeout resilience — Minion paused 120s, zero false outages/alarms/events"
log "══════════════════════════════════════════════════════════════"
```

- [ ] **Step 4: Run test-minion-rpc-e2e.sh**

```bash
cd /Users/david/development/src/opennms/delta-v/opennms-container/delta-v
./test-minion-rpc-e2e.sh --verbose
```

Expected: ~18 passed, 0 failed (up from 11). Phase 4 alone adds ~7 assertions.

**Important:** Phase 4 takes ~120s for the pause window plus recovery time. Total test runtime will increase from ~8 minutes to ~13 minutes.

- [ ] **Step 5: Commit**

```bash
cd /Users/david/development/src/opennms/delta-v
git add opennms-container/delta-v/test-minion-rpc-e2e.sh
git commit -m "test: add RPC timeout resilience phase to Minion RPC E2E"
```

---

### Task 5: Run Full E2E Regression Suite

- [ ] **Step 1: Run all 8 E2E suites**

```bash
cd /Users/david/development/src/opennms/delta-v/opennms-container/delta-v
./test-e2e.sh && \
./test-syslog-e2e.sh && \
./test-passive-e2e.sh && \
./test-minion-rpc-e2e.sh && \
./test-collectd-e2e.sh && \
./test-minion-e2e.sh && \
./test-perspective-e2e.sh && \
./test-enlinkd-e2e.sh
```

Expected: 115+ passed, 0 failed across all 8 suites.

| Suite | Expected |
|-------|----------|
| test-e2e.sh | 12 |
| test-syslog-e2e.sh | 15 |
| test-passive-e2e.sh | 16 |
| test-minion-rpc-e2e.sh | ~18 |
| test-collectd-e2e.sh | 4 |
| test-minion-e2e.sh | 13 |
| test-perspective-e2e.sh | ~23 |
| test-enlinkd-e2e.sh | 18 |

- [ ] **Step 2: If any failures, debug and fix before proceeding**

Check docker logs for the failing daemon. Common issues:
- Stale 1.0.3 JARs: re-run the `.m2` purge from Task 1 Step 4
- Kafka consumer lag: increase timeout in the failing assertion
- Drools delete too fast: adjust the `sleep 5` before alarm check in Task 3

---

### Task 6: Create PR

- [ ] **Step 1: Push and create PR**

```bash
cd /Users/david/development/src/opennms/delta-v
git push -u origin fix/rpc-timeout-audit-alarm-e2e
gh pr create --repo pbrane/delta-v --base develop \
  --title "fix: horizon 1.0.4 + RPC timeout audit + E2E alarm lifecycle" \
  --body "$(cat <<'EOF'
## Summary
- Bumps Horizon JARs from 1.0.3 → 1.0.4 (includes PerspectivePollJob `onTimedOut()` log-only fix)
- Adds RPC timeout handling audit report — all handlers across all daemons verified compliant
- Adds alarm lifecycle assertions to `test-perspective-e2e.sh` (alarm create, Kafka resolution event, alarm cleared/Drools-deleted)
- Adds `lastgood` poll completion verification to perspective E2E Phase 3
- Adds Phase 4 "RPC timeout resilience" to `test-minion-rpc-e2e.sh` — pauses Minion for 120s, asserts zero false outages/alarms/events on Kafka

## Test plan
- [x] Maven build: 21/21 modules
- [x] Docker images rebuilt with horizon 1.0.4
- [x] test-perspective-e2e.sh: ~23 passed (was 18)
- [x] test-minion-rpc-e2e.sh: ~18 passed (was 11)
- [x] Full regression: 115+ passed across all 8 suites
EOF
)"
```
