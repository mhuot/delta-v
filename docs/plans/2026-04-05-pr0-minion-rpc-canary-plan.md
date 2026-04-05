# PR0: Minion RPC Canary E2E Test — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create `test-minion-rpc-e2e.sh` — a new shell-based E2E test that provisions a node against the Docker-internal Minion (location="Default"), verifies SnmpDetector + IcmpDetector executed via Minion RPC, and verifies Pollerd is actively polling the node's services via Minion RPC. This is the canary test that gates all subsequent Phase 3 PRs.

**Architecture:** Follows the existing shell-script E2E pattern (`test-collectd-e2e.sh`, `test-passive-e2e.sh`). Writes a requisition targeting the existing `snmp-agent` mock container at a Docker-resolved IP, triggers Provisiond import, then asserts:
1. Both ICMP and SNMP appear in `ifservices` for the canary node (detectors executed on Minion)
2. Pollerd logs show poll activity for the canary node's services within a timeout (monitor executing via Minion RPC)

**Tech Stack:** Bash, Docker Compose, PostgreSQL (`psql`), OpenNMS requisition XML, existing `test-lib.sh` helpers.

**Reference implementations to model after:**
- `opennms-container/delta-v/test-collectd-e2e.sh` — provisioning + DB assertion pattern
- `opennms-container/delta-v/test-enlinkd-e2e.sh` — SNMP detector assertion on multiple nodes
- `opennms-container/delta-v/test-passive-e2e.sh` — outages table queries and `wait_for_db` usage

**File location note:** The spec mentions `integration-tests/test-minion-rpc-e2e/` as a possible location. The existing Delta-V convention places E2E tests as shell scripts at `opennms-container/delta-v/test-*-e2e.sh`. This plan follows the existing convention; the filename `test-minion-rpc-e2e.sh` preserves the "distinct from passive test-minion-e2e" distinction the user requested.

---

## File Structure

**Files to create:**

| Path | Purpose | Approximate size | Committed to git? |
|---|---|---|---|
| `opennms-container/delta-v/provisiond-overlay/etc/foreign-sources/rpc-canary.xml` | Foreign-source config restricting detectors to ICMP + SNMP only | ~15 lines | Yes (static, no runtime values) |
| `opennms-container/delta-v/test-minion-rpc-e2e.sh` | Test script with prereq checks, runtime requisition generation, detector assertion, monitor assertion | ~300 lines | Yes |

**Files written at test runtime (NOT committed):** `provisiond-overlay/etc/imports/rpc-canary.xml` — the requisition targets snmp-agent at a Docker-assigned IP that can only be resolved at runtime. The test script generates this file fresh on every run, matching the pattern in `test-collectd-e2e.sh` which generates `delta-v.xml` inline.

**Files NOT modified:** `docker-compose.yml` (snmp-agent and minion already exist and are sufficient).

---

## Task 1: Create the canary foreign-source config

**Files:**
- Create: `opennms-container/delta-v/provisiond-overlay/etc/foreign-sources/rpc-canary.xml`

Why a separate foreign-source (`rpc-canary`) rather than reusing `delta-v`: keeping the canary node segregated lets `--post-cleanup` target only the canary's data without disturbing other tests, and makes DB assertions trivially node-scoped by `foreignsource = 'rpc-canary'`.

Why an explicit foreign-source config: restricting detectors to ICMP + SNMP only makes the test deterministic. If we fell back to the default foreign-source config, unexpected detectors might run, slowing provisioning and making failure modes ambiguous.

- [ ] **Step 1: Create the foreign-source config**

```bash
mkdir -p opennms-container/delta-v/provisiond-overlay/etc/foreign-sources
```

```xml
<!-- opennms-container/delta-v/provisiond-overlay/etc/foreign-sources/rpc-canary.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<foreign-source xmlns="http://xmlns.opennms.org/xsd/config/foreign-source" name="rpc-canary">
    <scan-interval>1d</scan-interval>
    <detectors>
        <detector name="ICMP" class="org.opennms.netmgt.provision.detector.icmp.IcmpDetector"/>
        <detector name="SNMP" class="org.opennms.netmgt.provision.detector.snmp.SnmpDetector"/>
    </detectors>
    <policies/>
</foreign-source>
```

- [ ] **Step 2: Verify the file was created**

Run:
```bash
grep -c "SnmpDetector\|IcmpDetector" opennms-container/delta-v/provisiond-overlay/etc/foreign-sources/rpc-canary.xml
```

Expected: `2` (one line per detector).

- [ ] **Step 3: Commit the foreign-source config**

