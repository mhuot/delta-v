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
import java.util.concurrent.CompletableFuture;

import org.opennms.netmgt.collection.api.ServiceCollector;
import org.opennms.netmgt.collection.api.ServiceCollectorRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry of service collectors provided via constructor injection.
 * Replaces ServiceLoader-based discovery — callers pass the exact list of
 * collectors they want to expose.
 */
public class LocalServiceCollectorRegistry implements ServiceCollectorRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(LocalServiceCollectorRegistry.class);
    private final Map<String, ServiceCollector> collectorsByClassName = new HashMap<>();

    public LocalServiceCollectorRegistry(List<ServiceCollector> collectors) {
        Objects.requireNonNull(collectors, "collectors");
        for (ServiceCollector collector : collectors) {
            String className = collector.getClass().getCanonicalName();
            collectorsByClassName.put(className, collector);
            LOG.info("Registered collector: {}", className);
        }
        LOG.info("Loaded {} collectors", collectorsByClassName.size());
    }

    @Override
    public CompletableFuture<ServiceCollector> getCollectorFutureByClassName(String className) {
        return CompletableFuture.completedFuture(collectorsByClassName.get(className));
    }

    @Override
    public Set<String> getCollectorClassNames() {
        return Collections.unmodifiableSet(collectorsByClassName.keySet());
    }
}
