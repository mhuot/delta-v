#!/usr/bin/env bash
set -euo pipefail

SFLOW_COLLECTOR="${SFLOW_COLLECTOR:-minion:4729}"
CAPTURE_INTERFACE="${CAPTURE_INTERFACE:-eth0}"
SAMPLING_RATE="${SAMPLING_RATE:-100}"
POLLING_INTERVAL="${POLLING_INTERVAL:-20}"
TRAFFIC_TARGETS="${TRAFFIC_TARGETS:-kafka postgres minion-default-01}"
TRAFFIC_INTERVAL="${TRAFFIC_INTERVAL:-5}"

SFLOW_COLLECTOR_HOST="${SFLOW_COLLECTOR%%:*}"
SFLOW_COLLECTOR_PORT="${SFLOW_COLLECTOR##*:}"
if [[ "$SFLOW_COLLECTOR_HOST" == "$SFLOW_COLLECTOR_PORT" ]]; then
    SFLOW_COLLECTOR_PORT="6343"
fi

echo "[sflow-exporter] Writing /etc/hsflowd.conf..."
cat > /etc/hsflowd.conf <<EOF
sflow {
  DNSSD = off
  polling = ${POLLING_INTERVAL}
  sampling = ${SAMPLING_RATE}
  agent = ${CAPTURE_INTERFACE}
  collector {
    ip = ${SFLOW_COLLECTOR_HOST}
    udpport = ${SFLOW_COLLECTOR_PORT}
  }
  pcap { dev = ${CAPTURE_INTERFACE} }
}
EOF
cat /etc/hsflowd.conf

echo "[sflow-exporter] Waiting for interface ${CAPTURE_INTERFACE}..."
while ! ip link show "$CAPTURE_INTERFACE" >/dev/null 2>&1; do
    sleep 1
done

echo "[sflow-exporter] Starting hsflowd (sFlow -> ${SFLOW_COLLECTOR_HOST}:${SFLOW_COLLECTOR_PORT})..."
hsflowd -d -f /etc/hsflowd.conf &
HSFLOWD_PID=$!

echo "[sflow-exporter] Starting traffic generator (targets: ${TRAFFIC_TARGETS})..."
while true; do
    for target in $TRAFFIC_TARGETS; do
        ping -c 1 -W 1 "$target" >/dev/null 2>&1 || true
        curl -sf --max-time 2 "http://${target}:8080/" >/dev/null 2>&1 || true
    done
    if ! kill -0 "$HSFLOWD_PID" 2>/dev/null; then
        echo "[sflow-exporter] hsflowd exited; restarting..."
        hsflowd -d -f /etc/hsflowd.conf &
        HSFLOWD_PID=$!
    fi
    sleep "$TRAFFIC_INTERVAL"
done
