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
package org.opennms.minion.boot;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.opennms.core.daemon.registry.CollectorRegistryConfiguration;
import org.opennms.netmgt.collection.api.ServiceCollectorRegistry;
import org.opennms.netmgt.collection.client.rpc.CollectorClientRpcModule;
import org.opennms.netmgt.snmp.proxy.LocationAwareSnmpClient;
import org.opennms.netmgt.snmp.proxy.common.LocationAwareSnmpClientRpcImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Wires the Collector RPC module (module ID "Collect") and its
 * {@link ServiceCollectorRegistry}.
 *
 * <p>The {@link ServiceCollectorRegistry} is provided by
 * {@link CollectorRegistryConfiguration}, which registers explicit collector beans.
 * The imported configuration constructs {@link org.opennms.netmgt.collectd.SnmpCollector}
 * with a {@link LocationAwareSnmpClient}, which we supply here.</p>
 */
@Configuration
@Import(CollectorRegistryConfiguration.class)
@ConditionalOnProperty(name = "opennms.minion.collector.enabled", havingValue = "true", matchIfMissing = true)
public class CollectorConfiguration {

    @Bean
    public LocationAwareSnmpClient locationAwareSnmpClient() {
        return new LocationAwareSnmpClientRpcImpl();
    }

    @Bean
    public Executor collectorExecutor() {
        return Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "collector-rpc");
            t.setDaemon(true);
            return t;
        });
    }

    @Bean
    public CollectorClientRpcModule collectorClientRpcModule(ServiceCollectorRegistry serviceCollectorRegistry,
                                                              @org.springframework.beans.factory.annotation.Qualifier("collectorExecutor") Executor collectorExecutor) {
        CollectorClientRpcModule module = new CollectorClientRpcModule();
        module.setServiceCollectorRegistry(serviceCollectorRegistry);
        module.setExecutor(collectorExecutor);
        return module;
    }
}
