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
 * Tests {@link Netflow9MessageProcessor}. Netflow v9 data records share
 * horizon's normalized {@link FlowMessage} protobuf with Netflow v5 and
 * IPFIX; the only difference at this layer is the
 * {@code netflow_version} field. Template/data distinction happens in the
 * parser layer (on the Minion) before the {@code FlowMessage} reaches the
 * adapter.
 */
class Netflow9MessageProcessorTest {

    private Netflow9MessageProcessor processor;

    @BeforeEach
    void setUp() {
        AdapterDefinition adapterDefinition = TestAdapterDefinitions.testAdapterDefinition("netflow9-test");
        MetricRegistry metricRegistry = new MetricRegistry();
        processor = new Netflow9MessageProcessor(adapterDefinition, metricRegistry);
    }

    @Test
    void processReturnsEmptyListForNullMessageLog() {
        assertThat(processor.process(null)).isEmpty();
    }

    @Test
    void processParsesNetflow9FlowMessage() {
        FlowMessage flowMessage = FlowMessage.newBuilder()
                .setTimestamp(System.currentTimeMillis())
                .setNetflowVersion(org.opennms.netmgt.telemetry.protocols.netflow.transport.NetflowVersion.V9)
                .setSrcAddress("10.0.0.1")
                .setDstAddress("10.0.0.2")
                .setNextHopAddress("10.0.0.254")
                .setSrcPort(UInt32Value.of(12345))
                .setDstPort(UInt32Value.of(80))
                .setProtocol(UInt32Value.of(17)) // UDP
                .setNumBytes(UInt64Value.of(2048L))
                .setNumPackets(UInt64Value.of(16L))
                .setFirstSwitched(UInt64Value.of(1_700_000_000_000L))
                .setLastSwitched(UInt64Value.of(1_700_000_001_000L))
                .setDeltaSwitched(UInt64Value.of(1_700_000_000_000L))
                .setInputSnmpIfindex(UInt32Value.of(3))
                .setOutputSnmpIfindex(UInt32Value.of(4))
                .setIpProtocolVersion(UInt32Value.of(4))
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
        assertThat(flow.getSrcAddr()).isEqualTo("10.0.0.1");
        assertThat(flow.getDstAddr()).isEqualTo("10.0.0.2");
        assertThat(flow.getProtocol()).isEqualTo(17);
        assertThat(flow.getDstPort()).isEqualTo(80);
        assertThat(flow.getNetflowVersion()).isEqualTo(NetflowVersion.V9);
    }
}
