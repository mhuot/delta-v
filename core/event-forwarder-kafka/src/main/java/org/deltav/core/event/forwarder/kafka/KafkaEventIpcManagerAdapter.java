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
package org.deltav.core.event.forwarder.kafka;

import java.util.Collection;
import java.util.Objects;

import org.opennms.netmgt.events.api.EventForwarder;
import org.opennms.netmgt.events.api.EventIpcBroadcaster;
import org.opennms.netmgt.events.api.EventIpcManager;
import org.opennms.netmgt.events.api.EventListener;
import org.opennms.netmgt.events.api.EventProxyException;
import org.opennms.netmgt.events.api.EventSubscriptionService;
import org.opennms.netmgt.xml.event.Event;
import org.opennms.netmgt.xml.event.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin adapter that composes an {@link EventForwarder} and an
 * {@link EventSubscriptionService} into a single {@link EventIpcManager}
 * (and {@link EventIpcBroadcaster}) instance.
 *
 * <p>This allows existing daemon code that depends on {@code EventIpcManager}
 * to work unchanged when backed by Kafka-based implementations.</p>
 *
 * <p>{@link EventIpcBroadcaster#broadcastNow} is a no-op because in Kafka mode
 * broadcasting is handled by the Kafka consumer poll loop in
 * {@link KafkaEventSubscriptionService}.</p>
 */
public class KafkaEventIpcManagerAdapter implements EventIpcManager, EventIpcBroadcaster {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaEventIpcManagerAdapter.class);

    private final EventForwarder eventForwarder;
    private final EventSubscriptionService subscriptionService;

    public KafkaEventIpcManagerAdapter(EventForwarder eventForwarder,
                                       EventSubscriptionService subscriptionService) {
        this.eventForwarder = Objects.requireNonNull(eventForwarder, "eventForwarder");
        this.subscriptionService = Objects.requireNonNull(subscriptionService, "subscriptionService");
    }

    // -------- EventForwarder delegation --------

    @Override
    public void sendNow(Event event) {
        eventForwarder.sendNow(event);
    }

    @Override
    public void sendNow(Log eventLog) {
        eventForwarder.sendNow(eventLog);
    }

    @Override
    public void sendNowSync(Event event) {
        eventForwarder.sendNowSync(event);
    }

    @Override
    public void sendNowSync(Log eventLog) {
        eventForwarder.sendNowSync(eventLog);
    }

    // -------- EventProxy delegation (delegates to sendNow) --------

    @Override
    public void send(Event event) throws EventProxyException {
        eventForwarder.sendNow(event);
    }

    @Override
    public void send(Log eventLog) throws EventProxyException {
        eventForwarder.sendNow(eventLog);
    }

    // -------- EventSubscriptionService delegation --------

    @Override
    public void addEventListener(EventListener listener) {
        subscriptionService.addEventListener(listener);
    }

    @Override
    public void addEventListener(EventListener listener, Collection<String> ueis) {
        subscriptionService.addEventListener(listener, ueis);
    }

    @Override
    public void addEventListener(EventListener listener, String uei) {
        subscriptionService.addEventListener(listener, uei);
    }

    @Override
    public void removeEventListener(EventListener listener) {
        subscriptionService.removeEventListener(listener);
    }

    @Override
    public void removeEventListener(EventListener listener, Collection<String> ueis) {
        subscriptionService.removeEventListener(listener, ueis);
    }

    @Override
    public void removeEventListener(EventListener listener, String uei) {
        subscriptionService.removeEventListener(listener, uei);
    }

    @Override
    public boolean hasEventListener(String uei) {
        return subscriptionService.hasEventListener(uei);
    }

    // -------- EventIpcBroadcaster (no-op) --------

    @Override
    public void broadcastNow(Event event, boolean synchronous) {
        LOG.debug("broadcastNow() is a no-op in Kafka mode; event UEI={}, synchronous={}",
                event != null ? event.getUei() : "null", synchronous);
    }
}
