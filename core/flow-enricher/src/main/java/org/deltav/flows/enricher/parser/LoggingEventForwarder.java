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
package org.deltav.flows.enricher.parser;

import org.opennms.netmgt.events.api.EventForwarder;
import org.opennms.netmgt.xml.event.Event;
import org.opennms.netmgt.xml.event.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link EventForwarder} that logs events at {@code WARN} level and then
 * discards them. The horizon Netflow parsers use {@code EventForwarder} to
 * emit operational signals (clock skew detection, repeated unknown template
 * IDs, illegal flow records) — this implementation keeps those visible in
 * the flow-enricher log without wiring up a real event pipeline.
 *
 * <p>A Kafka-backed event forwarder is deferred as a followup per the Phase 2
 * design.
 */
public class LoggingEventForwarder implements EventForwarder {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingEventForwarder.class);

    @Override
    public void sendNow(Event event) {
        logEvent(event);
    }

    @Override
    public void sendNow(Log eventLog) {
        logBatch(eventLog);
    }

    @Override
    public void sendNowSync(Event event) {
        logEvent(event);
    }

    @Override
    public void sendNowSync(Log eventLog) {
        logBatch(eventLog);
    }

    private void logEvent(Event event) {
        if (event == null) {
            return;
        }
        LOG.warn("Parser event dropped (logging-only forwarder): uei={}, source={}",
                event.getUei(), event.getSource());
    }

    private void logBatch(Log eventLog) {
        if (eventLog == null || eventLog.getEvents() == null || eventLog.getEvents().getEvent() == null) {
            return;
        }
        for (Event e : eventLog.getEvents().getEvent()) {
            logEvent(e);
        }
    }
}
