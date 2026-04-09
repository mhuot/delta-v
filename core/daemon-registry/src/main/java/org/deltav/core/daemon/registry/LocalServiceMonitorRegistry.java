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
