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

import java.util.Objects;

import org.deltav.minion.telemetry.proto.TelemetryProtos.TelemetryMessageLog;
import org.opennms.core.ipc.sink.api.Message;

/**
 * Thin wrapper that adapts the locally-generated protobuf
 * {@link TelemetryMessageLog} to the Sink API's {@link Message} marker
 * interface. Needed because {@link TelemetryMessageLog} extends
 * {@code com.google.protobuf.GeneratedMessageV3} and does not implement
 * the Sink API marker; horizon's hand-edited {@code TelemetryProtos.java}
 * adds the interface manually, but delta-v uses pure protoc output.
 *
 * <p>Immutable. The wrapped {@link TelemetryMessageLog} is itself
 * immutable (protobuf generated types are).
 */
public final class FlowTelemetryMessage implements Message {

    private final TelemetryMessageLog log;

    public FlowTelemetryMessage(TelemetryMessageLog log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    public TelemetryMessageLog getTelemetryMessageLog() {
        return log;
    }

    public byte[] toByteArray() {
        return log.toByteArray();
    }
}