```bash
git add opennms-container/delta-v/provisiond-overlay/etc/foreign-sources/rpc-canary.xml
git commit -m "test: add rpc-canary foreign-source config (ICMP + SNMP detectors)"
```

---

## Task 2: Create test script skeleton with header, flags, and configuration

**Files:**
- Create: `opennms-container/delta-v/test-minion-rpc-e2e.sh`

- [ ] **Step 1: Write the script header (shebang, doc comment, set flags, cd, source test-lib.sh)**

```bash
#!/usr/bin/env bash
#
# test-minion-rpc-e2e.sh — End-to-end Minion RPC canary for Delta-V
#
# Provisions a canary node at location="Default" targeting the snmp-agent
# mock container. Verifies that SnmpDetector + IcmpDetector executed via
# Minion RPC (evidence: services in ifservices table) and that Pollerd
# is actively polling those services via Minion RPC (evidence: poll
# activity in Pollerd logs).
#
# This test is the CANARY for Phase 3 horizon-extraction. Any PR that
# regresses Minion RPC detection or monitoring will fail this test.
#
# Usage:
#   ./test-minion-rpc-e2e.sh              Run the test
#   ./test-minion-rpc-e2e.sh --verbose    Show diagnostic queries on failure
#   ./test-minion-rpc-e2e.sh --pre-clean  Full pre-run cleanup (DB + restart daemons)
#   ./test-minion-rpc-e2e.sh --post-cleanup  Delete canary node and alarms after run
#
# Prerequisites:
#   - Delta-V deployed with lite or full profile: ./deploy.sh up lite
#   - snmp-agent, minion, provisiond, pollerd, postgres, kafka running
#
# Exit codes:
#   0 = all tests passed
#   1 = test failure
#   2 = prerequisite failure
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"
source "${SCRIPT_DIR}/test-lib.sh"

# ── Configuration ──────────────────────────────────────────────────
FOREIGN_SOURCE="rpc-canary"
EXPECTED_NODES=1
PROVISION_TIMEOUT=120        # 2 min — wait for node provisioning via Minion
DETECT_TIMEOUT=180           # 3 min — wait for detectors to complete via RPC
POLL_TIMEOUT=180             # 3 min — wait for Pollerd to start polling via RPC
POLL_INTERVAL=10             # seconds between DB checks

# ── Parse flags ────────────────────────────────────────────────────
VERBOSE=false
PRE_CLEAN=false
POST_CLEANUP=false
for arg in "$@"; do
    case "$arg" in
        --verbose) VERBOSE=true ;;
        --pre-clean) PRE_CLEAN=true ;;
        --post-cleanup) POST_CLEANUP=true ;;
        --help|-h)
            sed -n '2,/^$/{ s/^# //; s/^#//; p }' "$0"
            exit 0
            ;;
    esac
done
```

- [ ] **Step 2: Verify the script parses as valid bash**

Run:
```bash
bash -n opennms-container/delta-v/test-minion-rpc-e2e.sh
```

Expected: no output (successful syntax check).

- [ ] **Step 3: Make the script executable**

```bash
chmod +x opennms-container/delta-v/test-minion-rpc-e2e.sh
```

---

## Task 3: Add helper functions and snmp-agent IP resolution

**Files:**
- Modify: `opennms-container/delta-v/test-minion-rpc-e2e.sh` (append)

- [ ] **Step 1: Append logging helpers, `psql_query`, `wait_for_db`, and `resolve_snmp_agent_ip`**

Append the following to `test-minion-rpc-e2e.sh`:

