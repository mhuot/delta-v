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
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;

import com.google.protobuf.ByteString;

import org.deltav.flows.enricher.enrichment.FlowLocalityCalculator;
import org.deltav.flows.enricher.enrichment.InterfaceMarkingCache;
import org.deltav.flows.enricher.enrichment.JdbcNodeInfoLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opennms.core.ipc.sink.model.SinkMessage;
import org.opennms.netmgt.telemetry.common.ipc.TelemetryProtos;

class FlowEnrichmentFunctionTest {

    private FlowEnrichmentFunction function;

    @BeforeEach
    void setUp() {
        SinkMessageDeserializer deserializer = new SinkMessageDeserializer();
        JdbcNodeInfoLookup nodeInfoLookup = mock(JdbcNodeInfoLookup.class);
        FlowLocalityCalculator localityCalculator = mock(FlowLocalityCalculator.class);
        InterfaceMarkingCache interfaceMarkingCache = mock(InterfaceMarkingCache.class);

        function = new FlowEnrichmentFunction(
                deserializer,
                nodeInfoLookup,
                localityCalculator,
                interfaceMarkingCache,
                Map.of()); // Commit 5 populates the dispatch map with real processors
    }

    @Test
    void returnsEmptyListForNullMessage() {
        List<byte[]> result = function.processMessage(null);
        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyListForEmptyMessage() {
        List<byte[]> result = function.processMessage(new byte[0]);
        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyListForGarbageBytes() {
        List<byte[]> result = function.processMessage(new byte[]{0x01, 0x02, 0x03, 0x04});
        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyListForEmptyTelemetryMessageLog() {
        // Valid SinkMessage envelope wrapping an empty TelemetryMessageLog (no
        // flow records): function must drop the message without producing output.
        byte[] payload = TelemetryProtos.TelemetryMessageLog.newBuilder()
                .setLocation("Default")
                .setSystemId("minion-01")
                .setSourceAddress("192.168.1.1")
                .setSourcePort(4729)
                .build()
                .toByteArray();
        byte[] kafkaBytes = buildSinkMessageBytes("test-empty", payload);

        List<byte[]> result = function.processMessage(kafkaBytes);

        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyListForUnknownModuleId() {
        // Commit 3 scope: the dispatch map is empty, so every parseable message
        // falls through to the "no processor for moduleId" path. A real flow
        // record-bearing message still returns an empty list here. Commit 5
        // (Task 12) replaces this placeholder with per-flow enrichment output.
        byte[] payload = TelemetryProtos.TelemetryMessageLog.newBuilder()
                .setLocation("Default")
                .setSystemId("minion-01")
                .setSourceAddress("192.168.1.1")
                .setSourcePort(4729)
                .addMessage(TelemetryProtos.TelemetryMessage.newBuilder()
                        .setTimestamp(System.currentTimeMillis())
                        .setBytes(ByteString.copyFrom(new byte[]{0x00, 0x01, 0x02}))
                        .build())
                .build()
                .toByteArray();
        byte[] kafkaBytes = buildSinkMessageBytes("test-unknown-module", payload);

        List<byte[]> result = function.processMessage(kafkaBytes);

        assertThat(result).isEmpty();
    }

    private static byte[] buildSinkMessageBytes(String messageId, byte[] payload) {
        return SinkMessage.newBuilder()
                .setMessageId(messageId)
                .setContent(ByteString.copyFrom(payload))
                .setCurrentChunkNumber(0)
                .setTotalChunks(1)
                .build()
                .toByteArray();
    }
}
