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
package org.opennms.core.daemon.common.registry;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import org.opennms.netmgt.provision.ServiceDetector;
import org.opennms.netmgt.provision.ServiceDetectorFactory;
import org.opennms.netmgt.provision.detector.registry.api.ServiceDetectorRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local ServiceDetectorRegistry for standalone daemon containers.
 * Discovers ServiceDetectorFactory implementations via ServiceLoader,
 * then falls back to explicit reflection for factories that ServiceLoader
 * cannot discover across OSGi bundle boundaries.
 * Used by both Provisiond and Discovery daemon-loaders.
 */
public class LocalServiceDetectorRegistry implements ServiceDetectorRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(LocalServiceDetectorRegistry.class);
    private final Map<String, ServiceDetectorFactory<?>> factoryByClassName = new HashMap<>();

    /**
     * Detector factories to register explicitly because OSGi's ServiceLoader
     * can't discover them across bundle boundaries. These are concrete factory
     * classes with no-arg constructors that Delta-V deployments require.
     */
    private static final String[] EXPLICIT_DETECTOR_FACTORIES = {
        // opennms-detector-simple
        "org.opennms.netmgt.provision.detector.icmp.IcmpDetectorFactory",
        "org.opennms.netmgt.provision.detector.snmp.SnmpDetectorFactory",
        "org.opennms.netmgt.provision.detector.smb.SmbDetectorFactory",
        "org.opennms.netmgt.provision.detector.loop.LoopDetectorFactory",
        // opennms-detector-lineoriented
        "org.opennms.netmgt.provision.detector.msexchange.MSExchangeDetectorFactory",
        "org.opennms.netmgt.provision.detector.simple.CitrixDetectorFactory",
        "org.opennms.netmgt.provision.detector.simple.DominoIIOPDetectorFactory",
        "org.opennms.netmgt.provision.detector.simple.FtpDetectorFactory",
        "org.opennms.netmgt.provision.detector.simple.HttpDetectorFactory",
        "org.opennms.netmgt.provision.detector.simple.HttpsDetectorFactory",
        "org.opennms.netmgt.provision.detector.simple.ImapDetectorFactory",
        "org.opennms.netmgt.provision.detector.simple.ImapsDetectorFactory",
        "org.opennms.netmgt.provision.detector.simple.LdapDetectorFactory",
        "org.opennms.netmgt.provision.detector.simple.LdapsDetectorFactory",
        "org.opennms.netmgt.provision.detector.simple.MemcachedDetectorFactory",
        "org.opennms.netmgt.provision.detector.simple.NotesHttpDetectorFactory",
        "org.opennms.netmgt.provision.detector.simple.NrpeDetectorFactory",
        "org.opennms.netmgt.provision.detector.simple.Pop3DetectorFactory",
        "org.opennms.netmgt.provision.detector.simple.SmtpDetectorFactory",
        "org.opennms.netmgt.provision.detector.simple.TcpDetectorFactory",
        "org.opennms.netmgt.provision.detector.simple.TrivialTimeDetectorFactory",
        // opennms-detector-datagram
        "org.opennms.netmgt.provision.detector.datagram.DnsDetectorFactory",
        "org.opennms.netmgt.provision.detector.datagram.NtpDetectorFactory",
        // opennms-detector-ssh
        "org.opennms.netmgt.provision.detector.ssh.SshDetectorFactory",
        // opennms-detector-web
        "org.opennms.netmgt.provision.detector.web.WebDetectorFactory",
        // opennms-detector-jmx
        "org.opennms.netmgt.provision.detector.jmx.Jsr160DetectorFactory",
    };

    public LocalServiceDetectorRegistry() {
        ServiceLoader<ServiceDetectorFactory> loader = ServiceLoader.load(ServiceDetectorFactory.class);
        for (ServiceDetectorFactory<?> factory : loader) {
            String detectorClassName = factory.getDetectorClass().getCanonicalName();
            factoryByClassName.put(detectorClassName, factory);
            LOG.info("Registered detector factory via ServiceLoader: {} -> {}", detectorClassName, factory.getClass().getCanonicalName());
        }
        // In Karaf OSGi, ServiceLoader can't discover factories across bundle boundaries.
        // Explicitly register factories via reflection using DynamicImport-Package: *.
        for (String factoryClassName : EXPLICIT_DETECTOR_FACTORIES) {
            try {
                Class<?> clazz = Class.forName(factoryClassName, true, LocalServiceDetectorRegistry.class.getClassLoader());
                ServiceDetectorFactory<?> factory = (ServiceDetectorFactory<?>) clazz.getDeclaredConstructor().newInstance();
                String detectorClassName = factory.getDetectorClass().getCanonicalName();
                if (!factoryByClassName.containsKey(detectorClassName)) {
                    factoryByClassName.put(detectorClassName, factory);
                    LOG.info("Registered detector factory via reflection: {} -> {}", detectorClassName, factoryClassName);
                }
            } catch (Exception e) {
                LOG.warn("Could not register detector factory {}: {}", factoryClassName, e.getMessage());
            }
        }
        LOG.info("Loaded {} detector factories total", factoryByClassName.size());
    }

    @Override
    public Map<String, String> getTypes() {
        Map<String, String> types = new HashMap<>();
        for (Map.Entry<String, ServiceDetectorFactory<?>> entry : factoryByClassName.entrySet()) {
            types.put(entry.getKey(), entry.getValue().getDetectorClass().getCanonicalName());
        }
        return types;
    }

    @Override
    public Set<String> getClassNames() {
        return factoryByClassName.keySet();
    }

    @Override
    public ServiceDetector getDetectorByClassName(String className, Map<String, String> properties) {
        ServiceDetectorFactory<?> factory = factoryByClassName.get(className);
        if (factory != null) {
            return factory.createDetector(properties);
        }
        return null;
    }

    @Override
    public ServiceDetectorFactory<?> getDetectorFactoryByClassName(String className) {
        return factoryByClassName.get(className);
    }

    @Override
    public Set<String> getServiceNames() {
        return Collections.emptySet();
    }

    @Override
    public String getDetectorClassNameFromServiceName(String serviceName) {
        return null;
    }

    @Override
    public Class<?> getDetectorClassByServiceName(String serviceName) {
        return null;
    }
}
