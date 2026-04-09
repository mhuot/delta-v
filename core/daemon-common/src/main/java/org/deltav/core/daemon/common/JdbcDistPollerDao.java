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

import java.util.List;

import javax.sql.DataSource;

import org.opennms.core.criteria.Criteria;
import org.opennms.netmgt.dao.api.DistPollerDao;
import org.opennms.netmgt.model.OnmsDistPoller;
import org.opennms.netmgt.model.OnmsMonitoringSystem;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcDistPollerDao implements DistPollerDao {

    private final JdbcTemplate jdbc;
    private volatile OnmsDistPoller localPoller;

    public JdbcDistPollerDao(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public OnmsDistPoller whoami() {
        if (localPoller == null) {
            synchronized (this) {
                if (localPoller == null) {
                    localPoller = jdbc.queryForObject(
                        "SELECT id, label, location FROM monitoringsystems WHERE type = 'OpenNMS' LIMIT 1",
                        (rs, rowNum) -> {
                            var dp = new OnmsDistPoller();
                            dp.setId(rs.getString("id"));
                            dp.setLabel(rs.getString("label"));
                            dp.setLocation(rs.getString("location"));
                            dp.setType(OnmsMonitoringSystem.TYPE_OPENNMS);
                            return dp;
                        });
                }
            }
        }
        return localPoller;
    }

    // Remaining DAO methods — not needed by Spring Boot daemons, throw UnsupportedOperationException
    @Override public OnmsDistPoller get(String id) { throw new UnsupportedOperationException(); }
    @Override public OnmsDistPoller load(String id) { throw new UnsupportedOperationException(); }
    @Override public String save(OnmsDistPoller entity) { throw new UnsupportedOperationException(); }
    @Override public void saveOrUpdate(OnmsDistPoller entity) { throw new UnsupportedOperationException(); }
    @Override public void update(OnmsDistPoller entity) { throw new UnsupportedOperationException(); }
    @Override public void delete(OnmsDistPoller entity) { throw new UnsupportedOperationException(); }
    @Override public void delete(String key) { throw new UnsupportedOperationException(); }
    @Override public List<OnmsDistPoller> findAll() { throw new UnsupportedOperationException(); }
    @Override public List<OnmsDistPoller> findMatching(Criteria criteria) { throw new UnsupportedOperationException(); }
    @Override public int countAll() { throw new UnsupportedOperationException(); }
    @Override public int countMatching(Criteria criteria) { throw new UnsupportedOperationException(); }
    @Override public void flush() { }
    @Override public void clear() { }
    @Override public void initialize(Object obj) { }
    @Override public void lock() { }
}
