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
package org.deltav.netmgt.provision.boot;

import org.opennms.netmgt.events.api.EventForwarder;
import org.opennms.netmgt.xml.event.Event;
import org.opennms.netmgt.xml.event.Log;

/**
 * Simple delegating {@link EventForwarder} that exists solely to satisfy
 * {@code @Qualifier("transactionAware")} injection points in standalone
 * daemon containers.
 *
 * <p>In the monolithic OpenNMS, {@code TransactionAwareEventForwarder}
 * defers event sending until after JPA transaction commit. In standalone
 * containers, events go straight to Kafka (no JPA transaction coordination
 * needed), so this class simply delegates to the underlying EventForwarder.</p>
 */
public class QualifiedEventForwarder implements EventForwarder {

    private final EventForwarder delegate;

    public QualifiedEventForwarder(EventForwarder delegate) {
        this.delegate = delegate;
    }

    @Override
    public void sendNow(Event event) {
        delegate.sendNow(event);
    }

    @Override
    public void sendNow(Log eventLog) {
        delegate.sendNow(eventLog);
    }

    @Override
    public void sendNowSync(Event event) {
        delegate.sendNowSync(event);
    }

    @Override
    public void sendNowSync(Log eventLog) {
        delegate.sendNowSync(eventLog);
    }
}
