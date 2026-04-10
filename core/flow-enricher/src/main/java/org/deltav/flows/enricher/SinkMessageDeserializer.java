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

import org.opennms.core.ipc.sink.model.SinkMessage;
import org.opennms.netmgt.telemetry.common.ipc.TelemetryProtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unwraps the two-layer protobuf encoding used on Kafka Sink topics:
 * Kafka bytes → {@link SinkMessage} envelope → {@link TelemetryProtos.TelemetryMessageLog}.
 *
 * <p>The Sink IPC framework (OpenNMS Minion) supports message chunking for
 * payloads that exceed the Kafka message size limit. Flow telemetry messages
 * are typically small (single UDP packet payload) so this deserializer takes
 * the simpler path of dropping any chunked message it encounters and logging
 * a warning. Reassembly across chunks can be added later if it proves
 * necessary in production.
 */
public class SinkMessageDeserializer {

    private static final Logger LOG = LoggerFactory.getLogger(SinkMessageDeserializer.class);

    /**
     * Deserializes a Kafka payload into a {@link TelemetryProtos.TelemetryMessageLog}.
     * Returns {@code null} if the payload is null, malformed, or chunked. Callers
     * should treat null as "drop and continue".
     */
    public TelemetryProtos.TelemetryMessageLog deserialize(byte[] kafkaBytes) {
        if (kafkaBytes == null) {
            return null;
        }
        try {
            SinkMessage sinkMessage = SinkMessage.parseFrom(kafkaBytes);
            if (sinkMessage.getTotalChunks() > 1) {
                LOG.warn("Chunked SinkMessage not supported (messageId={}, totalChunks={}); dropping",
                        sinkMessage.getMessageId(), sinkMessage.getTotalChunks());
                return null;
            }
            return TelemetryProtos.TelemetryMessageLog.parseFrom(sinkMessage.getContent());
        } catch (Exception e) {
            LOG.warn("Failed to deserialize SinkMessage payload ({} bytes): {}",
                    kafkaBytes.length, e.getMessage());
            return null;
        }
    }
}
