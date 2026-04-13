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

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.opennms.core.ipc.sink.api.AsyncDispatcher;
import org.opennms.netmgt.telemetry.api.receiver.TelemetryMessage;

/**
 * A singleton {@link AsyncDispatcher} that delegates every {@code send()}
 * call to a per-thread {@link CapturingDispatcher} installed by the
 * enricher's processor code immediately before it calls
 * {@code UdpParser.parse()} and cleared in the corresponding {@code finally}.
 *
 * <p>Horizon {@code UdpParser} instances take their {@code AsyncDispatcher}
 * as a final constructor field — the dispatcher cannot be swapped per call
 * on a singleton parser. But the parser's per-exporter template cache must
 * persist across calls, so fresh parsers per call are unusable. This class
 * resolves the conflict: the parser is a long-lived singleton bean wired to
 * this {@code ThreadLocalDispatcher}; each processor call installs a private
 * {@link CapturingDispatcher} onto the current thread, lets the parser write
 * into it, reads the captured messages after {@code parse()} returns, and
 * clears the thread-local in {@code finally}.
 *
 * <p><strong>Critical invariant:</strong> every {@link #install} call must be
 * paired with a {@link #clear} call in a {@code finally} block that runs
 * regardless of parser outcome. A leaked thread-local silently cross-contaminates
 * the next parse call on the same thread — a correctness bug that will not
 * surface in tests or staging without explicit coverage (see
 * {@code Netflow9MessageProcessorTest.sequentialCallsDoNotCrossContaminate}).
 */
public class ThreadLocalDispatcher implements AsyncDispatcher<TelemetryMessage> {

    private final ThreadLocal<CapturingDispatcher> delegate = new ThreadLocal<>();

    /**
     * Install the given {@link CapturingDispatcher} as the per-thread delegate.
     *
     * <p><strong>Throws {@link IllegalStateException} if a dispatcher is already
     * installed on the current thread.</strong> The whole point of this class is
     * to enforce the install/clear pairing contract, so silently overwriting a
     * previously-installed dispatcher would hide the most dangerous failure mode
     * (a missing {@code clear()} in a {@code finally} block leaks captures from
     * one parse call into the next). We'd rather crash immediately with a pointer
     * at the offending thread than silently cross-contaminate flows.
     */
    public void install(CapturingDispatcher dispatcher) {
        Objects.requireNonNull(dispatcher, "dispatcher");
        CapturingDispatcher existing = delegate.get();
        if (existing != null) {
            throw new IllegalStateException(
                    "CapturingDispatcher already installed on thread "
                            + Thread.currentThread().getName()
                            + "; a previous parse call leaked its ThreadLocal — missing clear() in a finally block?");
        }
        delegate.set(dispatcher);
    }

    /**
     * Remove the per-thread delegate. Must be called in a {@code finally} block
     * paired with {@link #install}.
     */
    public void clear() {
        delegate.remove();
    }

    /**
     * Returns the currently-installed {@link CapturingDispatcher} on this
     * thread, or {@code null} if nothing is installed.
     */
    public CapturingDispatcher current() {
        return delegate.get();
    }

    @Override
    public CompletableFuture<DispatchStatus> send(TelemetryMessage message) {
        CapturingDispatcher d = delegate.get();
        if (d == null) {
            throw new IllegalStateException(
                    "No CapturingDispatcher installed on thread "
                            + Thread.currentThread().getName()
                            + "; parser dispatched outside a processor call?");
        }
        return d.send(message);
    }

    @Override
    public int getQueueSize() {
        CapturingDispatcher d = delegate.get();
        return d == null ? 0 : d.getQueueSize();
    }

    @Override
    public void close() {
        // Thread-local dispatchers are cleared per-call; no global cleanup.
    }
}
