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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.opennms.core.ipc.sink.api.AsyncDispatcher;
import org.opennms.netmgt.telemetry.api.receiver.TelemetryMessage;

/**
 * An {@link AsyncDispatcher} that accumulates dispatched {@link TelemetryMessage}
 * objects into a local list instead of forwarding them anywhere. Used as the
 * capture sink for horizon {@link org.opennms.netmgt.telemetry.listeners.UdpParser}
 * instances during the flow-enricher's Stage 1 parse step.
 *
 * <p><strong>Thread-safety:</strong> {@link #send} is safe to call concurrently
 * from multiple threads. Horizon's {@code Netflow9UdpParser} (and the rest of
 * its {@code ParserBase} family) dispatches flow records on an internal
 * {@code ThreadPoolExecutor}, so a single batch parse can spread its
 * {@code dispatcher.send(...)} calls across many worker threads. The earlier
 * implementation used a plain {@link ArrayList} without synchronisation, which
 * is fine in single-threaded sFlow tests but raced under concurrent Netflow9
 * dispatches: {@code ArrayList.add} occasionally threw
 * {@code ArrayIndexOutOfBoundsException} on capacity expansion and even more
 * often produced a {@code null} entry that bombed downstream iteration in
 * {@code AbstractProtocolMessageProcessor.synthesizeParsedLog} with an NPE on
 * {@code msg.getBuffer()}. This implementation guards both {@link #send} and
 * {@link #getCaptured} with synchronisation on the captured list.
 *
 * <p>Each call to {@link #getCaptured} returns an immutable defensive copy
 * snapshot rather than a wrapper, so callers can iterate without any
 * additional locking even if a parser thread is still dispatching after the
 * snapshot is taken (which should not happen in production because the
 * processor calls {@code parser.parse(...).join()} before reading captures,
 * but defensive snapshots keep the contract simple).
 *
 * <p>One {@link CapturingDispatcher} instance is still created per
 * {@code process()} call and installed into the singleton
 * {@link ThreadLocalDispatcher}. Sharing one instance across sequential calls
 * would mix flows from different exporters, which is a correctness bug the
 * Stage 1 error-handling contract explicitly forbids.
 */
public class CapturingDispatcher implements AsyncDispatcher<TelemetryMessage> {

    private final List<TelemetryMessage> captured = new ArrayList<>();

    @Override
    public CompletableFuture<DispatchStatus> send(TelemetryMessage message) {
        Objects.requireNonNull(message, "message");
        synchronized (captured) {
            captured.add(message);
        }
        return CompletableFuture.completedFuture(DispatchStatus.DISPATCHED);
    }

    @Override
    public int getQueueSize() {
        synchronized (captured) {
            return captured.size();
        }
    }

    @Override
    public void close() {
        // No resources to release; captures remain accessible.
    }

    /**
     * Returns an immutable snapshot of the messages dispatched to this
     * instance since it was created. The returned list is a defensive copy:
     * subsequent {@link #send} calls do not mutate it, and callers can
     * iterate it without external locking.
     */
    public List<TelemetryMessage> getCaptured() {
        synchronized (captured) {
            return List.copyOf(captured);
        }
    }
}
