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
package org.opennms.core.daemon.loader;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import org.opennms.netmgt.poller.ServiceMonitor;
import org.opennms.netmgt.poller.ServiceMonitorRegistry;
import org.opennms.netmgt.poller.monitors.PassiveServiceMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local ServiceMonitorRegistry for standalone daemon containers.
 * Discovers ServiceMonitor implementations via Java ServiceLoader
 * and explicitly registers monitors that can't be discovered in OSGi.
 */
public class LocalServiceMonitorRegistry implements ServiceMonitorRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(LocalServiceMonitorRegistry.class);

    private final Map<String, ServiceMonitor> monitorsByClassName = new HashMap<>();

    /**
     * Monitors to register explicitly because OSGi's ServiceLoader can't
     * discover them across bundle boundaries. These are the monitors from
     * poller-monitors-core that Delta-V E2E tests require.
     */
    private static final String[] EXPLICIT_MONITORS = {
        "org.opennms.netmgt.poller.monitors.TcpMonitor",
        "org.opennms.netmgt.poller.monitors.PageSequenceMonitor",
        "org.opennms.netmgt.poller.monitors.HttpMonitor",
        "org.opennms.netmgt.poller.monitors.HttpsMonitor",
        "org.opennms.netmgt.poller.monitors.DnsMonitor",
        "org.opennms.netmgt.poller.monitors.IcmpMonitor",
        "org.opennms.netmgt.poller.monitors.SnmpMonitor",
        "org.opennms.netmgt.poller.monitors.SshMonitor",
        "org.opennms.netmgt.poller.monitors.SSLCertMonitor",
    };

    public LocalServiceMonitorRegistry() {
        for (ServiceMonitor monitor : ServiceLoader.load(ServiceMonitor.class)) {
            final String className = monitor.getClass().getCanonicalName();
            LOG.info("Registered service monitor via ServiceLoader: {}", className);
            monitorsByClassName.put(className, monitor);
        }
        // In Karaf OSGi, ServiceLoader can't discover monitors across bundle boundaries.
        // Explicitly register monitors from poller-api that aren't in the monitors-core JAR.
        monitorsByClassName.putIfAbsent(PassiveServiceMonitor.class.getCanonicalName(), new PassiveServiceMonitor());
        // Register common monitors from poller-monitors-core via reflection.
        // DynamicImport-Package: * in the bundle manifest allows loading classes
        // from any other bundle at runtime.
        for (String className : EXPLICIT_MONITORS) {
            if (!monitorsByClassName.containsKey(className)) {
                try {
                    // Use this class's bundle classloader (which has DynamicImport-Package: *)
                    // rather than the thread context classloader which may be from a different bundle
                    Class<?> clazz = Class.forName(className, true, LocalServiceMonitorRegistry.class.getClassLoader());
                    monitorsByClassName.put(className, (ServiceMonitor) clazz.getDeclaredConstructor().newInstance());
                    LOG.info("Registered service monitor via reflection: {}", className);
                } catch (Exception e) {
                    LOG.warn("Could not register monitor {}: {}", className, e.getMessage());
                }
            }
        }
        LOG.info("Loaded {} service monitors total", monitorsByClassName.size());
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
