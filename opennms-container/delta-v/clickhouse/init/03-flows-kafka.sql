CREATE TABLE IF NOT EXISTS deltav.flows_kafka
(
    -- Timing and identity
    timestamp               UInt64,
    netflow_version         String,
    direction               String,
    sampling_algorithm      String,
    sampling_interval       Nullable(Float64),
    clock_correction        UInt64,

    -- Volume and flow lifetime
    num_bytes               Nullable(UInt64),
    num_packets             Nullable(UInt64),
    num_flow_records        Nullable(UInt32),
    first_switched          Nullable(UInt64),
    last_switched           Nullable(UInt64),
    delta_switched          Nullable(UInt64),
    flow_seq_num            Nullable(UInt64),

    -- Source L3/L4
    src_address             String,
    src_hostname            String,
    src_port                Nullable(UInt32),
    src_as                  Nullable(UInt64),
    src_mask_len            Nullable(UInt32),

    -- Destination L3/L4
    dst_address             String,
    dst_hostname            String,
    dst_port                Nullable(UInt32),
    dst_as                  Nullable(UInt64),
    dst_mask_len            Nullable(UInt32),

    -- Next-hop
    next_hop_address        String,
    next_hop_hostname       String,

    -- Protocol / QoS / TCP
    protocol                Nullable(UInt32),
    ip_protocol_version     Nullable(UInt32),
    tcp_flags               Nullable(UInt32),
    tos                     Nullable(UInt32),
    dscp                    Nullable(UInt32),
    ecn                     Nullable(UInt32),
    vlan                    String,

    -- Locality
    src_locality            String,
    dst_locality            String,
    flow_locality           String,

    -- Classification
    application             String,

    -- Exporter display metadata
    host                    String,
    location                String,
    engine_id               Nullable(UInt32),
    engine_type             Nullable(UInt32),

    -- Exporter/src/dest NodeInfo (as Tuple, name-matched to proto)
    src_node                Tuple(
        node_id             UInt32,
        foreign_source      String,
        foreign_id          String,
        categories          Array(String)
    ),
    exporter_node           Tuple(
        node_id             UInt32,
        foreign_source      String,
        foreign_id          String,
        categories          Array(String)
    ),
    dest_node               Tuple(
        node_id             UInt32,
        foreign_source      String,
        foreign_id          String,
        categories          Array(String)
    ),

    -- SNMP ifindex lives at the top level of the proto, not in NodeInfo
    input_snmp_ifindex      Nullable(UInt32),
    output_snmp_ifindex     Nullable(UInt32)
)
ENGINE = Kafka
SETTINGS
    kafka_broker_list          = 'kafka:9092',
    kafka_topic_list           = 'deltav-flows',
    kafka_group_name           = 'deltav-clickhouse-persister',
    kafka_format               = 'Protobuf',
    kafka_schema               = 'deltav-flows.proto:FlowDocument',
    kafka_num_consumers        = 2,
    kafka_max_block_size       = 65536,
    kafka_skip_broken_messages = 100;