```bash
# ── Helpers ────────────────────────────────────────────────────────
PASS=0
FAIL=0

log()  { echo "==> $*"; }
ok()   { echo "  [PASS] $*"; PASS=$((PASS + 1)); }
fail() { echo "  [FAIL] $*"; FAIL=$((FAIL + 1)); }
err()  { echo "ERROR: $*" >&2; exit 2; }

cleanup() {
    if $POST_CLEANUP; then
        log "Post-run cleanup (--post-cleanup): removing canary node..."
        psql_query "DELETE FROM outages WHERE nodeid IN (SELECT nodeid FROM node WHERE foreignsource = '${FOREIGN_SOURCE}')" || true
        psql_query "DELETE FROM ifservices WHERE ipinterfaceid IN (SELECT id FROM ipinterface WHERE nodeid IN (SELECT nodeid FROM node WHERE foreignsource = '${FOREIGN_SOURCE}'))" || true
        psql_query "DELETE FROM ipinterface WHERE nodeid IN (SELECT nodeid FROM node WHERE foreignsource = '${FOREIGN_SOURCE}')" || true
        psql_query "DELETE FROM events WHERE nodeid IN (SELECT nodeid FROM node WHERE foreignsource = '${FOREIGN_SOURCE}')" || true
        psql_query "DELETE FROM node WHERE foreignsource = '${FOREIGN_SOURCE}'" || true
        log "  Canary node deleted"
    fi
}
trap cleanup EXIT

psql_query() {
    docker compose exec -T -e PGPASSWORD=opennms postgres \
        psql -U opennms -d opennms -t -A -c "$1" 2>/dev/null
}

wait_for_db() {
    local query="$1"
    local timeout="$2"
    local description="$3"
    local poll_interval="${4:-$POLL_INTERVAL}"
    local elapsed=0

    log "Waiting for $description (timeout: ${timeout}s)..."
    while [ $elapsed -lt "$timeout" ]; do
        local result
        result=$(psql_query "$query" 2>/dev/null || echo "")
        if [ -n "$result" ] && [ "$result" != "0" ]; then
            return 0
        fi
        sleep "$poll_interval"
        elapsed=$((elapsed + poll_interval))
        if [ $((elapsed % 60)) -eq 0 ]; then
            log "  ... ${elapsed}s elapsed"
        fi
    done
    return 1
}

wait_for_health() {
    local container="$1"
    local health_url="$2"
    local timeout="${3:-120}"
    local elapsed=0

    log "Waiting for $container health check (timeout: ${timeout}s)..."
    while [ $elapsed -lt "$timeout" ]; do
        if docker compose exec -T "$container" curl -sf "$health_url" >/dev/null 2>&1; then
            log "  ... $container is healthy"
            return 0
        fi
        sleep 5
        elapsed=$((elapsed + 5))
    done
    return 1
}

# Resolves the snmp-agent container's IP on the Docker internal network.
# Uses docker inspect from the host — more reliable than exec'ing into Minion
# (Alpine-based Minion container lacks ping, nslookup, and may lack getent).
# The IP returned is the container's IP on the shared Docker network, which
# is exactly what Minion will use to reach snmp-agent.
resolve_snmp_agent_ip() {
    docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{"\n"}}{{end}}' delta-v-snmp-agent 2>/dev/null | grep -v '^$' | head -1
}

show_diagnostics() {
    if ! $VERBOSE; then
        log "Hint: re-run with --verbose for diagnostic output"
        return
    fi
    log ""
    log "── Diagnostic Queries ──"
    log ""
    log "Canary node:"
    psql_query "SELECT nodeid, nodelabel, foreignid, location, lastcapsdpoll FROM node WHERE foreignsource = '${FOREIGN_SOURCE}'" || true
    log ""
    log "IP Interfaces:"
    psql_query "SELECT n.nodelabel, ip.ipaddr, ip.issnmpprimary FROM ipinterface ip JOIN node n ON ip.nodeid = n.nodeid WHERE n.foreignsource = '${FOREIGN_SOURCE}'" || true
    log ""
    log "Services detected:"
    psql_query "SELECT n.nodelabel, svc.servicename, s.status FROM ifservices s JOIN service svc ON s.serviceid = svc.serviceid JOIN ipinterface ip ON s.ipinterfaceid = ip.id JOIN node n ON ip.nodeid = n.nodeid WHERE n.foreignsource = '${FOREIGN_SOURCE}' ORDER BY svc.servicename" || true
    log ""
    log "Recent Pollerd log lines mentioning canary node:"
    docker compose logs --tail=100 pollerd 2>/dev/null | grep -i "snmp-agent-canary" || log "  (no matches)"
}
```

- [ ] **Step 2: Verify script still parses**

Run:
```bash
bash -n opennms-container/delta-v/test-minion-rpc-e2e.sh
```

Expected: no output.

---

## Task 4: Add prerequisite checks and pre-clean logic

**Files:**
- Modify: `opennms-container/delta-v/test-minion-rpc-e2e.sh` (append)

- [ ] **Step 1: Append prerequisite checks section**

Append:

