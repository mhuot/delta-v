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
package org.deltav.netmgt.trapd.boot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.opennms.netmgt.config.api.EventConfDao;
import org.opennms.netmgt.model.EventConfEvent;
import org.opennms.netmgt.model.EventConfSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Loads event definitions from the {@code eventconf_events} table via JDBC
 * and initializes the in-memory {@link EventConfDao}.
 *
 * <p>Replaces the Karaf-era {@code EventConfInitializer} + Hibernate DAO.
 * The event definitions are stored as XML blobs in the {@code xml_content}
 * column; {@code DefaultEventConfDao.loadEventsFromDB()} parses them.</p>
 */
@Component
public class JdbcEventConfLoader {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcEventConfLoader.class);

    private static final String LOAD_SQL =
            "SELECT e.id, e.uei, e.event_label, e.description, e.severity, " +
            "e.xml_content, e.enabled, e.source_id, " +
            "s.name AS source_name, s.file_order AS source_file_order " +
            "FROM eventconf_events e " +
            "JOIN eventconf_sources s ON e.source_id = s.id " +
            "WHERE e.enabled = true " +
            "ORDER BY s.file_order ASC, e.id ASC";

    private final JdbcTemplate jdbc;
    private final EventConfDao eventConfDao;

    public JdbcEventConfLoader(DataSource dataSource, EventConfDao eventConfDao) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.eventConfDao = eventConfDao;
    }

    @Scheduled(fixedDelayString = "${opennms.trapd.eventconf-refresh-interval-ms:600000}",
               initialDelayString = "0")
    public void loadEventConf() {
        long start = System.currentTimeMillis();
        List<EventConfEvent> events = new ArrayList<>();
        Map<Long, EventConfSource> sourceCache = new HashMap<>();

        jdbc.query(LOAD_SQL, rs -> {
            long sourceId = rs.getLong("source_id");
            String sourceName = rs.getString("source_name");
            int sourceFileOrder = rs.getInt("source_file_order");
            EventConfSource source = sourceCache.computeIfAbsent(sourceId, id -> {
                EventConfSource s = new EventConfSource();
                s.setId(id);
                s.setName(sourceName);
                s.setFileOrder(sourceFileOrder);
                return s;
            });

            EventConfEvent event = new EventConfEvent();
            event.setId(rs.getLong("id"));
            event.setUei(rs.getString("uei"));
            event.setEventLabel(rs.getString("event_label"));
            event.setDescription(rs.getString("description"));
            event.setSeverity(rs.getString("severity"));
            event.setXmlContent(rs.getString("xml_content"));
            event.setEnabled(true);
            event.setSource(source);
            events.add(event);
        });

        eventConfDao.loadEventsFromDB(events);
        long elapsed = System.currentTimeMillis() - start;
        LOG.info("Loaded {} event definitions from database in {} ms", events.size(), elapsed);
    }
}
