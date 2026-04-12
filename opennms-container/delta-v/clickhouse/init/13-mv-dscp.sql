CREATE TABLE IF NOT EXISTS deltav.flows_by_dscp_1m
(
    t_minute           DateTime('UTC'),
    exporter_node_id   UInt32,
    input_snmp_ifindex UInt32,
    dscp               UInt8,
    bytes_in           UInt64,
    bytes_out          UInt64,
    packets_in         UInt64,
    packets_out        UInt64,
    flow_count         UInt64
)
ENGINE = SummingMergeTree
PARTITION BY toYYYYMM(t_minute)
ORDER BY (exporter_node_id, t_minute, input_snmp_ifindex, dscp)
TTL t_minute + INTERVAL ${DELTAV_CLICKHOUSE_FLOWS_AGG_TTL_DAYS} DAY DELETE;

CREATE MATERIALIZED VIEW IF NOT EXISTS deltav.flows_by_dscp_1m_mv
TO deltav.flows_by_dscp_1m AS
SELECT
    toStartOfMinute(timestamp)                      AS t_minute,
    exporter_node_id,
    input_snmp_ifindex,
    ifNull(dscp, 255)                               AS dscp,
    sumIf(num_bytes,   direction = 'INGRESS')       AS bytes_in,
    sumIf(num_bytes,   direction = 'EGRESS')        AS bytes_out,
    sumIf(num_packets, direction = 'INGRESS')       AS packets_in,
    sumIf(num_packets, direction = 'EGRESS')        AS packets_out,
    count()                                         AS flow_count
FROM deltav.flows_raw
GROUP BY t_minute, exporter_node_id, input_snmp_ifindex, dscp;
