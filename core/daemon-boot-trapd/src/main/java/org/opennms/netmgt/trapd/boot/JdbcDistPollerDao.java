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
package org.opennms.netmgt.trapd.boot;

import java.util.List;

import javax.sql.DataSource;

import org.opennms.core.criteria.Criteria;
import org.opennms.netmgt.dao.api.DistPollerDao;
import org.opennms.netmgt.model.OnmsDistPoller;
import org.opennms.netmgt.model.OnmsMonitoringSystem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
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

    // Remaining DAO methods — not needed by Trapd, throw UnsupportedOperationException
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
