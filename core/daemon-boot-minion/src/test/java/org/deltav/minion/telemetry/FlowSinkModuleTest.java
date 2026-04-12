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
package org.deltav.minion.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import org.deltav.minion.telemetry.proto.TelemetryProtos.TelemetryMessage;
import org.deltav.minion.telemetry.proto.TelemetryProtos.TelemetryMessageLog;
import org.junit.jupiter.api.Test;

class FlowSinkModuleTest {

    private static final int QUEUE_SIZE = 10_000;
    private static final int NUM_THREADS = 4;

    private TelemetryMessageLog sampleLog() {
        return TelemetryMessageLog.newBuilder()
                .setLocation("Default")
                .setSystemId("minion-default-01")
                .setSourceAddress("192.0.2.10")
                .setSourcePort(54321)
                .addMessage(TelemetryMessage.newBuilder()
                        .setTimestamp(1_700_000_000_000L)
                        .setBytes(ByteString.copyFrom(new byte[] { 0x00, 0x09, 0x01, 0x02 }))
                        .build())
                .build();
    }

    @Test
    void moduleIdMatchesProtocol() {
        FlowSinkModule module = new FlowSinkModule(FlowProtocol.NETFLOW_9, QUEUE_SIZE, NUM_THREADS);
        assertThat(module.getId()).isEqualTo("Telemetry-Netflow-9");
    }

    @Test
    void marshalUnmarshalRoundTripPreservesAllFields() {
        FlowSinkModule module = new FlowSinkModule(FlowProtocol.NETFLOW_9, QUEUE_SIZE, NUM_THREADS);
        FlowTelemetryMessage original = new FlowTelemetryMessage(sampleLog());

        byte[] bytes = module.marshal(original);
        FlowTelemetryMessage roundTripped = module.unmarshal(bytes);

        TelemetryMessageLog expected = original.getLog();
        TelemetryMessageLog actual = roundTripped.getLog();
        assertThat(actual.getLocation()).isEqualTo(expected.getLocation());
        assertThat(actual.getSystemId()).isEqualTo(expected.getSystemId());
        assertThat(actual.getSourceAddress()).isEqualTo(expected.getSourceAddress());
        assertThat(actual.getSourcePort()).isEqualTo(expected.getSourcePort());
        assertThat(actual.getMessageCount()).isEqualTo(1);
        assertThat(actual.getMessage(0).getTimestamp()).isEqualTo(1_700_000_000_000L);
        assertThat(actual.getMessage(0).getBytes()).isEqualTo(expected.getMessage(0).getBytes());
    }

    @Test
    void marshalSingleMessageEqualsMarshalWhenNoAggregation() {
        FlowSinkModule module = new FlowSinkModule(FlowProtocol.IPFIX, QUEUE_SIZE, NUM_THREADS);
        FlowTelemetryMessage msg = new FlowTelemetryMessage(sampleLog());

        assertThat(module.marshalSingleMessage(msg)).isEqualTo(module.marshal(msg));
    }

    @Test
    void aggregationPolicyIsNull() {
        FlowSinkModule module = new FlowSinkModule(FlowProtocol.SFLOW, QUEUE_SIZE, NUM_THREADS);
        assertThat(module.getAggregationPolicy()).isNull();
    }

    @Test
    void asyncPolicyReflectsConstructorArgs() {
        FlowSinkModule module = new FlowSinkModule(FlowProtocol.NETFLOW_5, 5_000, 2);
        assertThat(module.getAsyncPolicy().getQueueSize()).isEqualTo(5_000);
        assertThat(module.getAsyncPolicy().getNumThreads()).isEqualTo(2);
        assertThat(module.getAsyncPolicy().isBlockWhenFull()).isFalse();
    }
}
