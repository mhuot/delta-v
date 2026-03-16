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
package org.opennms.netmgt.translator.boot;

import java.util.Collection;
import java.util.Objects;

import org.opennms.core.daemon.common.EventConfEnrichmentService;
import org.opennms.netmgt.events.api.EventIpcManager;
import org.opennms.netmgt.events.api.EventListener;
import org.opennms.netmgt.events.api.EventProxyException;
import org.opennms.netmgt.xml.event.Event;
import org.opennms.netmgt.xml.event.Log;

/**
 * Delegating {@link EventIpcManager} wrapper that enriches events with
 * alarm-data, severity, and logmsg from the event configuration before
 * forwarding them to the real {@link EventIpcManager}.
 *
 * <p>Used by EventTranslator so that translated events are enriched before
 * being published to Kafka, ensuring downstream Alarmd sees fully-populated
 * alarm-data without needing to re-enrich on the alarm side.</p>
 */
public class EventIpcManagerEnrichingWrapper implements EventIpcManager {

    private final EventIpcManager delegate;
    private final EventConfEnrichmentService enrichmentService;

    public EventIpcManagerEnrichingWrapper(EventIpcManager delegate,
                                           EventConfEnrichmentService enrichmentService) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.enrichmentService = Objects.requireNonNull(enrichmentService, "enrichmentService");
    }

    @Override
    public void sendNow(Event event) {
        enrichmentService.enrichEvent(event);
        delegate.sendNow(event);
    }

    @Override
    public void sendNow(Log eventLog) {
        if (eventLog != null && eventLog.getEvents() != null) {
            for (Event event : eventLog.getEvents().getEventCollection()) {
                enrichmentService.enrichEvent(event);
            }
        }
        delegate.sendNow(eventLog);
    }

    @Override
    public void sendNowSync(Event event) {
        enrichmentService.enrichEvent(event);
        delegate.sendNowSync(event);
    }

    @Override
    public void sendNowSync(Log eventLog) {
        if (eventLog != null && eventLog.getEvents() != null) {
            for (Event event : eventLog.getEvents().getEventCollection()) {
                enrichmentService.enrichEvent(event);
            }
        }
        delegate.sendNowSync(eventLog);
    }

    @Override
    public void send(Event event) throws EventProxyException {
        enrichmentService.enrichEvent(event);
        delegate.send(event);
    }

    @Override
    public void send(Log eventLog) throws EventProxyException {
        if (eventLog != null && eventLog.getEvents() != null) {
            for (Event event : eventLog.getEvents().getEventCollection()) {
                enrichmentService.enrichEvent(event);
            }
        }
        delegate.send(eventLog);
    }

    @Override
    public void addEventListener(EventListener listener) {
        delegate.addEventListener(listener);
    }

    @Override
    public void addEventListener(EventListener listener, Collection<String> ueis) {
        delegate.addEventListener(listener, ueis);
    }

    @Override
    public void addEventListener(EventListener listener, String uei) {
        delegate.addEventListener(listener, uei);
    }

    @Override
    public void removeEventListener(EventListener listener) {
        delegate.removeEventListener(listener);
    }

    @Override
    public void removeEventListener(EventListener listener, Collection<String> ueis) {
        delegate.removeEventListener(listener, ueis);
    }

    @Override
    public void removeEventListener(EventListener listener, String uei) {
        delegate.removeEventListener(listener, uei);
    }

    @Override
    public boolean hasEventListener(String uei) {
        return delegate.hasEventListener(uei);
    }
}
