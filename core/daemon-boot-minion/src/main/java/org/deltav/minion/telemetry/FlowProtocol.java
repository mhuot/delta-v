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

/**
 * Flow protocols the Minion listens for. Each protocol is identified by a
 * version field in the first few bytes of the UDP datagram and maps to a
 * Kafka Sink topic suffix consumed by the flow-enricher.
 *
 * <p>Netflow v5, Netflow v9, and IPFIX share a 2-byte version field at
 * offset 0. sFlow uses a 4-byte version field at offset 0 with value 5.
 * Since an sFlow packet's first two bytes are {@code 0x0000} (high bytes
 * of the 4-byte field), there is no collision with Netflow v5's
 * {@code 0x0005}.
 */
public enum FlowProtocol {

    NETFLOW_5("Telemetry-Netflow-5"),
    NETFLOW_9("Telemetry-Netflow-9"),
    IPFIX("Telemetry-IPFIX"),
    SFLOW("Telemetry-SFlow");

    private final String sinkModuleId;

    FlowProtocol(String sinkModuleId) {
        this.sinkModuleId = sinkModuleId;
    }

    public String getSinkModuleId() {
        return sinkModuleId;
    }

    /**
     * Returns the detected protocol or {@code null} if the datagram is too
     * small or the version header does not match a supported protocol.
     */
    public static FlowProtocol detect(byte[] datagram) {
        if (datagram == null || datagram.length < 4) {
            return null;
        }
        int version16 = ((datagram[0] & 0xFF) << 8) | (datagram[1] & 0xFF);
        switch (version16) {
            case 0x0005:
                return NETFLOW_5;
            case 0x0009:
                return NETFLOW_9;
            case 0x000A:
                return IPFIX;
            default:
                int version32 = ((datagram[0] & 0xFF) << 24)
                              | ((datagram[1] & 0xFF) << 16)
                              | ((datagram[2] & 0xFF) << 8)
                              | (datagram[3] & 0xFF);
                if (version32 == 5) {
                    return SFLOW;
                }
                return null;
        }
    }
}
