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
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import org.opennms.core.ipc.sink.api.AsyncDispatcher;
import org.opennms.netmgt.telemetry.api.receiver.TelemetryMessage;

/**
 * A call-scoped {@link AsyncDispatcher} that delegates every {@code send()}
 * call to the {@link CapturingDispatcher} currently installed for the active
 * parse call. Horizon {@code UdpParser} instances hold their dispatcher as a
 * final constructor field — it cannot be swapped per call. However, the
 * parser's per-exporter template cache (in {@code UdpSessionManager}) must
 * persist across calls, so fresh parsers per call are not viable. This class
 * resolves the conflict: the parser is a singleton wired to one instance of
 * this dispatcher; each {@code process()} call installs a private
 * {@link CapturingDispatcher}, runs the parse, reads captured messages, then
 * clears the dispatcher.
 *
 * <h2>Threading model</h2>
 *
 * <p>Horizon's {@code ParserBase} serializes and dispatches flow records on an
 * internal thread pool (distinct from the calling thread). Consequently,
 * {@link #send} is called on a background thread — not the thread that called
 * {@link #install}. A {@code ThreadLocal}-based design therefore fails silently
 * (the background thread has an empty slot). This implementation uses a
 * {@link ReentrantLock} + {@link AtomicReference} instead:
 *
 * <ul>
 *   <li>{@link #install} acquires the lock and stores the dispatcher. It blocks
 *       if a previous call has not yet cleared — which guarantees serialized
 *       parse calls per parser instance and prevents cross-call contamination.</li>
 *   <li>{@link #send} reads the {@link AtomicReference} directly — visible to
 *       any thread, including the parser's background worker threads.</li>
 *   <li>{@link #clear} clears the {@link AtomicReference} and releases the lock.
 *       It MUST be called in a {@code finally} block paired with {@link #install}
 *       or the lock is never released and the next call blocks forever.</li>
 * </ul>
 *
 * <h2>Concurrency</h2>
 *
 * <p>Calls to {@code process()} on the same parser instance are serialized by the
 * lock. This is consistent with the production deployment where Spring Cloud Stream
 * Kafka bindings default to {@code concurrency=1} per topic — one consumer thread
 * drives one processor at a time. If higher concurrency is needed in the future,
 * each parser + processor pair should be given its own {@code ThreadLocalDispatcher}
 * instance (i.e., remove the shared singleton in {@code FlowEnricherConfiguration}).
 */
public class ThreadLocalDispatcher implements AsyncDispatcher<TelemetryMessage> {

    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicReference<CapturingDispatcher> current = new AtomicReference<>();

    /**
     * Install the given {@link CapturingDispatcher} as the delegate for the
     * current parse call. Acquires the per-instance lock; blocks if a previous
     * call has not yet cleared.
     *
     * <p><strong>Must be paired with a {@link #clear} call in a {@code finally}
     * block.</strong> A missing {@code clear()} leaves the lock permanently
     * acquired, blocking all future parse calls on this instance.
     *
     * @throws NullPointerException if {@code dispatcher} is {@code null}
     */
    public void install(CapturingDispatcher dispatcher) {
        Objects.requireNonNull(dispatcher, "dispatcher");
        lock.lock();
        current.set(dispatcher);
    }

    /**
     * Clear the installed dispatcher and release the lock. Must be called in a
     * {@code finally} block paired with {@link #install}.
     */
    public void clear() {
        current.set(null);
        lock.unlock();
    }

    /**
     * Returns the currently-installed {@link CapturingDispatcher}, or
     * {@code null} if nothing is installed.
     */
    public CapturingDispatcher current() {
        return current.get();
    }

    @Override
    public CompletableFuture<DispatchStatus> send(TelemetryMessage message) {
        CapturingDispatcher d = current.get();
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
        CapturingDispatcher d = current.get();
        return d == null ? 0 : d.getQueueSize();
    }

    @Override
    public void close() {
        // Call-scoped dispatchers are cleared per-call; no global cleanup.
    }
}
