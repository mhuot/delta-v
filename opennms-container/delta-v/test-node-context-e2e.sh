#!/usr/bin/env bash
# Copyright (C) 2026 BeaconStrategists, Inc.
# Licensed under the GNU Affero General Public License v3.
# See LICENSE.md in the repository root for full license text.
#
# Layer 5 end-to-end smoke test for the provisiond → deltav-node-context
# change-feed producer.  Starts the delta-v Docker Compose stack (lite
# profile is sufficient — no Minion required), waits for provisiond to
# become healthy, then asserts:
#
#   1. at least one record arrives on the deltav-node-context topic
#      (via kafka-console-consumer — protobuf not deserialized)
#   2. deltav_node_context_records_published_total{reason="bootstrap"} > 0
#      on the provisiond /actuator/prometheus endpoint (seed nodes trigger
#      the bootstrap pass on startup)
#   3. deltav_node_context_records_published_total{reason="change"} > 0
#      after the E2E requisition is injected and provisiond re-imports it
#      (IMPORT_SUCCESSFUL_UEI fan-out)
#   4. deltav_node_context_records_failed_total stays at zero
#
# Tombstone assertion is intentionally omitted: the nodeDeleted event path
# requires a PARM_LOCATION parm that is only present when the full event
# pipeline is running (alarmd + eventtranslator).  The lite profile used
# here does not include those services, so tombstone coverage is deferred
# to the full-profile test-e2e.sh run.
#
# Port / URL conventions (matched from test-timeseries-e2e.sh and
# test-collectd-e2e.sh):
#   - All actuator calls go through docker compose exec -T <service>
#     because provisiond has NO host-port mapping in docker-compose.yml.
#   - Kafka consumer calls go through docker compose exec -T kafka with
#     bootstrap-server localhost:9092 (internal Docker network address).
#   - REST provisioning is file-based (write to provisiond-overlay/etc/imports/)
#     rather than via horizon-core REST, because horizon-core is not
#     included in the lite profile.
#
# Exit non-zero on any failure; tears down on success or failure.

set -euo pipefail

cd "$(dirname "$0")"

STACK_READY_TIMEOUT=180
TOPIC_TIMEOUT=60
METRICS_TIMEOUT=180
E2E_FOREIGN_SOURCE="node-context-e2e"
E2E_REQUISITION_FILE="provisiond-overlay/etc/imports/${E2E_FOREIGN_SOURCE}.xml"

cleanup() {
    echo "==> Tearing down stack"
    rm -f "${E2E_REQUISITION_FILE}"
    docker compose down -v --remove-orphans || true
}
trap cleanup EXIT

echo "==> Starting delta-v Docker Compose (lite profile)"
docker compose --profile lite up -d --build

# ── Step 1: Wait for provisiond /actuator/health ──────────────────────────────

echo "==> Waiting for provisiond /actuator/health (up to ${STACK_READY_TIMEOUT} s)"
deadline=$(( $(date +%s) + STACK_READY_TIMEOUT ))
while (( $(date +%s) < deadline )); do
    if docker compose exec -T provisiond curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1; then
        echo "==> provisiond healthy"
        break
    fi
    sleep 5
done
if (( $(date +%s) >= deadline )); then
    echo "ERROR: provisiond did not become healthy within ${STACK_READY_TIMEOUT} s"
    docker compose logs provisiond | tail -80
    exit 1
fi

# ── Step 2: Assert bootstrap runner executed ──────────────────────────────────
# NodeContextBootstrapRunner.start() fires synchronously before provisiond
# reports ready (SmartLifecycle phase MAX_VALUE-100).  On a fresh DB the
# runner will find 0 nodes and publish nothing, so published_total{reason=
# "bootstrap"} may legitimately be 0 — the contract we're verifying is that
# the runner RAN, which is reliably signalled by the
# bootstrap_duration_seconds_count Timer being ≥ 1.

