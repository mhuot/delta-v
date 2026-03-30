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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.opennms.netmgt.config.api.EventConfDao;
import org.opennms.netmgt.model.EventConfEvent;
import org.opennms.netmgt.xml.eventconf.Event;
import org.opennms.netmgt.xml.eventconf.EventMatchers;
import org.opennms.netmgt.xml.eventconf.EventOrdering;
import org.opennms.netmgt.xml.eventconf.Events;
import org.opennms.netmgt.xml.eventconf.Field;
import org.opennms.netmgt.xml.eventconf.Partition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lightweight {@link EventConfDao} implementation for Spring Boot daemons.
 *
 * <p>Uses the eventconf matching engine ({@link Events}, {@link EventMatchers})
 * from {@code opennms-config-model} directly, avoiding the heavyweight
 * {@code DefaultEventConfDao} in {@code opennms-config} which transitively
 * depends on {@code opennms-model}.</p>
 *
 * <p>Only {@link #findByEvent} and {@link #loadEventsFromDB} are fully
 * implemented — these are the only methods used by daemon enrichment and
 * the Kafka event forwarder.</p>
 */
public class DaemonEventConfDao implements EventConfDao {

    private static final Logger LOG = LoggerFactory.getLogger(DaemonEventConfDao.class);

    /** Standard JAXB context for Event.class only — avoids EclipseLink MOXy's
     *  package scanning which triggers ClassNotFoundException for opennms-model types. */
    private static final JAXBContext EVENT_JAXB_CONTEXT;
    static {
        try {
            EVENT_JAXB_CONTEXT = JAXBContext.newInstance(Event.class);
        } catch (JAXBException e) {
            throw new ExceptionInInitializerError("Failed to create JAXBContext for Event: " + e.getMessage());
        }
    }

    private volatile Events events;

    public DaemonEventConfDao() {
        this.events = new Events();
        this.events.initialize(new EnterpriseIdPartition(), new EventOrdering());
    }

    @Override
    public Event findByEvent(org.opennms.netmgt.xml.event.Event matchingEvent) {
        return events.findFirstMatchingEvent(matchingEvent);
    }

    @Override
    public Event findByUei(String uei) {
        if (uei == null) {
            return null;
        }
        return events.findFirstMatchingEvent(e -> uei.equals(e.getUei()));
    }

    @Override
    public void loadEventsFromDB(List<EventConfEvent> dbEvents) {
        Map<String, List<EventConfEvent>> eventsBySource = dbEvents.stream()
                .collect(Collectors.groupingBy(
                        event -> event.getSource().getName(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<Map.Entry<String, List<EventConfEvent>>> sortedSources = eventsBySource.entrySet().stream()
                .sorted(Map.Entry.comparingByValue((events1, events2) -> {
                    Integer order1 = events1.get(0).getSource().getFileOrder();
                    Integer order2 = events2.get(0).getSource().getFileOrder();
                    return Integer.compare(order2 != null ? order2 : 0, order1 != null ? order1 : 0);
                }))
                .toList();

        Events rootEvents = new Events();

        for (Map.Entry<String, List<EventConfEvent>> sourceEntry : sortedSources) {
            Events eventsForSource = new Events();
            for (EventConfEvent dbEvent : sourceEntry.getValue()) {
                String xmlContent = dbEvent.getXmlContent();
                if (xmlContent != null && !xmlContent.trim().isEmpty()) {
                    try {
                        Unmarshaller unmarshaller = EVENT_JAXB_CONTEXT.createUnmarshaller();
                        Event event = (Event) unmarshaller.unmarshal(new StringReader(xmlContent));
                        if (event != null) {
                            eventsForSource.addEvent(event);
                        }
                    } catch (Exception e) {
                        LOG.warn("Failed to parse event XML for UEI {}: {}", dbEvent.getUei(), e.getMessage());
                    }
                }
            }
            rootEvents.addLoadedEventFile(sourceEntry.getKey(), eventsForSource);
        }

        Partition partition = new EnterpriseIdPartition();
        rootEvents.initialize(partition, new EventOrdering());
        this.events = rootEvents;
    }

    @Override
    public Events getRootEvents() {
        return events;
    }

    // --- Methods not needed by daemon enrichment / Kafka forwarder ---

    @Override
    public void reload() {
        // Reload is handled by periodic re-invocation of loadEventsFromDB
    }

    @Override
    public List<Event> getEvents(String uei) {
        throw new UnsupportedOperationException("Not used in daemon context");
    }

    @Override
    public List<String> getEventUEIs() {
        throw new UnsupportedOperationException("Not used in daemon context");
    }

    @Override
    public Map<String, String> getEventLabels() {
        throw new UnsupportedOperationException("Not used in daemon context");
    }

    @Override
    public String getEventLabel(String uei) {
        throw new UnsupportedOperationException("Not used in daemon context");
    }

    @Override
    public List<Event> getEventsByLabel() {
        throw new UnsupportedOperationException("Not used in daemon context");
    }

    @Override
    public void addEvent(Event event) {
        throw new UnsupportedOperationException("Not used in daemon context");
    }

    @Override
    public boolean isSecureTag(String tag) {
        return false;
    }

    // --- Partition ---

    private static class EnterpriseIdPartition implements Partition {

        private final Field field = EventMatchers.field("id");

        @Override
        public List<String> group(Event eventConf) {
            List<String> keys = eventConf.getMaskElementValues("id");
            if (keys == null) return null;
            for (String key : keys) {
                if (key.endsWith("%")) return null;
                if (key.startsWith("~")) return null;
            }
            return keys;
        }

        @Override
        public String group(org.opennms.netmgt.xml.event.Event matchingEvent) {
            return field.get(matchingEvent);
        }
    }
}
