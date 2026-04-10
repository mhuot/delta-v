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
package org.deltav.flows.enricher;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;

import org.junit.jupiter.api.Test;
import org.opennms.core.ipc.sink.model.SinkMessage;
import org.opennms.netmgt.telemetry.common.ipc.TelemetryProtos;

class SinkMessageDeserializerTest {

    private final SinkMessageDeserializer deserializer = new SinkMessageDeserializer();

    @Test
    void deserializesSingleChunkMessage() {
        TelemetryProtos.TelemetryMessageLog messageLog = TelemetryProtos.TelemetryMessageLog.newBuilder()
                .setLocation("Default")
                .setSystemId("minion-01")
                .setSourceAddress("192.168.1.1")
                .setSourcePort(4729)
                .build();

        SinkMessage sinkMessage = SinkMessage.newBuilder()
                .setMessageId("test-1")
                .setContent(ByteString.copyFrom(messageLog.toByteArray()))
                .setCurrentChunkNumber(0)
                .setTotalChunks(1)
                .build();

        TelemetryProtos.TelemetryMessageLog result = deserializer.deserialize(sinkMessage.toByteArray());

        assertThat(result).isNotNull();
        assertThat(result.getLocation()).isEqualTo("Default");
        assertThat(result.getSystemId()).isEqualTo("minion-01");
        assertThat(result.getSourceAddress()).isEqualTo("192.168.1.1");
        assertThat(result.getSourcePort()).isEqualTo(4729);
    }

    @Test
    void chunkedMessageIsDroppedAndReturnsNull() {
        TelemetryProtos.TelemetryMessageLog messageLog = TelemetryProtos.TelemetryMessageLog.newBuilder()
                .setLocation("Default")
                .setSystemId("minion-01")
                .setSourceAddress("192.168.1.1")
                .setSourcePort(4729)
                .build();

        SinkMessage sinkMessage = SinkMessage.newBuilder()
                .setMessageId("test-chunked")
                .setContent(ByteString.copyFrom(messageLog.toByteArray()))
                .setCurrentChunkNumber(0)
                .setTotalChunks(3)
                .build();

        assertThat(deserializer.deserialize(sinkMessage.toByteArray())).isNull();
    }

    @Test
    void garbageBytesReturnNull() {
        assertThat(deserializer.deserialize(new byte[]{0x01, 0x02, 0x03, 0x04})).isNull();
    }

    @Test
    void nullBytesReturnNull() {
        assertThat(deserializer.deserialize(null)).isNull();
    }
}
