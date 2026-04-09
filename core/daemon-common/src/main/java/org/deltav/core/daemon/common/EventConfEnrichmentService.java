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

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.opennms.netmgt.config.api.EventConfDao;
import org.opennms.netmgt.model.EventConfEvent;
import org.opennms.netmgt.model.EventConfSource;
import org.opennms.netmgt.xml.event.AlarmData;
import org.opennms.netmgt.xml.event.Event;
import org.opennms.netmgt.xml.event.Parm;
import org.opennms.netmgt.xml.event.UpdateField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Shared event-configuration enrichment service for Spring Boot daemons.
 *
 * <p>Loads all enabled event configurations from PostgreSQL into an in-memory
 * {@link DefaultEventConfDao} on construction and exposes a single
 * {@link #enrichEvent(Event)} method that all daemons can call to apply
 * alarm-data, severity, and logmsg from the matching event conf entry.</p>
 *
 * <p>Parameter tokens in reduction-key / clear-key (e.g. {@code %uei%},
 * {@code %dpname%}, {@code %nodeid%}, {@code %parm[#1]%}) are expanded with
 * values taken directly from the incoming event so that the keys match what
 * classic OpenNMS would generate.</p>
 */
@Service
public class EventConfEnrichmentService {

    private static final Logger LOG = LoggerFactory.getLogger(EventConfEnrichmentService.class);

    private final DaemonEventConfDao eventConfDao;

    public EventConfEnrichmentService(DataSource dataSource) {
        this.eventConfDao = new DaemonEventConfDao();
        List<EventConfEvent> events = loadEventConfFromDb(new JdbcTemplate(dataSource));
        if (!events.isEmpty()) {
            eventConfDao.loadEventsFromDB(events);
            LOG.info("EventConfEnrichmentService: loaded {} event configurations from database", events.size());
        } else {
            LOG.warn("EventConfEnrichmentService: no event configurations loaded — enrichment will be disabled");
        }
    }

    /**
     * Returns the underlying EventConfDao for use by components that need
     * direct access to event configuration lookups (e.g., KafkaEventForwarder).
     */
    public EventConfDao getEventConfDao() {
        return eventConfDao;
    }

    /**
     * Enriches an event by looking up the matching event conf entry and applying
     * alarm-data, severity, and logmsg when the event is missing them.
     *
     * <p>This method is a no-op when no matching event conf entry is found.</p>
     *
     * @param event the event to enrich (mutated in place)
     */
    public void enrichEvent(Event event) {
        org.opennms.netmgt.xml.eventconf.Event matched = eventConfDao.findByEvent(event);
        if (matched == null) {
            LOG.debug("enrichEvent: no event conf match for UEI '{}'; skipping enrichment", event.getUei());
            return;
        }

        if (event.getAlarmData() == null && matched.getAlarmData() != null) {
            AlarmData alarmData = convertAlarmData(matched.getAlarmData(), event);
            event.setAlarmData(alarmData);
            LOG.debug("enrichEvent: applied alarm-data (reductionKey='{}') to event UEI '{}'",
                alarmData.getReductionKey(), event.getUei());
        }

        if (event.getSeverity() == null && matched.getSeverity() != null) {
            event.setSeverity(matched.getSeverity());
        }

        if (event.getLogmsg() == null && matched.getLogmsg() != null) {
            org.opennms.netmgt.xml.event.Logmsg eventLogmsg = new org.opennms.netmgt.xml.event.Logmsg();
            org.opennms.netmgt.xml.eventconf.Logmsg confLogmsg = matched.getLogmsg();
            eventLogmsg.setContent(confLogmsg.getContent());
            if (confLogmsg.getDest() != null) {
                eventLogmsg.setDest(confLogmsg.getDest().toString());
            }
            event.setLogmsg(eventLogmsg);
        }
    }

    // -------------------------------------------------------------------------
    // Database loading
    // -------------------------------------------------------------------------

    /**
     * Loads all enabled event configurations from the database using plain JDBC.
     * Joins {@code eventconf_events} with {@code eventconf_sources} so that each
     * {@link EventConfEvent} has its parent {@link EventConfSource} populated,
     * which is required by {@link DefaultEventConfDao#loadEventsFromDB}.
     */
    private List<EventConfEvent> loadEventConfFromDb(JdbcTemplate jdbc) {
        Map<Long, EventConfSource> sourcesById = new HashMap<>();
        try {
            jdbc.query(
                "SELECT id, name, description, vendor, file_order, enabled, event_count " +
                "FROM eventconf_sources ORDER BY file_order",
                (ResultSet rs) -> {
                    EventConfSource source = new EventConfSource();
                    source.setId(rs.getLong("id"));
                    source.setName(rs.getString("name"));
                    source.setDescription(rs.getString("description"));
                    source.setVendor(rs.getString("vendor"));
                    source.setFileOrder(rs.getInt("file_order"));
                    source.setEnabled(rs.getBoolean("enabled"));
                    source.setEventCount(rs.getInt("event_count"));
                    sourcesById.put(source.getId(), source);
                }
            );
        } catch (Exception e) {
            LOG.error("Failed to load eventconf_sources from database", e);
            return List.of();
        }

        if (sourcesById.isEmpty()) {
            LOG.warn("No sources found in eventconf_sources table");
            return List.of();
        }

        List<EventConfEvent> events = new ArrayList<>();
        try {
            jdbc.query(
                "SELECT e.id, e.source_id, e.uei, e.event_label, e.description, " +
                "       e.enabled, e.xml_content, e.severity " +
                "FROM eventconf_events e " +
                "JOIN eventconf_sources s ON e.source_id = s.id " +
                "WHERE e.enabled = true AND s.enabled = true " +
                "ORDER BY s.file_order, e.id",
                (ResultSet rs) -> {
                    long sourceId = rs.getLong("source_id");
                    EventConfSource source = sourcesById.get(sourceId);
                    if (source == null) {
                        return;
                    }

                    EventConfEvent event = new EventConfEvent();
                    event.setId(rs.getLong("id"));
                    event.setSource(source);
                    event.setUei(rs.getString("uei"));
                    event.setEventLabel(rs.getString("event_label"));
                    event.setDescription(rs.getString("description"));
                    event.setEnabled(rs.getBoolean("enabled"));
                    event.setXmlContent(rs.getString("xml_content"));
                    event.setSeverity(rs.getString("severity"));
                    events.add(event);
                }
            );
        } catch (Exception e) {
            LOG.error("Failed to load eventconf_events from database", e);
            return List.of();
        }

        return events;
    }

    // -------------------------------------------------------------------------
    // Parameter expansion and conversion helpers
    // -------------------------------------------------------------------------

    /**
     * Converts an {@code eventconf} {@link org.opennms.netmgt.xml.eventconf.AlarmData}
     * to an {@code event} {@link AlarmData}, expanding all parameter tokens in
     * reduction-key and clear-key using values from the live event.
     */
    private AlarmData convertAlarmData(
            org.opennms.netmgt.xml.eventconf.AlarmData confAlarmData,
            Event event) {
        AlarmData alarmData = new AlarmData();

        alarmData.setReductionKey(expandParms(confAlarmData.getReductionKey(), event));

        if (confAlarmData.getClearKey() != null) {
            alarmData.setClearKey(expandParms(confAlarmData.getClearKey(), event));
        }

        alarmData.setAlarmType(confAlarmData.getAlarmType());
        alarmData.setAutoClean(confAlarmData.getAutoClean());

        if (confAlarmData.getX733AlarmType() != null) {
            alarmData.setX733AlarmType(confAlarmData.getX733AlarmType());
        }
        if (confAlarmData.getX733ProbableCause() != null) {
            alarmData.setX733ProbableCause(confAlarmData.getX733ProbableCause());
        }

        if (!confAlarmData.getUpdateFields().isEmpty()) {
            List<UpdateField> updateFields = new ArrayList<>();
            for (org.opennms.netmgt.xml.eventconf.UpdateField confField : confAlarmData.getUpdateFields()) {
                UpdateField field = new UpdateField();
                field.setFieldName(confField.getFieldName());
                field.setUpdateOnReduction(confField.getUpdateOnReduction());
                updateFields.add(field);
            }
            alarmData.setUpdateField(updateFields);
        }

        if (confAlarmData.getManagedObject() != null) {
            org.opennms.netmgt.xml.event.ManagedObject mo = new org.opennms.netmgt.xml.event.ManagedObject();
            mo.setType(confAlarmData.getManagedObject().getType());
            alarmData.setManagedObject(mo);
        }

        return alarmData;
    }

    /**
     * Expands well-known OpenNMS parameter tokens in a string using values from
     * the provided event.
     *
     * <p>Supported tokens:
     * <ul>
     *   <li>{@code %uei%} — event UEI</li>
     *   <li>{@code %dpname%} or {@code %distpoller%} — dist-poller / source system name</li>
     *   <li>{@code %nodeid%} — node ID</li>
     *   <li>{@code %interface%} — interface IP address</li>
     *   <li>{@code %service%} — service name</li>
     *   <li>{@code %parm[#N]%} — Nth event parameter value (1-based)</li>
     *   <li>{@code %parm[name]%} — event parameter value by name</li>
     * </ul>
     */
    private String expandParms(String template, Event event) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        String result = template;

        result = replaceToken(result, "%uei%", safeToString(event.getUei()));
        result = replaceToken(result, "%dpname%", safeToString(event.getDistPoller()));
        result = replaceToken(result, "%distpoller%", safeToString(event.getDistPoller()));
        result = replaceToken(result, "%nodeid%", event.getNodeid() != null ? event.getNodeid().toString() : "0");
        result = replaceToken(result, "%interface%", safeToString(event.getInterface()));
        result = replaceToken(result, "%service%", safeToString(event.getService()));

        result = expandPositionalParmTokens(result, event);
        result = expandNamedParmTokens(result, event);

        return result;
    }

    private String replaceToken(String input, String token, String value) {
        if (!input.contains(token)) {
            return input;
        }
        return input.replace(token, value != null ? value : "");
    }

    /**
     * Expands {@code %parm[#N]%} tokens where N is a 1-based positional index.
     */
    private String expandPositionalParmTokens(String input, Event event) {
        if (!input.contains("%parm[#")) {
            return input;
        }
        List<Parm> parms = event.getParmCollection();
        StringBuilder result = new StringBuilder(input);
        int searchFrom = 0;
        while (true) {
            int start = result.indexOf("%parm[#", searchFrom);
            if (start < 0) {
                break;
            }
            int end = result.indexOf("]%", start + 7);
            if (end < 0) {
                break;
            }
            String indexStr = result.substring(start + 7, end);
            String replacement = "";
            try {
                int index = Integer.parseInt(indexStr) - 1; // convert to 0-based
                if (parms != null && index >= 0 && index < parms.size()) {
                    Parm parm = parms.get(index);
                    if (parm.getValue() != null && parm.getValue().getContent() != null) {
                        replacement = parm.getValue().getContent();
                    }
                }
            } catch (NumberFormatException ignored) {
                // leave replacement as empty string
            }
            result.replace(start, end + 2, replacement);
            searchFrom = start + replacement.length();
        }
        return result.toString();
    }

    /**
     * Expands {@code %parm[name]%} tokens where name is a parameter name.
     */
    private String expandNamedParmTokens(String input, Event event) {
        if (!input.contains("%parm[")) {
            return input;
        }
        List<Parm> parms = event.getParmCollection();
        if (parms == null || parms.isEmpty()) {
            return input;
        }
        Map<String, String> parmValues = new HashMap<>();
        for (Parm parm : parms) {
            if (parm.getParmName() != null && parm.getValue() != null
                    && parm.getValue().getContent() != null) {
                parmValues.put(parm.getParmName(), parm.getValue().getContent());
            }
        }

        StringBuilder result = new StringBuilder(input);
        int searchFrom = 0;
        while (true) {
            int start = result.indexOf("%parm[", searchFrom);
            if (start < 0) {
                break;
            }
            // Skip positional tokens that begin with '#'
            if (start + 6 < result.length() && result.charAt(start + 6) == '#') {
                searchFrom = start + 6;
                continue;
            }
            int end = result.indexOf("]%", start + 6);
            if (end < 0) {
                break;
            }
            String parmName = result.substring(start + 6, end);
            String replacement = parmValues.getOrDefault(parmName, "");
            result.replace(start, end + 2, replacement);
            searchFrom = start + replacement.length();
        }
        return result.toString();
    }

    private String safeToString(Object value) {
        return value != null ? value.toString() : "";
    }
}
