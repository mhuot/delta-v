/*
 * Licensed to The OpenNMS Group, Inc (TOG) under one or more
 * contributor license agreements.  See the LICENSE.md file
 * distributed with this work for additional information
 * regarding copyright ownership.
 *
 * TOG licenses this file to You under the GNU Affero General
 * Public License Version 3 (the "License") or (at your option)
 * any later version.  You may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at:
 *
 *      https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */
package org.opennms.core.daemon.common;

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
