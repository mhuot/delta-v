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

import org.opennms.netmgt.provision.ServiceDetector;
import org.opennms.netmgt.provision.ServiceDetectorFactory;
import org.opennms.netmgt.provision.detector.registry.api.ServiceDetectorRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry of service detector factories provided via constructor injection.
 * Replaces ServiceLoader + reflection-based discovery — callers pass the exact
 * list of factories they want to expose.
 */
public class LocalServiceDetectorRegistry implements ServiceDetectorRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(LocalServiceDetectorRegistry.class);
    private final Map<String, ServiceDetectorFactory<?>> factoryByClassName = new HashMap<>();

    public LocalServiceDetectorRegistry(List<ServiceDetectorFactory<?>> factories) {
        Objects.requireNonNull(factories, "factories");
        for (ServiceDetectorFactory<?> factory : factories) {
            String detectorClassName = factory.getDetectorClass().getCanonicalName();
            factoryByClassName.put(detectorClassName, factory);
            LOG.info("Registered detector factory: {} -> {}", detectorClassName, factory.getClass().getCanonicalName());
        }
        LOG.info("Loaded {} detector factories", factoryByClassName.size());
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
        return Collections.unmodifiableSet(factoryByClassName.keySet());
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
