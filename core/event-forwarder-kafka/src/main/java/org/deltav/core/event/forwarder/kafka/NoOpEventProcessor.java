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

import org.opennms.netmgt.events.api.EventProcessor;
import org.opennms.netmgt.events.api.EventProcessorException;
import org.opennms.netmgt.xml.event.Log;

/**
 * No-op {@link EventProcessor} used in daemon containers where event
 * expansion (eventconf lookup) is not available. Events pass through
 * un-expanded; the core side handles expansion after Kafka consumption.
 */
public class NoOpEventProcessor implements EventProcessor {

    @Override
    public void process(Log eventLog) throws EventProcessorException {
        // no-op
    }

    @Override
    public void process(Log eventLog, boolean synchronous) throws EventProcessorException {
        // no-op
    }
}
