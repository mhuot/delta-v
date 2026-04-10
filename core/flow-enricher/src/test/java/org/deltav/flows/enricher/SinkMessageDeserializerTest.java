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

@SuppressWarnings("deprecation") // intentionally exercises the deprecated single-arg overload
class SinkMessageDeserializerTest {

    private final SinkMessageDeserializer deserializer = new SinkMessageDeserializer();

    @Test
    void deserializesSingleChunkMessage() {
        byte[] bytes = buildSinkMessageBytes("test-1", samplePayloadBytes(), 0, 1);

        DeserializedSinkMessage result = deserializer.deserialize(bytes);

        assertThat(result).isNotNull();
        assertThat(result.messageLog()).isNotNull();
        assertThat(result.messageLog().getLocation()).isEqualTo("Default");
        assertThat(result.messageLog().getSystemId()).isEqualTo("minion-01");
        assertThat(result.messageLog().getSourceAddress()).isEqualTo("192.168.1.1");
        assertThat(result.messageLog().getSourcePort()).isEqualTo(4729);
    }

    @Test
    void deserializedMessageModuleIdIsNullForSingleArgOverload() {
        // The Sink envelope does not carry a moduleId field (see DeserializedSinkMessage
        // javadoc); the single-arg deserializer therefore always reports null. Wiring
        // topic-derived moduleId through the function signature is a later commit.
        byte[] bytes = buildSinkMessageBytes("test-moduleid", samplePayloadBytes(), 0, 1);

        DeserializedSinkMessage result = deserializer.deserialize(bytes);

        assertThat(result).isNotNull();
        assertThat(result.moduleId()).isNull();
    }

    @Test
    void deserializedMessageExposesModuleIdFromTwoArgOverload() {
        byte[] bytes = buildSinkMessageBytes("test-moduleid-2arg", samplePayloadBytes(), 0, 1);

        DeserializedSinkMessage result = deserializer.deserialize("Telemetry-Netflow-9", bytes);

        assertThat(result).isNotNull();
        assertThat(result.moduleId()).isEqualTo("Telemetry-Netflow-9");
        assertThat(result.messageLog()).isNotNull();
        assertThat(result.messageLog().getLocation()).isEqualTo("Default");
    }

    @Test
    void chunkedMessageIsDroppedAndReturnsNull() {
        byte[] bytes = buildSinkMessageBytes("test-chunked", samplePayloadBytes(), 0, 3);

        assertThat(deserializer.deserialize(bytes)).isNull();
    }

    @Test
    void chunkedMessageIsDroppedAndReturnsNullForTwoArgOverload() {
        byte[] bytes = buildSinkMessageBytes("test-chunked-2arg", samplePayloadBytes(), 0, 3);

        assertThat(deserializer.deserialize("Telemetry-Netflow-5", bytes)).isNull();
    }

    @Test
    void garbageBytesReturnNull() {
        assertThat(deserializer.deserialize(new byte[]{0x01, 0x02, 0x03, 0x04})).isNull();
    }

    @Test
    void nullBytesReturnNull() {
        assertThat(deserializer.deserialize(null)).isNull();
    }

    @Test
    void nullBytesReturnNullForTwoArgOverload() {
        assertThat(deserializer.deserialize("Telemetry-Netflow-5", null)).isNull();
    }

    private static byte[] samplePayloadBytes() {
        return TelemetryProtos.TelemetryMessageLog.newBuilder()
                .setLocation("Default")
                .setSystemId("minion-01")
                .setSourceAddress("192.168.1.1")
                .setSourcePort(4729)
                .build()
                .toByteArray();
    }

    private static byte[] buildSinkMessageBytes(String messageId, byte[] payload, int currentChunk, int totalChunks) {
        return SinkMessage.newBuilder()
                .setMessageId(messageId)
                .setContent(ByteString.copyFrom(payload))
                .setCurrentChunkNumber(currentChunk)
                .setTotalChunks(totalChunks)
                .build()
                .toByteArray();
    }
}
