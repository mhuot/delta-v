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
package org.deltav.netmgt.provision.nodecontext;

import io.micrometer.core.instrument.MeterRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-nodeId cancel-and-reschedule debouncer. Each {@link #enqueueUpdate(int)}
 * call atomically cancels any pending future for that nodeId and schedules a
 * new one {@code debounceMs} out that invokes {@code publisher.publishNode(nodeId)}.
 * Rapid bursts collapse to a single publish per debounce-quiet window per node.
 *
 * <p>Deletes and relocations do NOT go through the debouncer — call
 * {@link #evict(int)} to cancel any pending work for a node that's being
 * tombstoned or relocated.</p>
 */
public class NodeContextDebouncer {

    private static final Logger LOG = LoggerFactory.getLogger(NodeContextDebouncer.class);

    private final NodeContextPublisher publisher;
    private final long debounceMs;
    private final ScheduledExecutorService executor;
    private final ConcurrentHashMap<Integer, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();
    private final MeterRegistry meters;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public NodeContextDebouncer(NodeContextPublisher publisher,
                                 long debounceMs,
                                 int threads,
                                 MeterRegistry meters) {
        this.publisher = publisher;
        this.debounceMs = debounceMs;
        this.executor = Executors.newScheduledThreadPool(threads, r -> {
            Thread t = new Thread(r, "node-context-debounce");
            t.setDaemon(true);
            return t;
        });
        this.meters = meters;
        meters.gauge("deltav_node_context_debounce_pending_gauge", pending,
                ConcurrentHashMap::size);
    }

    public void enqueueUpdate(int nodeId) {
        if (closed.get()) {
            return;
        }
        pending.compute(nodeId, (id, existing) -> {
            if (existing != null) {
                existing.cancel(false);
                meters.counter("deltav_node_context_debounce_coalesced_total").increment();
            }
            try {
                return executor.schedule(() -> {
                    pending.remove(id);
                    try {
                        publisher.publishNode(id);
                    } catch (Throwable t) {
                        LOG.warn("Debounced publish failed for nodeId={}", id, t);
                    }
                }, debounceMs, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException rex) {
                LOG.debug("Debouncer executor rejected task for nodeId={} (shutting down)", id);
                meters.counter("deltav_node_context_records_failed_total",
                        "location", "", "reason", "debouncer_rejected").increment();
                return null;
            }
        });
    }

    public void evict(int nodeId) {
        ScheduledFuture<?> f = pending.remove(nodeId);
        if (f != null) {
            f.cancel(false);
        }
    }

    public void flushAndClose() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        List<Integer> keys = new ArrayList<>(pending.keySet());
        for (Integer id : keys) {
            ScheduledFuture<?> f = pending.remove(id);
            if (f != null) {
                f.cancel(false);
            }
            try {
                publisher.publishNode(id);
            } catch (Throwable t) {
                LOG.warn("Flush publish failed for nodeId={}", id, t);
            }
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
