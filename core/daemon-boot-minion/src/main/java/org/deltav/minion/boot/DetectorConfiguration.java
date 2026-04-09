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

import org.deltav.core.daemon.registry.DetectorRegistryConfiguration;
import org.deltav.core.daemon.registry.NoOpSnmpAgentConfigFactory;
import org.opennms.netmgt.config.api.SnmpAgentConfigFactory;
import org.opennms.netmgt.provision.detector.client.rpc.DetectorClientRpcModule;
import org.opennms.netmgt.provision.detector.registry.api.ServiceDetectorRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Wires the Detector RPC module (module ID "Detect") and its
 * {@link ServiceDetectorRegistry}.
 *
 * <p>The {@link ServiceDetectorRegistry} is provided by
 * {@link DetectorRegistryConfiguration}, which registers explicit detector factory
 * beans. Since Minion receives SNMP config via RPC request attributes, we supply
 * a {@link NoOpSnmpAgentConfigFactory} to satisfy the SNMP detector factory
 * constructor argument.</p>
 */
@Configuration
@Import(DetectorRegistryConfiguration.class)
@ConditionalOnProperty(name = "opennms.minion.detector.enabled", havingValue = "true", matchIfMissing = true)
public class DetectorConfiguration {

    @Bean
    public SnmpAgentConfigFactory snmpAgentConfigFactory() {
        return new NoOpSnmpAgentConfigFactory();
    }

    @Bean
    public Executor scanExecutor() {
        return Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "detector-rpc");
            t.setDaemon(true);
            return t;
        });
    }

    @Bean
    public DetectorClientRpcModule detectorClientRpcModule(ServiceDetectorRegistry serviceDetectorRegistry,
                                                            @org.springframework.beans.factory.annotation.Qualifier("scanExecutor") Executor scanExecutor) {
        DetectorClientRpcModule module = new DetectorClientRpcModule();
        module.setServiceDetectorRegistry(serviceDetectorRegistry);
        module.setExecutor(scanExecutor);
        return module;
    }
}
