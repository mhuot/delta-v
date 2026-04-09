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
package org.deltav.netmgt.poller.boot;

import org.opennms.core.tsid.TsidFactory;
import org.opennms.netmgt.config.PollerConfig;
import org.opennms.netmgt.events.api.EventIpcManager;
import org.opennms.netmgt.icmp.proxy.LocationAwarePingClient;
import org.opennms.netmgt.poller.DefaultPollContext;
import org.opennms.netmgt.poller.QueryManager;

/**
 * Standalone PollContext that skips AsyncPollingEngine initialization.
 *
 * <p>In standalone daemon containers, resilience4j bundles may not be wired
 * to opennms-services at class-load time. The AsyncPollingEngine (which
 * uses resilience4j Bulkhead) is not needed -- polls execute via Kafka RPC
 * to Minion without async bulkhead control.</p>
 */
public class StandalonePollContext extends DefaultPollContext {

    public StandalonePollContext(EventIpcManager eventManager, PollerConfig pollerConfig,
                                 QueryManager queryManager, LocationAwarePingClient locationAwarePingClient,
                                 TsidFactory tsidFactory, String localHostName, String name) {
        super(eventManager, pollerConfig, queryManager, locationAwarePingClient,
              tsidFactory, localHostName, name);
    }

    @Override
    public void afterPropertiesSet() {
        // Skip AsyncPollingEngine creation -- not needed for standalone polling.
        // The parent creates new AsyncPollingEngine(...) which requires
        // resilience4j-bulkhead, unavailable in the standalone container.
    }
}
