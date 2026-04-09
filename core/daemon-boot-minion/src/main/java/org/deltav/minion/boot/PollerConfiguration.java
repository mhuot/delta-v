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

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.deltav.core.daemon.registry.MonitorRegistryConfiguration;
import org.opennms.netmgt.poller.ServiceMonitorRegistry;
import org.opennms.netmgt.poller.client.rpc.PollerClientRpcModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Wires the Poller RPC module (module ID "Poller") and its
 * {@link ServiceMonitorRegistry}.
 *
 * <p>The {@link ServiceMonitorRegistry} is provided by
 * {@link MonitorRegistryConfiguration}, which registers explicit monitor beans.</p>
 */
@Configuration
@Import(MonitorRegistryConfiguration.class)
@ConditionalOnProperty(name = "opennms.minion.poller.enabled", havingValue = "true", matchIfMissing = true)
public class PollerConfiguration {

    @Bean
    public Executor pollerExecutor() {
        return Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "poller-rpc");
            t.setDaemon(true);
            return t;
        });
    }

    @Bean
    public PollerClientRpcModule pollerClientRpcModule(ServiceMonitorRegistry serviceMonitorRegistry,
                                                       @org.springframework.beans.factory.annotation.Qualifier("pollerExecutor") Executor pollerExecutor) {
        PollerClientRpcModule module = new PollerClientRpcModule();
        module.setServiceMonitorRegistry(serviceMonitorRegistry);
        module.setExecutor(pollerExecutor);
        return module;
    }
}
