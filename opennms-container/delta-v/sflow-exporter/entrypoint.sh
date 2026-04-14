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
# hsflowd applies a link-speed-tiered default for the pcap sampling rate:
# sampling.10G=10000, sampling.1G=1000, sampling.100M=100. The generic
# `sampling = N` line is ignored when a per-speed match exists. Docker veth
# interfaces report themselves as 10G via ETHTOOL_GLINKSETTINGS, so without
# explicit per-speed overrides mod_pcap silently samples 1-in-10000 — and
# this low-traffic test bed never hits the threshold, producing only counter
# samples and zero flow samples. We override every realistic speed tier so
# the test exporter samples every packet regardless of what Docker reports.
cat > /etc/hsflowd.conf <<EOF
sflow {
  DNSSD = off
  polling = ${POLLING_INTERVAL}
  sampling = ${SAMPLING_RATE}
  sampling.10G = ${SAMPLING_RATE}
  sampling.1G = ${SAMPLING_RATE}
  sampling.100M = ${SAMPLING_RATE}
  sampling.10M = ${SAMPLING_RATE}
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
# -P keeps hsflowd running as root inside the container so its mod_pcap
# can open the eth0 capture socket. Without -P, hsflowd drops privileges
# to a non-root user (CapEff=0), libpcap fails to open eth0 in capture
# mode silently, and only counter samples (which don't need pcap) are
# emitted on the polling timer — no flow samples ever reach the collector.
# This is a test exporter, so running as root is fine; production hsflowd
# deployments would instead grant CAP_NET_RAW + CAP_NET_ADMIN to the
# dropped-privilege user.
hsflowd -d -P -f /etc/hsflowd.conf &
HSFLOWD_PID=$!

echo "[sflow-exporter] Starting traffic generator (targets: ${TRAFFIC_TARGETS})..."
while true; do
    for target in $TRAFFIC_TARGETS; do
        ping -c 1 -W 1 "$target" >/dev/null 2>&1 || true
        curl -sf --max-time 2 "http://${target}:8080/" >/dev/null 2>&1 || true
    done
    if ! kill -0 "$HSFLOWD_PID" 2>/dev/null; then
        echo "[sflow-exporter] hsflowd exited; restarting..."
        hsflowd -d -P -f /etc/hsflowd.conf &
        HSFLOWD_PID=$!
    fi
    sleep "$TRAFFIC_INTERVAL"
done
