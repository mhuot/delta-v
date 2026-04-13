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
import org.opennms.netmgt.flows.api.Flow;
import org.opennms.netmgt.telemetry.common.ipc.TelemetryProtos;
import org.opennms.netmgt.telemetry.config.api.AdapterDefinition;
import org.opennms.netmgt.telemetry.protocols.netflow.transport.FlowMessage;
import org.opennms.netmgt.telemetry.protocols.netflow.transport.NetflowVersion;

import com.codahale.metrics.MetricRegistry;
import com.google.protobuf.ByteString;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;

class Netflow9MessageProcessorTest {

    private ThreadLocalDispatcher tld;
    private FakeUdpParser fakeParser;
    private Netflow9MessageProcessor processor;

    @BeforeEach
    void setUp() {
        tld = new ThreadLocalDispatcher();
        fakeParser = new FakeUdpParser(tld);
        AdapterDefinition adapterDefinition = TestAdapterDefinitions.testAdapterDefinition("netflow9-test");
        MetricRegistry metricRegistry = new MetricRegistry();
        processor = new Netflow9MessageProcessor(fakeParser, adapterDefinition, metricRegistry, tld);
    }

    @Test
    void processReturnsEmptyListForNullMessageLog() {
        assertThat(processor.process(null)).isEmpty();
    }

    @Test
    void processReturnsEmptyListForEmptyMessageLog() {
        TelemetryProtos.TelemetryMessageLog empty = TelemetryProtos.TelemetryMessageLog.newBuilder()
                .setLocation("Default")
                .setSystemId("test-system")
                .build();
        assertThat(processor.process(empty)).isEmpty();
    }

    @Test
    void processYieldsFlowsWhenParserEmitsFlowMessageBytes() {
        byte[] flowMessageBytes = buildFlowMessage("10.0.0.1", "10.0.0.2").toByteArray();
        fakeParser.emitNext(flowMessageBytes);

        TelemetryProtos.TelemetryMessageLog raw = rawLogWithOneEntry(new byte[]{1, 2, 3, 4});
        List<Flow> flows = processor.process(raw);

        assertThat(flows).hasSize(1);
        assertThat(flows.get(0).getSrcAddr()).isEqualTo("10.0.0.1");
        assertThat(flows.get(0).getDstAddr()).isEqualTo("10.0.0.2");
        assertThat(fakeParser.getParseCallCount()).isEqualTo(1);
    }

    @Test
    void parserExceptionDoesNotLeakThreadLocal() {
        fakeParser.throwOnNextParse(new IllegalStateException("boom from parser"));

        TelemetryProtos.TelemetryMessageLog raw = rawLogWithOneEntry(new byte[]{1, 2, 3, 4});
        List<Flow> flows = processor.process(raw);

        // The per-entry catch should swallow the exception at DEBUG and
        // produce zero flows, NOT rethrow.
        assertThat(flows).isEmpty();
        // The critical invariant: thread-local is cleared even after exception.
        assertThat(tld.current()).isNull();
    }

    @Test
    void sequentialCallsDoNotCrossContaminate() {
        byte[] flowA = buildFlowMessage("10.0.0.11", "10.0.0.12").toByteArray();
        byte[] flowB = buildFlowMessage("10.0.0.21", "10.0.0.22").toByteArray();
        fakeParser.emitNext(flowA).emitNext(flowB);

        List<Flow> resultA = processor.process(rawLogWithOneEntry(new byte[]{1}));
        List<Flow> resultB = processor.process(rawLogWithOneEntry(new byte[]{2}));

        assertThat(resultA).hasSize(1);
        assertThat(resultA.get(0).getSrcAddr()).isEqualTo("10.0.0.11");

        assertThat(resultB).hasSize(1);
        assertThat(resultB.get(0).getSrcAddr()).isEqualTo("10.0.0.21");

        // A's flow must not appear in B's result
        assertThat(resultB).noneMatch(f -> "10.0.0.11".equals(f.getSrcAddr()));
        // And vice versa
        assertThat(resultA).noneMatch(f -> "10.0.0.21".equals(f.getSrcAddr()));
    }

    private static TelemetryProtos.TelemetryMessageLog rawLogWithOneEntry(byte[] bytes) {
        return TelemetryProtos.TelemetryMessageLog.newBuilder()
                .setLocation("Default")
                .setSystemId("test-system")
                .setSourceAddress("192.0.2.1")
                .setSourcePort(54321)
                .addMessage(TelemetryProtos.TelemetryMessage.newBuilder()
                        .setTimestamp(System.currentTimeMillis())
                        .setBytes(ByteString.copyFrom(bytes)))
                .build();
    }

    private static FlowMessage buildFlowMessage(String srcAddr, String dstAddr) {
        return FlowMessage.newBuilder()
                .setTimestamp(System.currentTimeMillis())
                .setNetflowVersion(NetflowVersion.V9)
                .setSrcAddress(srcAddr)
                .setDstAddress(dstAddr)
                .setNextHopAddress("10.0.0.254")
                .setSrcPort(UInt32Value.of(12345))
                .setDstPort(UInt32Value.of(80))
                .setProtocol(UInt32Value.of(17))
                .setNumBytes(UInt64Value.of(2048L))
                .setNumPackets(UInt64Value.of(16L))
                .setFirstSwitched(UInt64Value.of(1_700_000_000_000L))
                .setLastSwitched(UInt64Value.of(1_700_000_001_000L))
                .setDeltaSwitched(UInt64Value.of(1_700_000_000_000L))
                .setInputSnmpIfindex(UInt32Value.of(3))
                .setOutputSnmpIfindex(UInt32Value.of(4))
                .setIpProtocolVersion(UInt32Value.of(4))
                .build();
    }
}
