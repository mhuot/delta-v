/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.deltav.flows.enricher.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opennms.integration.api.v1.flows.Flow.NetflowVersion;
import org.opennms.netmgt.flows.api.Flow;
import org.opennms.netmgt.telemetry.common.ipc.TelemetryProtos;
import org.opennms.netmgt.telemetry.config.api.AdapterDefinition;
import org.opennms.netmgt.telemetry.protocols.netflow.transport.FlowMessage;

import com.codahale.metrics.MetricRegistry;
import com.google.protobuf.ByteString;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;

/**
 * Tests {@link IpfixMessageProcessor}. IPFIX (Netflow v10) data records
 * share horizon's normalized {@link FlowMessage} protobuf with Netflow v5
 * and Netflow v9; the only difference at this layer is the
 * {@code netflow_version} field. Template/data distinction happens in the
 * parser layer (on the Minion) before the {@code FlowMessage} reaches the
 * adapter.
 */
class IpfixMessageProcessorTest {

    private IpfixMessageProcessor processor;

    @BeforeEach
    void setUp() {
        AdapterDefinition adapterDefinition = TestAdapterDefinitions.testAdapterDefinition("ipfix-test");
        MetricRegistry metricRegistry = new MetricRegistry();
        processor = new IpfixMessageProcessor(adapterDefinition, metricRegistry);
    }

    @Test
    void processReturnsEmptyListForNullMessageLog() {
        assertThat(processor.process(null)).isEmpty();
    }

    @Test
    void processParsesIpfixFlowMessage() {
        FlowMessage flowMessage = FlowMessage.newBuilder()
                .setTimestamp(System.currentTimeMillis())
                .setNetflowVersion(org.opennms.netmgt.telemetry.protocols.netflow.transport.NetflowVersion.IPFIX)
                .setSrcAddress("2001:db8::1")
                .setDstAddress("2001:db8::2")
                .setNextHopAddress("2001:db8::ffff")
                .setSrcPort(UInt32Value.of(40000))
                .setDstPort(UInt32Value.of(22))
                .setProtocol(UInt32Value.of(6)) // TCP
                .setTcpFlags(UInt32Value.of(0x02)) // SYN
                .setNumBytes(UInt64Value.of(4096L))
                .setNumPackets(UInt64Value.of(32L))
                .setFirstSwitched(UInt64Value.of(1_700_000_000_000L))
                .setLastSwitched(UInt64Value.of(1_700_000_002_000L))
                .setDeltaSwitched(UInt64Value.of(1_700_000_000_000L))
                .setInputSnmpIfindex(UInt32Value.of(5))
                .setOutputSnmpIfindex(UInt32Value.of(6))
                .setIpProtocolVersion(UInt32Value.of(6))
                .build();

        TelemetryProtos.TelemetryMessageLog log = TelemetryProtos.TelemetryMessageLog.newBuilder()
                .setLocation("test-location")
                .setSystemId("test-system")
                .setSourceAddress("10.0.0.100")
                .addMessage(TelemetryProtos.TelemetryMessage.newBuilder()
                        .setBytes(ByteString.copyFrom(flowMessage.toByteArray()))
                        .setTimestamp(System.currentTimeMillis())
                        .build())
                .build();

        List<Flow> flows = processor.process(log);

        assertThat(flows).hasSize(1);
        Flow flow = flows.get(0);
        assertThat(flow.getSrcAddr()).isEqualTo("2001:db8::1");
        assertThat(flow.getDstAddr()).isEqualTo("2001:db8::2");
        assertThat(flow.getProtocol()).isEqualTo(6);
        assertThat(flow.getDstPort()).isEqualTo(22);
        assertThat(flow.getIpProtocolVersion()).isEqualTo(6);
        assertThat(flow.getNetflowVersion()).isEqualTo(NetflowVersion.IPFIX);
    }
}
