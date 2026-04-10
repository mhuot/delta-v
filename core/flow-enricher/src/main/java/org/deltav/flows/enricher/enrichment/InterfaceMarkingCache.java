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
package org.deltav.flows.enricher.enrichment;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Marks SNMP interfaces as flow-enabled in the database, deduplicated by a
 * TTL cache so that high-volume flow streams do not flood the database with
 * UPDATE statements.
 *
 * <p>Each (nodeId, ifIndex) pair is marked once per TTL window. The cache is
 * intentionally process-local — if the service restarts, the first flow on
 * each interface will re-mark, which is harmless.
 *
 * <p>{@link #evictNode(long)} should be called when a node is deleted (e.g.
 * via a {@code nodeDeleted} event subscription) so that the next flow with
 * a recycled nodeId triggers a fresh DB write rather than relying on stale
 * cache state.
 */
public class InterfaceMarkingCache {

    private static final Logger LOG = LoggerFactory.getLogger(InterfaceMarkingCache.class);

    static final String UPDATE_SQL =
            "UPDATE snmpinterface SET hasflows = true WHERE nodeid = ? AND snmpifindex = ?";

    private final JdbcTemplate jdbc;
    private final Duration ttl;
    private final Map<Long, Map<Integer, Instant>> cache = new ConcurrentHashMap<>();

    public InterfaceMarkingCache(JdbcTemplate jdbc, Duration ttl) {
        this.jdbc = jdbc;
        this.ttl = ttl;
    }

    public void markIfNeeded(long nodeId, int ifIndex) {
        Map<Integer, Instant> nodeCache = cache.computeIfAbsent(nodeId, k -> new ConcurrentHashMap<>());
        Instant lastMarked = nodeCache.get(ifIndex);
        Instant now = Instant.now();
        if (lastMarked != null && lastMarked.plus(ttl).isAfter(now)) {
            return;
        }
        try {
            jdbc.update(UPDATE_SQL, nodeId, ifIndex);
            nodeCache.put(ifIndex, now);
        } catch (Exception e) {
            LOG.debug("Failed to mark interface hasflows for node {} ifIndex {}: {}",
                    nodeId, ifIndex, e.getMessage());
        }
    }

    public void evictNode(long nodeId) {
        cache.remove(nodeId);
    }

    /**
     * Removes cache entries whose last-marked timestamp is older than the TTL.
     * Intended to be called periodically (e.g. hourly) to bound memory growth
     * for nodes whose interfaces have stopped emitting flows.
     */
    public void cleanExpired() {
        Instant cutoff = Instant.now().minus(ttl);
        cache.forEach((nodeId, interfaces) -> {
            interfaces.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
            if (interfaces.isEmpty()) {
                cache.remove(nodeId);
            }
        });
    }
}