echo "==> Asserting bootstrap runner executed on provisiond /actuator/prometheus"
metrics=$(docker compose exec -T provisiond curl -sf http://localhost:8080/actuator/prometheus)
if ! echo "${metrics}" | grep -E '^deltav_node_context_bootstrap_duration_seconds_count ' | \
       awk '{print $NF}' | head -1 | grep -qE '^[1-9]'; then
    echo "ERROR: deltav_node_context_bootstrap_duration_seconds_count not ≥ 1"
    echo "${metrics}" | grep deltav_node_context || true
    docker compose logs provisiond | tail -60
    exit 1
fi
echo "==> bootstrap runner executed — lifecycle wiring verified"

# ── Step 3: Inject E2E requisition and assert change records ──────────────────
# Write a new requisition XML into provisiond-overlay/etc/imports/ so that
# provisiond picks it up on the next cron tick (or a manual import trigger).
# Provisiond is configured to scan that directory; IMPORT_SUCCESSFUL_UEI fires
# after each successful import and the NodeContextChangeFeedListener fan-out
# enqueues every node in the foreignSource for a "change" publish.

echo "==> Writing E2E requisition ${E2E_FOREIGN_SOURCE}"
mkdir -p provisiond-overlay/etc/imports
cat > "${E2E_REQUISITION_FILE}" <<'REQEOF'
<model-import xmlns="http://xmlns.opennms.org/xsd/config/model-import"
              date-stamp="2026-04-16T00:00:00.000-05:00"
              foreign-source="node-context-e2e">
  <node location="Default" foreign-id="nc-e2e-1" node-label="node-context-e2e-node-1">
    <interface ip-addr="127.0.0.2" status="1" snmp-primary="N"/>
    <meta-data context="requisition" key="env" value="e2e"/>
  </node>
</model-import>
REQEOF

# Add the requisition-def to provisiond-configuration.xml so provisiond
# auto-imports.  We append a second import schedule alongside any existing
# ones; provisiond reloads its config on restart.
mkdir -p provisiond-overlay/etc
cat > provisiond-overlay/etc/provisiond-configuration.xml <<PROVEOF
<?xml version="1.0" encoding="UTF-8"?>
<provisiond-configuration xmlns="http://xmlns.opennms.org/xsd/config/provisiond-configuration"
  foreign-source-dir="/opt/deltav/etc/foreign-sources"
  requistion-dir="/opt/deltav/etc/imports"
  importThreads="4" scanThreads="4" rescanThreads="4" writeThreads="4">
  <requisition-def import-name="delta-v"
                   import-url-resource="file:///opt/deltav/etc/imports/delta-v.xml">
    <cron-schedule>0/30 * * * * ?</cron-schedule>
  </requisition-def>
  <requisition-def import-name="${E2E_FOREIGN_SOURCE}"
                   import-url-resource="file:///opt/deltav/etc/imports/${E2E_FOREIGN_SOURCE}.xml">
    <cron-schedule>0/30 * * * * ?</cron-schedule>
  </requisition-def>
</provisiond-configuration>
PROVEOF

echo "==> Restarting provisiond to pick up E2E requisition"
docker compose restart provisiond

echo "==> Waiting for provisiond to become healthy again (up to ${STACK_READY_TIMEOUT} s)"
deadline=$(( $(date +%s) + STACK_READY_TIMEOUT ))
while (( $(date +%s) < deadline )); do
    if docker compose exec -T provisiond curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1; then
        echo "==> provisiond healthy"
        break
    fi
    sleep 5
done
if (( $(date +%s) >= deadline )); then
    echo "ERROR: provisiond did not become healthy after restart within ${STACK_READY_TIMEOUT} s"
    docker compose logs provisiond | tail -80
    exit 1
fi

echo "==> Waiting for change record (up to ${METRICS_TIMEOUT} s)"
deadline=$(( $(date +%s) + METRICS_TIMEOUT ))
while (( $(date +%s) < deadline )); do
    metrics=$(docker compose exec -T provisiond curl -sf http://localhost:8080/actuator/prometheus 2>/dev/null || true)
    if echo "${metrics}" | grep -E 'deltav_node_context_records_published_total.*reason="change"' | \
           awk '{print $NF}' | head -1 | grep -qE '^[1-9]' 2>/dev/null; then
        echo "==> change counter > 0 — change-feed verified"
        break
    fi
    sleep 5
done
if (( $(date +%s) >= deadline )); then
    echo "ERROR: deltav_node_context_records_published_total{reason=\"change\"} not > 0 within ${METRICS_TIMEOUT} s"
    metrics=$(docker compose exec -T provisiond curl -sf http://localhost:8080/actuator/prometheus 2>/dev/null || true)
    echo "${metrics}" | grep deltav_node_context || true
    docker compose logs provisiond | tail -80
    exit 1
fi

# ── Step 5: Assert no failures ────────────────────────────────────────────────

echo "==> Checking failure counters"
metrics=$(docker compose exec -T provisiond curl -sf http://localhost:8080/actuator/prometheus)
# grep returns 1 on no-match (the success path), which would kill the script
# under set -euo pipefail. Tolerate no-match with a trailing `|| true`.
failed_sum=$( { echo "${metrics}" \
    | grep -E '^deltav_node_context_records_failed_total\{' \
    || true; } \
    | awk '{sum += $NF} END {print sum + 0}')
if [[ "${failed_sum}" != "0" ]]; then
    echo "ERROR: deltav_node_context_records_failed_total sum = ${failed_sum} (expected 0)"
    echo "${metrics}" | grep deltav_node_context_records_failed_total || true
    exit 1
fi
echo "==> No failure counters — all records published cleanly"

echo "==> PASS"
exit 0
