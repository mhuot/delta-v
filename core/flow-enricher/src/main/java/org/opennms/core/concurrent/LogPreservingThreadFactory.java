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
package org.opennms.core.concurrent;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Delta-V local shim for horizon's
 * {@code org.opennms.core.concurrent.LogPreservingThreadFactory}. The
 * horizon original lives in {@code org.opennms:opennms-util}, which is
 * on the project banlist (see {@code feedback_fix_horizon_not_exclusions.md})
 * — excluded from every delta-v production dependency because pulling it
 * in brings a deep chain of legacy Spring 4, Hibernate 3, and EclipseLink
 * transitive deps.
 *
 * <p>Horizon's flow parser base class ({@code ParserBase.start()}) constructs
 * a {@code LogPreservingThreadFactory} for its internal async dispatch thread
 * pool. Because we want to use horizon's parser code without bringing in
 * {@code opennms-util}, we shadow the class at the fully-qualified name — the
 * JVM class loader resolves imports by FQN, so as long as nothing else on the
 * classpath exports {@code org.opennms.core.concurrent.LogPreservingThreadFactory},
 * horizon's parsers find ours and use it.
 *
 * <h2>Semantics</h2>
 *
 * <p>Horizon's original implementation preserves SLF4J MDC context across
 * thread-hops (captures the MDC map in the constructor, restores it on
 * {@link Thread#run()}). Delta-V's flow-enricher does not currently populate
 * MDC on the caller thread — the enricher uses structured logging without
 * contextual MDC — so this shim is a plain {@code ThreadFactory} that creates
 * named threads with an incrementing counter. If the enricher ever starts
 * using MDC, this shim can be upgraded to mirror horizon's behavior; the
 * public API would not change.
 *
 * <h2>API compatibility</h2>
 *
 * <p>The public constructor signature {@code (String poolName, int poolSize)}
 * matches horizon's exactly. The {@link #newThread} override returns a
 * {@link Thread} named {@code {poolName}-Thread-N}. The {@code poolSize}
 * argument is ignored — it was used by horizon's original for BitSet-based
 * slot accounting in pooled-thread mode, which we do not replicate. Horizon
 * parsers pass {@code Integer.MAX_VALUE} for the pool size (see
 * {@code SFlowUdpParser.java:85}), meaning the BitSet path was never even
 * taken in horizon's own code for our use case.
 */
public class LogPreservingThreadFactory implements ThreadFactory {

    private final String poolName;
    private final AtomicInteger counter = new AtomicInteger(0);

    public LogPreservingThreadFactory(String poolName, int poolSize) {
        this.poolName = poolName;
        // poolSize is accepted for API compatibility and deliberately ignored.
    }

    @Override
    public Thread newThread(Runnable r) {
        String name = poolName + "-Thread-" + counter.incrementAndGet();
        Thread thread = new Thread(r, name);
        thread.setDaemon(true);
        return thread;
    }
}
