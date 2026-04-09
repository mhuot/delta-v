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