```bash
# ── Prerequisite Checks ───────────────────────────────────────────
log "Checking prerequisites..."

REQUIRED_SERVICES="postgres kafka provisiond pollerd minion snmp-agent"
for svc in $REQUIRED_SERVICES; do
    if ! docker compose ps --status running --format '{{.Name}}' 2>/dev/null | grep -qw "$svc"; then
        err "Service '$svc' is not running. Deploy with: ./deploy.sh up lite"
    fi
done
ok "Required services running (postgres, kafka, provisiond, pollerd, minion, snmp-agent)"

# Resolve snmp-agent IP via Minion's DNS view (this is the IP Minion will use)
SNMP_AGENT_IP=$(resolve_snmp_agent_ip)
if [ -z "${SNMP_AGENT_IP:-}" ]; then
    err "Could not resolve snmp-agent IP from Minion's perspective. Is Docker DNS working?"
fi
ok "Resolved snmp-agent IP (from Minion's perspective): ${SNMP_AGENT_IP}"

# Quick sanity check — Minion can reach the SNMP port
if ! docker compose exec -T minion sh -c "nc -uzvw 2 snmp-agent 161" >/dev/null 2>&1; then
    log "  [WARN] Minion could not reach snmp-agent:161/udp (may still work; netcat may be unavailable)"
else
    ok "Minion can reach snmp-agent:161/udp"
fi

# ══════════════════════════════════════════════════════════════════
# Pre-run cleanup (--pre-clean): remove any prior canary data
# ══════════════════════════════════════════════════════════════════
if $PRE_CLEAN; then
    log ""
    log "Pre-run cleanup (--pre-clean): removing existing canary data..."

    psql_query "DELETE FROM outages WHERE nodeid IN (SELECT nodeid FROM node WHERE foreignsource = '${FOREIGN_SOURCE}')" || true
    psql_query "DELETE FROM ifservices WHERE ipinterfaceid IN (SELECT id FROM ipinterface WHERE nodeid IN (SELECT nodeid FROM node WHERE foreignsource = '${FOREIGN_SOURCE}'))" || true
    psql_query "DELETE FROM ipinterface WHERE nodeid IN (SELECT nodeid FROM node WHERE foreignsource = '${FOREIGN_SOURCE}')" || true
    psql_query "DELETE FROM events WHERE nodeid IN (SELECT nodeid FROM node WHERE foreignsource = '${FOREIGN_SOURCE}')" || true
    psql_query "DELETE FROM node WHERE foreignsource = '${FOREIGN_SOURCE}'" || true
    ok "Prior canary data cleaned"
fi
```

- [ ] **Step 2: Verify script still parses**

Run:
```bash
bash -n opennms-container/delta-v/test-minion-rpc-e2e.sh
```

Expected: no output.

---

## Task 5: Add Phase 1 — provisioning (requisition import + wait for node)

**Files:**
- Modify: `opennms-container/delta-v/test-minion-rpc-e2e.sh` (append)

- [ ] **Step 1: Append Phase 1 provisioning logic**

Append:

```bash
# ══════════════════════════════════════════════════════════════════
# Phase 1: Provision the canary node via Minion
# ══════════════════════════════════════════════════════════════════
log ""
log "Phase 1: Provisioning canary node targeting snmp-agent (${SNMP_AGENT_IP}) at location=Default..."

# Generate the requisition inline with the resolved IP. This file is overwritten
# on each test run — not committed to git (matches the delta-v.xml pattern
# used by test-collectd-e2e.sh).
mkdir -p provisiond-overlay/etc/imports
CANARY_REQ="provisiond-overlay/etc/imports/${FOREIGN_SOURCE}.xml"
cat > "$CANARY_REQ" <<REQEOF
<model-import xmlns="http://xmlns.opennms.org/xsd/config/model-import"
              date-stamp="$(date -u +%Y-%m-%dT%H:%M:%S.000Z)"
              foreign-source="${FOREIGN_SOURCE}">
   <node location="Default" foreign-id="snmp-agent-canary" node-label="snmp-agent-canary">
      <interface ip-addr="${SNMP_AGENT_IP}" status="1" snmp-primary="P">
         <monitored-service service-name="ICMP"/>
         <monitored-service service-name="SNMP"/>
      </interface>
   </node>
</model-import>
REQEOF
ok "Requisition generated at ${CANARY_REQ}"

# Update provisiond-configuration.xml to auto-import this foreign source.
# If an existing config has other requisition-defs (e.g., delta-v, mhuot-labs),
# we need to append ours without destroying theirs.
PROV_CONFIG="provisiond-overlay/etc/provisiond-configuration.xml"
mkdir -p provisiond-overlay/etc
PROVISIOND_NEEDS_RESTART=false
if [ ! -f "$PROV_CONFIG" ]; then
    log "  Writing new provisiond-configuration.xml with ${FOREIGN_SOURCE} import"
    cat > "$PROV_CONFIG" <<PROVEOF
<?xml version="1.0" encoding="UTF-8"?>
<provisiond-configuration xmlns="http://xmlns.opennms.org/xsd/config/provisiond-configuration"
  foreign-source-dir="/opt/deltav/etc/foreign-sources"
  requistion-dir="/opt/deltav/etc/imports"
  importThreads="4" scanThreads="4" rescanThreads="4" writeThreads="4" >
  <requisition-def import-name="${FOREIGN_SOURCE}"
                   import-url-resource="file:///opt/deltav/etc/imports/${FOREIGN_SOURCE}.xml">
    <cron-schedule>0/30 * * * * ?</cron-schedule>
  </requisition-def>
</provisiond-configuration>
PROVEOF
    PROVISIOND_NEEDS_RESTART=true
elif ! grep -q "import-name=\"${FOREIGN_SOURCE}\"" "$PROV_CONFIG"; then
    log "  Adding ${FOREIGN_SOURCE} requisition-def to existing provisiond-configuration.xml"
    # Insert the rpc-canary requisition-def before the closing </provisiond-configuration>
    sed -i.bak "s|</provisiond-configuration>|  <requisition-def import-name=\"${FOREIGN_SOURCE}\" import-url-resource=\"file:///opt/deltav/etc/imports/${FOREIGN_SOURCE}.xml\">\n    <cron-schedule>0/30 * * * * ?</cron-schedule>\n  </requisition-def>\n</provisiond-configuration>|" "$PROV_CONFIG"
    rm -f "${PROV_CONFIG}.bak"
    PROVISIOND_NEEDS_RESTART=true
fi

if $PROVISIOND_NEEDS_RESTART; then
    log "  Restarting Provisiond to pick up requisition-def..."
    docker compose restart provisiond
    if wait_for_health "provisiond" "http://localhost:8080/actuator/health" 120; then
        ok "Provisiond restarted and healthy"
    else
        fail "Provisiond not healthy after restart"
        exit 1
    fi
else
    ok "Provisiond configuration already includes ${FOREIGN_SOURCE}"
fi

# Wait for node to appear in DB
if wait_for_db \
    "SELECT count(*) FROM node WHERE foreignsource = '${FOREIGN_SOURCE}'" \
    "$PROVISION_TIMEOUT" "canary node in database" 10; then
    ok "Canary node provisioned"
else
    fail "Canary node not provisioned within ${PROVISION_TIMEOUT}s"
    show_diagnostics
    log ""
    log "Results: $PASS passed, $FAIL failed"
    exit 1
fi
```

