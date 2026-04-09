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
package org.deltav.core.daemon.registry;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.opennms.netmgt.poller.ServiceMonitor;
import org.opennms.netmgt.poller.ServiceMonitorRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry of service monitors provided via constructor injection.
 * Replaces ServiceLoader-based discovery — callers pass the exact list of
 * monitors they want to expose.
 */
public class LocalServiceMonitorRegistry implements ServiceMonitorRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(LocalServiceMonitorRegistry.class);
    private final Map<String, ServiceMonitor> monitorsByClassName = new HashMap<>();

    public LocalServiceMonitorRegistry(List<ServiceMonitor> monitors) {
        Objects.requireNonNull(monitors, "monitors");
        for (ServiceMonitor monitor : monitors) {
            String className = monitor.getClass().getCanonicalName();
            monitorsByClassName.put(className, monitor);
            LOG.info("Registered monitor: {}", className);
        }
        LOG.info("Loaded {} monitors", monitorsByClassName.size());
    }

    @Override
    public ServiceMonitor getMonitorByClassName(String className) {
        return monitorsByClassName.get(className);
    }

    @Override
    public Set<String> getMonitorClassNames() {
        return Collections.unmodifiableSet(monitorsByClassName.keySet());
    }
}
