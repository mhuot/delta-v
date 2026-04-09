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
package org.deltav.core.daemon.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;

import org.junit.jupiter.api.Test;

class JdbcInterfaceToNodeCacheTest {

    @Test
    void emptyCache_returnsEmpty() throws Exception {
        var cache = new JdbcInterfaceToNodeCache();
        var result = cache.getFirst("Default", InetAddress.getByName("192.168.1.1"));
        assertThat(result).isEmpty();
    }

    @Test
    void populatedCache_findsEntry() throws Exception {
        var cache = new JdbcInterfaceToNodeCache();
        cache.addEntry("Default", InetAddress.getByName("192.168.1.1"), 42, 100);

        var result = cache.getFirst("Default", InetAddress.getByName("192.168.1.1"));
        assertThat(result).isPresent();
        assertThat(result.get().nodeId).isEqualTo(42);
        assertThat(result.get().interfaceId).isEqualTo(100);
    }

    @Test
    void differentLocation_doesNotMatch() throws Exception {
        var cache = new JdbcInterfaceToNodeCache();
        cache.addEntry("Default", InetAddress.getByName("192.168.1.1"), 42, 100);

        var result = cache.getFirst("Remote", InetAddress.getByName("192.168.1.1"));
        assertThat(result).isEmpty();
    }

    @Test
    void removeInterfacesForNode_removesAll() throws Exception {
        var cache = new JdbcInterfaceToNodeCache();
        cache.addEntry("Default", InetAddress.getByName("192.168.1.1"), 42, 100);
        cache.addEntry("Default", InetAddress.getByName("192.168.1.2"), 42, 101);
        cache.addEntry("Default", InetAddress.getByName("10.0.0.1"), 99, 200);

        cache.removeInterfacesForNode(42);

        assertThat(cache.size()).isEqualTo(1);
        assertThat(cache.getFirst("Default", InetAddress.getByName("10.0.0.1"))).isPresent();
    }
}