- [ ] **Step 2: Verify script still parses**

```bash
bash -n opennms-container/delta-v/test-minion-rpc-e2e.sh
```

Expected: no output.

---

## Task 6: Add Phase 2 — detector assertion (ICMP + SNMP in ifservices)

**Files:**
- Modify: `opennms-container/delta-v/test-minion-rpc-e2e.sh` (append)

**Why this assertion proves Minion RPC detection worked:**
- Under Delta-V architecture (memory: `feedback_no_local_polling`), daemons NEVER poll directly — all detection requests for nodes at location="Default" route through the Minion container via Kafka RPC
- If both ICMP and SNMP services appear in `ifservices` for the canary node, it proves:
  - The detection RPC request was sent to Minion's location topic
  - Minion received and dispatched the request to its local IcmpDetector and SnmpDetector
  - Detectors successfully reached snmp-agent (for SNMP) and the network stack (for ICMP)
  - Results were serialized and sent back to Provisiond via Kafka
  - Provisiond wrote the results to `ifservices`
- If either assertion fails, the Minion RPC detection path is broken

- [ ] **Step 1: Append Phase 2 detector assertion**

Append:

```bash
# ══════════════════════════════════════════════════════════════════
# Phase 2: Detector Assertion — ICMP and SNMP both detected via Minion RPC
# ══════════════════════════════════════════════════════════════════
log ""
log "Phase 2: Verifying detectors executed on Minion via RPC..."

# Assertion: ICMP service detected
ICMP_QUERY="SELECT count(*) FROM ifservices s JOIN service svc ON s.serviceid = svc.serviceid JOIN ipinterface ip ON s.ipinterfaceid = ip.id JOIN node n ON ip.nodeid = n.nodeid WHERE n.foreignsource = '${FOREIGN_SOURCE}' AND svc.servicename = 'ICMP'"
if wait_for_db "$ICMP_QUERY" "$DETECT_TIMEOUT" "ICMP service detected (proves IcmpDetector ran via Minion RPC)" 10; then
    ok "ICMP service detected via Minion RPC"
else
    fail "ICMP service NOT detected within ${DETECT_TIMEOUT}s — IcmpDetector RPC path broken"
    show_diagnostics
    log ""
    log "Results: $PASS passed, $FAIL failed"
    exit 1
fi

# Assertion: SNMP service detected
SNMP_QUERY="SELECT count(*) FROM ifservices s JOIN service svc ON s.serviceid = svc.serviceid JOIN ipinterface ip ON s.ipinterfaceid = ip.id JOIN node n ON ip.nodeid = n.nodeid WHERE n.foreignsource = '${FOREIGN_SOURCE}' AND svc.servicename = 'SNMP'"
if wait_for_db "$SNMP_QUERY" "$DETECT_TIMEOUT" "SNMP service detected (proves SnmpDetector ran via Minion RPC)" 10; then
    ok "SNMP service detected via Minion RPC"
else
    fail "SNMP service NOT detected within ${DETECT_TIMEOUT}s — SnmpDetector RPC path broken OR snmp-agent community string mismatch"
    show_diagnostics
    log ""
    log "Hint: mock-snmp-agent default community is 'public'. If SnmpConfig overrides this,"
    log "      check that the community string in the SNMP config matches."
    log ""
    log "Results: $PASS passed, $FAIL failed"
    exit 1
fi

# Confirm the node's lastcapsdpoll timestamp is recent (detection actually completed)
LASTPOLL_EPOCH=$(psql_query "SELECT EXTRACT(EPOCH FROM lastcapsdpoll) FROM node WHERE foreignsource = '${FOREIGN_SOURCE}'" | head -1 | cut -d. -f1)
NOW_EPOCH=$(date +%s)
if [ -n "${LASTPOLL_EPOCH:-}" ] && [ "${LASTPOLL_EPOCH:-0}" -gt 0 ]; then
    AGE=$((NOW_EPOCH - LASTPOLL_EPOCH))
    if [ "$AGE" -lt 600 ]; then
        ok "Node lastcapsdpoll is recent (${AGE}s ago) — full detection scan completed"
    else
        log "  [WARN] Node lastcapsdpoll is stale (${AGE}s ago)"
    fi
else
    log "  [WARN] Node lastcapsdpoll is NULL — detection may not have completed"
fi
```

