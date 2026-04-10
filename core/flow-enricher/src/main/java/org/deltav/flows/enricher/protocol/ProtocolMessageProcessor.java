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

import java.util.List;

import org.opennms.netmgt.flows.api.Flow;
import org.opennms.netmgt.telemetry.common.ipc.TelemetryProtos;

/**
 * Per-protocol processor that wraps one horizon {@code AbstractFlowAdapter}
 * and exposes a uniform {@link #process(TelemetryProtos.TelemetryMessageLog)}
 * method returning the parsed flows. One implementation exists per protocol:
 * Netflow5, Netflow9, IPFIX, sFlow.
 *
 * <p>The telemetry protobuf type
 * {@code org.opennms.netmgt.telemetry.common.ipc.TelemetryProtos.TelemetryMessageLog}
 * already implements horizon's
 * {@code org.opennms.netmgt.telemetry.api.adapter.TelemetryMessageLog}
 * interface, so the protobuf can be passed directly into horizon adapters
 * without an intermediate conversion step. Each entry in the message log is
 * expected to carry a serialized {@code FlowMessage} protobuf (for the
 * Netflow variants) or a serialized BSON document (for sFlow); these
 * representations are the output of horizon's parser layer, which runs on
 * the Minion before the raw wire bytes are shipped over Kafka Sink.
 */
public interface ProtocolMessageProcessor {

    /**
     * Parses the telemetry message log into per-flow records via the
     * underlying horizon adapter.
     *
     * @param messageLog the decoded Sink payload
     * @return parsed flows (never {@code null}; may be empty if the message
     *         log contained no records or parsing failed)
     */
    List<Flow> process(TelemetryProtos.TelemetryMessageLog messageLog);
}
