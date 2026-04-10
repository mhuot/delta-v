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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class InterfaceMarkingCacheTest {

    private static final String EXPECTED_SQL =
            "UPDATE snmpinterface SET hasflows = true WHERE nodeid = ? AND snmpifindex = ?";

    @Test
    void firstMarkingExecutesDbUpdate() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        InterfaceMarkingCache cache = new InterfaceMarkingCache(jdbc, Duration.ofHours(24));

        cache.markIfNeeded(5L, 12);

        verify(jdbc).update(eq(EXPECTED_SQL), eq(5L), eq(12));
    }

    @Test
    void secondMarkingWithinTtlSkipsDbUpdate() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        InterfaceMarkingCache cache = new InterfaceMarkingCache(jdbc, Duration.ofHours(24));

        cache.markIfNeeded(5L, 12);
        cache.markIfNeeded(5L, 12);

        verify(jdbc, times(1)).update(anyString(), anyLong(), anyInt());
    }

    @Test
    void differentInterfacesAreTrackedSeparately() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        InterfaceMarkingCache cache = new InterfaceMarkingCache(jdbc, Duration.ofHours(24));

        cache.markIfNeeded(5L, 12);
        cache.markIfNeeded(5L, 13);

        verify(jdbc, times(2)).update(anyString(), anyLong(), anyInt());
    }

    @Test
    void differentNodesAreTrackedSeparately() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        InterfaceMarkingCache cache = new InterfaceMarkingCache(jdbc, Duration.ofHours(24));

        cache.markIfNeeded(5L, 12);
        cache.markIfNeeded(6L, 12);

        verify(jdbc, times(2)).update(anyString(), anyLong(), anyInt());
    }

    @Test
    void evictNodeClearsAllInterfacesForThatNode() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        InterfaceMarkingCache cache = new InterfaceMarkingCache(jdbc, Duration.ofHours(24));

        cache.markIfNeeded(5L, 12);
        cache.markIfNeeded(5L, 13);
        cache.evictNode(5L);
        cache.markIfNeeded(5L, 12);

        // 3 calls total: initial 12, initial 13, re-mark 12 after eviction
        verify(jdbc, times(3)).update(anyString(), anyLong(), anyInt());
    }

    @Test
    void evictNodeDoesNotAffectOtherNodes() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        InterfaceMarkingCache cache = new InterfaceMarkingCache(jdbc, Duration.ofHours(24));

        cache.markIfNeeded(5L, 12);
        cache.markIfNeeded(6L, 12);
        cache.evictNode(5L);
        cache.markIfNeeded(6L, 12); // still cached, no DB call

        verify(jdbc, times(2)).update(anyString(), anyLong(), anyInt());
    }
}