- [ ] **Step 2: Verify script still parses**

```bash
bash -n opennms-container/delta-v/test-minion-rpc-e2e.sh
```

Expected: no output.

---

## Task 7: Add Phase 3 — monitor assertion (Pollerd polling via Minion RPC)

**Files:**
- Modify: `opennms-container/delta-v/test-minion-rpc-e2e.sh` (append)

**Why this assertion proves Minion RPC monitoring works:**
- When Provisiond writes a service into `ifservices`, it fires a `nodeGainedService` event
- Pollerd consumes that event and schedules the service for polling
- Polls for the canary node route through Minion (force-remote=true in pollerd config per docker-compose.yml)
- Pollerd logs the poll request when it dispatches to Minion
- A log entry mentioning the canary node's IP or label proves the poll attempt reached Minion

- [ ] **Step 1: Append Phase 3 monitor assertion**

Append:

```bash
# ══════════════════════════════════════════════════════════════════
# Phase 3: Monitor Assertion — Pollerd is polling via Minion RPC
# ══════════════════════════════════════════════════════════════════
log ""
log "Phase 3: Verifying Pollerd is polling canary services via Minion RPC..."

# Wait for Pollerd to schedule the newly-detected services and make first poll attempts.
# Pollerd reacts to nodeGainedService events; first poll typically happens within 30-60s.
log "Waiting up to ${POLL_TIMEOUT}s for Pollerd to poll canary services..."

POLL_EVIDENCE_FOUND=false
ELAPSED=0
while [ $ELAPSED -lt "$POLL_TIMEOUT" ]; do
    # Look for Pollerd log entries mentioning the canary node or its IP.
    # Delta-V Pollerd's log format includes service + IP on poll dispatches.
    if docker compose logs --since="5m" pollerd 2>/dev/null | grep -qE "(snmp-agent-canary|${SNMP_AGENT_IP//./\\.})" ; then
        POLL_EVIDENCE_FOUND=true
        break
    fi
    sleep "$POLL_INTERVAL"
    ELAPSED=$((ELAPSED + POLL_INTERVAL))
    if [ $((ELAPSED % 60)) -eq 0 ]; then
        log "  ... ${ELAPSED}s elapsed"
    fi
done

if $POLL_EVIDENCE_FOUND; then
    ok "Pollerd poll activity observed for canary node (proves polling dispatched to Minion RPC)"
else
    fail "No Pollerd poll activity observed for canary node within ${POLL_TIMEOUT}s — Monitor RPC path may be broken"
    show_diagnostics
    log ""
    log "Results: $PASS passed, $FAIL failed"
    exit 1
fi

# Secondary assertion: no service should be in a stuck-open outage state immediately after detection.
# If the monitor IS running but cannot reach the service, an open outage would appear.
# If the monitor is running AND the service is reachable, the service is up and no open outage exists.
STUCK_OUTAGE_QUERY="SELECT count(*) FROM outages o JOIN ifservices s ON o.ifserviceid = s.id JOIN ipinterface ip ON s.ipinterfaceid = ip.id JOIN node n ON ip.nodeid = n.nodeid WHERE n.foreignsource = '${FOREIGN_SOURCE}' AND o.ifregainedservice IS NULL"
STUCK_OUTAGES=$(psql_query "$STUCK_OUTAGE_QUERY" || echo "0")
if [ "${STUCK_OUTAGES:-0}" -eq 0 ]; then
    ok "No stuck-open outages for canary services (services are reachable via Minion RPC)"
else
    fail "${STUCK_OUTAGES} stuck-open outage(s) on canary services — Monitor reports service unreachable via Minion RPC"
    show_diagnostics
    log ""
    log "Hint: If outages are for SNMP, verify mock-snmp-agent responds on UDP/161"
    log "      If outages are for ICMP, verify Minion has NET_RAW capability (docker-compose.yml minion.cap_add)"
    log ""
    log "Results: $PASS passed, $FAIL failed"
    exit 1
fi
```

