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
package org.deltav.core.daemon.common;

import java.net.InetAddress;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.sql.DataSource;

import org.opennms.netmgt.dao.api.AbstractInterfaceToNodeCache;
import org.opennms.netmgt.dao.api.InterfaceToNodeCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * JDBC-based InterfaceToNodeCache for Spring Boot daemon containers.
 *
 * <p>Loads (location, ipAddress) to Entry(nodeId, interfaceId) mappings from
 * the database and refreshes periodically.</p>
 */
public class JdbcInterfaceToNodeCache implements InterfaceToNodeCache {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcInterfaceToNodeCache.class);

    private static final String LOAD_SQL =
            "SELECT ip.nodeid, ip.ipaddr, ip.id AS interfaceid, n.location " +
            "FROM ipinterface ip " +
            "JOIN node n ON ip.nodeid = n.nodeid " +
            "WHERE n.nodetype != 'D' AND ip.ismanaged != 'D'";

    private final ConcurrentMap<LocationIpKey, Entry> cache = new ConcurrentHashMap<>();
    private JdbcTemplate jdbc;

    /** No-arg constructor for testing without a DataSource. */
    public JdbcInterfaceToNodeCache() {
    }

    public JdbcInterfaceToNodeCache(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** Populate cache entry directly (for testing). */
    void addEntry(String location, InetAddress addr, int nodeId, int interfaceId) {
        cache.put(new LocationIpKey(location, addr), new Entry(nodeId, interfaceId));
    }

    @Override
    public Optional<Entry> getFirst(String location, InetAddress addr) {
        if (location == null || addr == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache.get(new LocationIpKey(location, addr)));
    }

    @Override
    public boolean setNodeId(String location, InetAddress addr, int nodeId) {
        cache.put(new LocationIpKey(location, addr), new Entry(nodeId, 0));
        return true;
    }

    @Override
    public boolean removeNodeId(String location, InetAddress addr, int nodeId) {
        var key = new LocationIpKey(location, addr);
        Entry entry = cache.get(key);
        if (entry != null && entry.nodeId == nodeId) {
            cache.remove(key);
            return true;
        }
        return false;
    }

    @Override
    public void removeInterfacesForNode(int nodeId) {
        cache.entrySet().removeIf(e -> e.getValue().nodeId == nodeId);
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public int size() {
        return cache.size();
    }

    @Scheduled(fixedDelayString = "${opennms.daemon.interface-to-node-cache.refresh-interval-ms:300000}",
               initialDelayString = "0")
    public void refresh() {
        if (jdbc == null) return;
        long start = System.currentTimeMillis();
        var newCache = new ConcurrentHashMap<LocationIpKey, Entry>();

        jdbc.query(LOAD_SQL, rs -> {
            try {
                int nodeId = rs.getInt("nodeid");
                int interfaceId = rs.getInt("interfaceid");
                String ipAddr = rs.getString("ipaddr");
                String location = rs.getString("location");
                if (ipAddr != null && location != null) {
                    InetAddress addr = InetAddress.getByName(ipAddr);
                    newCache.putIfAbsent(new LocationIpKey(location, addr),
                            new Entry(nodeId, interfaceId));
                }
            } catch (Exception e) {
                LOG.warn("Error parsing interface-to-node entry: {}", e.getMessage());
            }
        });

        cache.clear();
        cache.putAll(newCache);
        AbstractInterfaceToNodeCache.setInstance(this);
        LOG.info("InterfaceToNodeCache refreshed: {} entries in {} ms",
                cache.size(), System.currentTimeMillis() - start);
    }

    @Override
    public void dataSourceSync() {
        refresh();
    }

    private record LocationIpKey(String location, InetAddress addr) {
        LocationIpKey {
            Objects.requireNonNull(location);
            Objects.requireNonNull(addr);
        }
    }
}
