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

import org.deltav.flows.enricher.parser.ThreadLocalDispatcher;
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
 * Tests {@link Netflow5MessageProcessor}. Under Phase 2 the processor runs
 * a two-stage bridge: Stage 1 feeds raw bytes to a horizon {@link
 * org.opennms.netmgt.telemetry.listeners.UdpParser}, which emits
 * {@code FlowMessage} protobuf bytes; Stage 2 passes those bytes to
 * horizon's {@code Netflow5Adapter}. Tests use {@link FakeUdpParser} to
 * script the parser's output without pulling in the real Netflow v5 wire
 * parser.
 */
class Netflow5MessageProcessorTest {

    private ThreadLocalDispatcher tld;
    private FakeUdpParser fakeParser;
    private Netflow5MessageProcessor processor;

    @BeforeEach
    void setUp() {
        tld = new ThreadLocalDispatcher();
        fakeParser = new FakeUdpParser(tld);
        AdapterDefinition adapterDefinition = TestAdapterDefinitions.testAdapterDefinition("netflow5-test");
        MetricRegistry metricRegistry = new MetricRegistry();
        processor = new Netflow5MessageProcessor(fakeParser, adapterDefinition, metricRegistry, tld);
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
    void processYieldsFlowsWhenParserEmitsFlowMessageBytes() {
        FlowMessage flowMessage = buildNetflow5FlowMessage();
        fakeParser.emitNext(flowMessage.toByteArray());

        TelemetryProtos.TelemetryMessageLog raw = rawLogWithOneEntry(new byte[]{1, 2, 3, 4});
        List<Flow> flows = processor.process(raw);

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

    private static TelemetryProtos.TelemetryMessageLog rawLogWithOneEntry(byte[] bytes) {
        return TelemetryProtos.TelemetryMessageLog.newBuilder()
                .setLocation("test-location")
                .setSystemId("test-system")
                .setSourceAddress("192.0.2.254")
                .setSourcePort(2055)
                .addMessage(TelemetryProtos.TelemetryMessage.newBuilder()
                        .setBytes(ByteString.copyFrom(bytes))
                        .setTimestamp(System.currentTimeMillis()))
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