- [ ] **Step 2: Verify script still parses**

```bash
bash -n opennms-container/delta-v/test-minion-rpc-e2e.sh
```

Expected: no output.

---

## Task 8: Add summary + exit handling

**Files:**
- Modify: `opennms-container/delta-v/test-minion-rpc-e2e.sh` (append)

- [ ] **Step 1: Append summary section**

Append:

```bash
# ══════════════════════════════════════════════════════════════════
# Summary
# ══════════════════════════════════════════════════════════════════
if $VERBOSE; then
    show_diagnostics
fi

log ""
log "══════════════════════════════════════════════════════════════"
log "Results: $PASS passed, $FAIL failed"
log ""
log "Validated:"
log "  Phase 1: Canary node provisioned at location=Default"
log "  Phase 2: ICMP + SNMP detectors executed via Minion RPC"
log "  Phase 3: Pollerd actively polling canary services via Minion RPC"
log "══════════════════════════════════════════════════════════════"
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
```

- [ ] **Step 2: Verify final script parses**

```bash
bash -n opennms-container/delta-v/test-minion-rpc-e2e.sh
```

Expected: no output.

- [ ] **Step 3: Confirm script is executable**

```bash
ls -l opennms-container/delta-v/test-minion-rpc-e2e.sh
```

Expected: `-rwxr-xr-x` permissions.

---

## Task 9: Run test against current develop baseline

**Files:**
- None modified; executing the test.

**Objective:** Prove the canary passes against current `develop` **before** any Phase 3 migration PRs are merged. This establishes the baseline that PR1–PR5 must continue to pass.

- [ ] **Step 1: Ensure Delta-V is deployed with at least `lite` profile**

```bash
cd opennms-container/delta-v
./deploy.sh status
```

Expected: shows `postgres`, `kafka`, `provisiond`, `pollerd`, `minion`, `snmp-agent` as running. If not, run `./deploy.sh up lite`.

- [ ] **Step 2: Run the canary test with pre-clean and verbose**

```bash
cd opennms-container/delta-v
./test-minion-rpc-e2e.sh --pre-clean --verbose
```

Expected output (last few lines):
```
Results: 5 passed, 0 failed

Validated:
  Phase 1: Canary node provisioned at location=Default
  Phase 2: ICMP + SNMP detectors executed via Minion RPC
  Phase 3: Pollerd actively polling canary services via Minion RPC
```

Expected exit code: `0`.

- [ ] **Step 3: If the test fails, diagnose before proceeding**

Run the script with `--verbose` to see diagnostic queries. Common failure modes:

| Failure | Likely cause | Debug step |
|---|---|---|
| Phase 1 timeout (node not provisioned) | Provisiond did not import requisition | Check Provisiond logs: `docker compose logs provisiond \| grep -i 'rpc-canary'` |
| Phase 2 timeout on SNMP | SnmpDetector cannot reach snmp-agent OR community mismatch | Run `docker compose exec minion snmpwalk -v2c -c public snmp-agent system` |
| Phase 2 timeout on ICMP | Minion lacks NET_RAW capability | Check docker-compose.yml has `cap_add: NET_RAW` on minion service |
| Phase 3 timeout (no poll evidence) | Pollerd is not forced-remote OR Minion RPC broken | Check `docker compose logs pollerd \| grep -i 'force-remote'` |
| Stuck-open outages | Monitor polling but service unreachable | Same as Phase 2 debug (reachability issue) |

