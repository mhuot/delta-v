#!/usr/bin/env bash
set -euo pipefail

NETFLOW_COLLECTOR="${NETFLOW_COLLECTOR:-minion-default-01:4729}"
NETFLOW_VERSION="${NETFLOW_VERSION:-9}"
CAPTURE_INTERFACE="${CAPTURE_INTERFACE:-eth0}"
TRAFFIC_TARGETS="${TRAFFIC_TARGETS:-kafka postgres minion-default-01}"
TRAFFIC_INTERVAL="${TRAFFIC_INTERVAL:-5}"

echo "[flow-exporter] Starting SNMP agent (snmpd)..."
snmpd -f -Lo -C -c /etc/snmp/snmpd.conf &
SNMPD_PID=$!

echo "[flow-exporter] Waiting for interface ${CAPTURE_INTERFACE}..."
while ! ip link show "$CAPTURE_INTERFACE" >/dev/null 2>&1; do
    sleep 1
done

echo "[flow-exporter] Starting softflowd (NetFlow v${NETFLOW_VERSION} -> ${NETFLOW_COLLECTOR})..."
softflowd -v "$NETFLOW_VERSION" \
    -n "$NETFLOW_COLLECTOR" \
    -i "$CAPTURE_INTERFACE" \
    -d -t maxlife=30 &
SOFTFLOWD_PID=$!

echo "[flow-exporter] Starting traffic generator (targets: ${TRAFFIC_TARGETS})..."
while true; do
    for target in $TRAFFIC_TARGETS; do
        ping -c 1 -W 1 "$target" >/dev/null 2>&1 || true
        curl -sf --max-time 2 "http://${target}:8080/" >/dev/null 2>&1 || true
    done
    sleep "$TRAFFIC_INTERVAL"
done
