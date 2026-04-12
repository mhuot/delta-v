CREATE MATERIALIZED VIEW IF NOT EXISTS deltav.flows_ingest
TO deltav.flows_raw AS
SELECT
    toDateTime64(timestamp / 1000.0, 3, 'UTC')      AS timestamp,
    netflow_version,
    direction,
    sampling_algorithm,
    sampling_interval,
    clock_correction,

    num_bytes,
    num_packets,
    num_flow_records,
    first_switched,
    last_switched,
    delta_switched,
    flow_seq_num,

    toIPv6(src_address)                             AS src_address,
    src_hostname,
    CAST(src_port AS Nullable(UInt16))              AS src_port,
    src_as,
    CAST(src_mask_len AS Nullable(UInt8))           AS src_mask_len,

    toIPv6(dst_address)                             AS dst_address,
    dst_hostname,
    CAST(dst_port AS Nullable(UInt16))              AS dst_port,
    dst_as,
    CAST(dst_mask_len AS Nullable(UInt8))           AS dst_mask_len,

    toIPv6OrNull(next_hop_address)                  AS next_hop_address,
    next_hop_hostname,

    CAST(protocol AS Nullable(UInt8))               AS protocol,
    CAST(ip_protocol_version AS Nullable(UInt8))    AS ip_protocol_version,
    tcp_flags,
    CAST(tos AS Nullable(UInt8))                    AS tos,
    CAST(dscp AS Nullable(UInt8))                   AS dscp,
    CAST(ecn AS Nullable(UInt8))                    AS ecn,
    vlan,

    src_locality,
    dst_locality,
    flow_locality,

    application,
    host,
    location,
    engine_id,
    engine_type,

    -- Exporter node: tuple flatten + ifNull for sort-key compatibility
    ifNull(exporter_node.node_id, 0)                AS exporter_node_id,
    exporter_node.foreign_source                    AS exporter_node_foreign_source,
    exporter_node.foreign_id                        AS exporter_node_foreign_id,
    exporter_node.categories                        AS exporter_node_categories,
    ifNull(input_snmp_ifindex, 0)                   AS input_snmp_ifindex,
    output_snmp_ifindex,

    -- Source node
    ifNull(src_node.node_id, 0)                     AS src_node_id,
    src_node.foreign_source                         AS src_node_foreign_source,
    src_node.foreign_id                             AS src_node_foreign_id,
    src_node.categories                             AS src_node_categories,

    -- Destination node
    ifNull(dest_node.node_id, 0)                    AS dest_node_id,
    dest_node.foreign_source                        AS dest_node_foreign_source,
    dest_node.foreign_id                            AS dest_node_foreign_id,
    dest_node.categories                            AS dest_node_categories
FROM deltav.flows_kafka;
