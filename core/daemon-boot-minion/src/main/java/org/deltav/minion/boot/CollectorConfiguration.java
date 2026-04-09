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
package org.deltav.minion.boot;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.deltav.core.daemon.registry.LocalServiceCollectorRegistry;
import org.opennms.netmgt.collectd.SnmpCollector;
import org.opennms.netmgt.collection.api.ServiceCollectorRegistry;
import org.opennms.netmgt.collection.client.rpc.CollectorClientRpcModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Collector RPC module (module ID "Collect") and its
 * {@link ServiceCollectorRegistry} on Minion.
 *
 * <p>Unlike the Horizon side (which uses
 * {@code CollectorRegistryConfiguration}), Minion does not have an
 * {@code RpcClientFactory}, so it cannot instantiate the RPC-backed
 * {@code LocationAwareSnmpClient}. The {@link SnmpCollector} runs locally
 * on Minion with its {@code m_client} field left null — on first
 * {@code collect()} invocation, the collector falls back to a
 * {@code BeanUtils} lookup that resolves to whatever {@code LocationAwareSnmpClient}
 * the Minion's wider Spring context provides.</p>
 */
@Configuration
@ConditionalOnProperty(name = "opennms.minion.collector.enabled", havingValue = "true", matchIfMissing = true)
public class CollectorConfiguration {

    @Bean
    public ServiceCollectorRegistry serviceCollectorRegistry() {
        return new LocalServiceCollectorRegistry(List.of(new SnmpCollector()));
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
