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
 * Tests {@link Netflow5MessageProcessor} by constructing a
 * {@link FlowMessage} protobuf directly (the parser layer's normalized
 * representation), wrapping its serialized bytes in a
 * {@link TelemetryProtos.TelemetryMessageLog}, and asserting that the
 * processor returns the expected parsed flow.
 *
 * <p>The horizon adapter's job at this layer is to take a
 * {@code FlowMessage} protobuf (already parsed from Netflow v5 wire format
 * on the Minion) and wrap it in a {@code NetflowMessage} implementation of
 * the {@link Flow} interface. Constructing the {@code FlowMessage} directly
 * avoids pulling in horizon's Netflow parser module as a test dependency.
 */
class Netflow5MessageProcessorTest {

    private Netflow5MessageProcessor processor;

    @BeforeEach
    void setUp() {
        AdapterDefinition adapterDefinition = TestAdapterDefinitions.testAdapterDefinition("netflow5-test");
        MetricRegistry metricRegistry = new MetricRegistry();
        processor = new Netflow5MessageProcessor(adapterDefinition, metricRegistry);
    }

    @Test
    void processReturnsEmptyListForNullMessageLog() {
        assertThat(processor.process(null)).isEmpty();
    }

    @Test
    void processReturnsEmptyListForEmptyMessageLog() {
        TelemetryProtos.TelemetryMessageLog emptyLog = TelemetryProtos.TelemetryMessageLog.newBuilder()
                .setLocation("test-location")
                .setSystemId("test-system")
                .setSourceAddress("192.0.2.254")
                .build();
        assertThat(processor.process(emptyLog)).isEmpty();
    }

    @Test
    void processParsesSingleFlowMessageProtobuf() {
        FlowMessage flowMessage = buildNetflow5FlowMessage();
        TelemetryProtos.TelemetryMessageLog log = wrapInMessageLog(flowMessage);

        List<Flow> flows = processor.process(log);

        assertThat(flows).hasSize(1);
        Flow flow = flows.get(0);
        assertThat(flow.getSrcAddr()).isEqualTo("192.0.2.1");
        assertThat(flow.getDstAddr()).isEqualTo("198.51.100.2");
        assertThat(flow.getDstPort()).isEqualTo(443);
        assertThat(flow.getSrcPort()).isEqualTo(54321);
        assertThat(flow.getProtocol()).isEqualTo(6);
        assertThat(flow.getBytes()).isEqualTo(1500L);
        assertThat(flow.getPackets()).isEqualTo(10L);
        assertThat(flow.getTcpFlags()).isEqualTo(0x18);
        assertThat(flow.getNetflowVersion()).isEqualTo(NetflowVersion.V5);
    }

    @Test
    void processParsesMultipleEntriesInMessageLog() {
        FlowMessage flowMessage = buildNetflow5FlowMessage();
        byte[] serialized = flowMessage.toByteArray();
        TelemetryProtos.TelemetryMessageLog log = TelemetryProtos.TelemetryMessageLog.newBuilder()
                .setLocation("test-location")
                .setSystemId("test-system")
                .setSourceAddress("192.0.2.254")
                .setSourcePort(2055)
                .addMessage(TelemetryProtos.TelemetryMessage.newBuilder()
                        .setBytes(ByteString.copyFrom(serialized))
                        .setTimestamp(System.currentTimeMillis())
                        .build())
                .addMessage(TelemetryProtos.TelemetryMessage.newBuilder()
                        .setBytes(ByteString.copyFrom(serialized))
                        .setTimestamp(System.currentTimeMillis())
                        .build())
                .build();

        List<Flow> flows = processor.process(log);

        // NetflowAdapter emits one Flow per FlowMessage protobuf entry
        assertThat(flows).hasSize(2);
    }

    @Test
    void processReturnsEmptyListWhenEntryBytesAreInvalid() {
        TelemetryProtos.TelemetryMessageLog log = TelemetryProtos.TelemetryMessageLog.newBuilder()
                .setLocation("test-location")
                .setSystemId("test-system")
                .setSourceAddress("192.0.2.254")
                .addMessage(TelemetryProtos.TelemetryMessage.newBuilder()
                        .setBytes(ByteString.copyFromUtf8("not a valid FlowMessage protobuf"))
                        .setTimestamp(System.currentTimeMillis())
                        .build())
                .build();

        // NetflowAdapter.parse() catches InvalidProtocolBufferException and
        // returns null, so the adapter publishes an empty flow list to the
        // pipeline. The processor returns that empty list without throwing.
        List<Flow> flows = processor.process(log);

        assertThat(flows).isEmpty();
    }

    private static TelemetryProtos.TelemetryMessageLog wrapInMessageLog(FlowMessage flowMessage) {
        return TelemetryProtos.TelemetryMessageLog.newBuilder()
                .setLocation("test-location")
                .setSystemId("test-system")
                .setSourceAddress("192.0.2.254")
                .setSourcePort(2055)
                .addMessage(TelemetryProtos.TelemetryMessage.newBuilder()
                        .setBytes(ByteString.copyFrom(flowMessage.toByteArray()))
                        .setTimestamp(System.currentTimeMillis())
                        .build())
                .build();
    }

    private static FlowMessage buildNetflow5FlowMessage() {
        // Represents a Netflow v5 record already parsed from wire format into
        // horizon's normalized FlowMessage protobuf.
        //   src=192.0.2.1:54321, dst=198.51.100.2:443, proto=6 (TCP),
        //   packets=10, bytes=1500, tcpFlags=0x18 (PSH+ACK).
        return FlowMessage.newBuilder()
                .setTimestamp(System.currentTimeMillis())
                .setNetflowVersion(org.opennms.netmgt.telemetry.protocols.netflow.transport.NetflowVersion.V5)
                .setSrcAddress("192.0.2.1")
                .setDstAddress("198.51.100.2")
                .setNextHopAddress("203.0.113.1")
                .setSrcPort(UInt32Value.of(54321))
                .setDstPort(UInt32Value.of(443))
                .setProtocol(UInt32Value.of(6))
                .setTcpFlags(UInt32Value.of(0x18))
                .setNumBytes(UInt64Value.of(1500L))
                .setNumPackets(UInt64Value.of(10L))
                .setFirstSwitched(UInt64Value.of(1_700_000_000_000L))
                .setLastSwitched(UInt64Value.of(1_700_000_001_000L))
                .setDeltaSwitched(UInt64Value.of(1_700_000_000_000L))
                .setInputSnmpIfindex(UInt32Value.of(1))
                .setOutputSnmpIfindex(UInt32Value.of(2))
                .setIpProtocolVersion(UInt32Value.of(4))
                .build();
    }
}
