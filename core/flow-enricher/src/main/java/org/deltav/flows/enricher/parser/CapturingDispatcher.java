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
package org.deltav.flows.enricher.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.opennms.core.ipc.sink.api.AsyncDispatcher;
import org.opennms.netmgt.telemetry.api.receiver.TelemetryMessage;

/**
 * An {@link AsyncDispatcher} that accumulates dispatched {@link TelemetryMessage}
 * objects into a local list instead of forwarding them anywhere. Used as the
 * capture sink for horizon {@link org.opennms.netmgt.telemetry.listeners.UdpParser}
 * instances during the flow-enricher's Stage 1 parse step.
 *
 * <p><strong>This class is NOT thread-safe.</strong> A fresh instance must be
 * created per {@code process()} call and installed into the singleton
 * {@link ThreadLocalDispatcher}. Sharing one instance across concurrent
 * threads or across sequential calls on the same thread will mix flows from
 * different exporters, which is a correctness bug the Stage 1 error-handling
 * contract explicitly forbids.
 */
public class CapturingDispatcher implements AsyncDispatcher<TelemetryMessage> {

    private final List<TelemetryMessage> captured = new ArrayList<>();

    @Override
    public CompletableFuture<DispatchStatus> send(TelemetryMessage message) {
        captured.add(message);
        return CompletableFuture.completedFuture(DispatchStatus.DISPATCHED);
    }

    @Override
    public int getQueueSize() {
        return captured.size();
    }

    @Override
    public void close() {
        // No resources to release; captures remain accessible.
    }

    /**
     * Returns an unmodifiable view of the messages dispatched to this
     * instance since it was created. Order matches dispatch order.
     */
    public List<TelemetryMessage> getCaptured() {
        return Collections.unmodifiableList(captured);
    }
}