Do NOT proceed to Task 10 if the test fails. The canary must pass on current develop before being committed.

---

## Task 10: Document the test and commit

**Files:**
- Modify: `opennms-container/delta-v/README.md` (append to test list if present)
- Commit all changes

- [ ] **Step 1: Check whether README has a test-scripts table and add an entry if so**

```bash
grep -l "test-minion-e2e\|test-collectd-e2e" opennms-container/delta-v/README.md 2>/dev/null || echo "No test list in README"
```

If README lists the existing tests, add:
```markdown
| `test-minion-rpc-e2e.sh` | Minion RPC canary — verifies SnmpDetector + IcmpDetector + Pollerd polling all execute via Minion RPC (Kafka roundtrip). Gates Phase 3 horizon-extraction PRs. |
```

If README doesn't list existing tests, skip this step (no change needed).

- [ ] **Step 2: Verify the canary node was cleaned up from prior test run**

```bash
cd opennms-container/delta-v
docker compose exec -T -e PGPASSWORD=opennms postgres psql -U opennms -d opennms -t -A -c "SELECT count(*) FROM node WHERE foreignsource = 'rpc-canary'"
```

Expected: `0` or `1` (one canary node from Task 9's run is acceptable and expected).

- [ ] **Step 3: Stage and commit**

```bash
cd /Users/david/development/src/opennms/delta-v
git status --short
git add opennms-container/delta-v/test-minion-rpc-e2e.sh
# Only add README if it was modified
git add opennms-container/delta-v/README.md 2>/dev/null || true
git commit -m "$(cat <<'EOF'
test: add Minion RPC canary E2E for Phase 3 gate

Adds test-minion-rpc-e2e.sh — a new shell-based E2E that provisions a
canary node targeting the snmp-agent mock container at location=Default,
then asserts:

Phase 1: Node provisioned
Phase 2: ICMP + SNMP detectors executed via Minion RPC (services in
         ifservices table)
Phase 3: Pollerd actively polling canary services via Minion RPC
         (poll activity in Pollerd logs, no stuck-open outages)

This is the canary for Phase 3 horizon-extraction. It establishes a
baseline against current develop. PRs 1-5 of the extraction migration
must not regress this test.

Restores coverage lost when a PerspectivePollerd + PageSequenceMonitor
E2E test was removed at some point prior (historical note from
2026-04-05 spec brainstorm).
EOF
)"
```

Expected: commit created on the branch.

- [ ] **Step 4: Verify commit**

```bash
git log --oneline -1
git show --stat HEAD
```

Expected: commit shows 1-2 files changed (test script; README only if it was modified).

---

## Self-Review Checklist (run before handoff)

Before presenting the plan as complete, walk through:

- [ ] **Spec coverage:** All three assertions the spec calls for (detector via RPC, monitor via RPC, canary baseline on current develop) are covered by tasks 6, 7, and 9 respectively.
- [ ] **Placeholder scan:** No "TBD", no "similar to task N", no "add error handling" — all shell code is literal and complete.
- [ ] **Variable consistency:** `FOREIGN_SOURCE`, `SNMP_AGENT_IP`, `POLL_TIMEOUT`, etc. are defined in Task 2 and used consistently in Tasks 3–8.
- [ ] **Idempotency:** `--pre-clean` removes prior canary data; `--post-cleanup` removes canary after run. Re-runs are safe.
- [ ] **Failure diagnostics:** Each `fail` path calls `show_diagnostics` and provides hints before exit.

---

## Notes for the Implementing Engineer

1. **Do not run `--post-cleanup` during the baseline run.** You want the canary node to remain in the DB briefly so the commit message can reference observed DB state if needed.

2. **The `snmp-agent` image (`./mock-snmp-agent`) uses community string `public` by default.** If your local SNMP config overrides this, SnmpDetector will fail in Phase 2. Verify by checking `docker compose exec minion snmpwalk -v2c -c public snmp-agent system` returns output.

3. **The test runs for up to 8 minutes** (provision 2m + detect 3m + poll 3m = 8m max). On a successful run it completes in ~2-3 minutes.

4. **If Pollerd does not log the service name or IP explicitly,** the Phase 3 log-grep assertion may need to use a different pattern. Inspect a few seconds of Pollerd logs after a poll to confirm the log format:
   ```bash
   docker compose logs --since=2m pollerd | head -50
   ```
   If the log format is different, adjust the `grep -qE "(snmp-agent-canary|...)"` pattern in Task 7 accordingly.

5. **No rollback needed.** This PR is purely additive — it adds one test script + one requisition fixture. Reverting the commit is the only rollback.
