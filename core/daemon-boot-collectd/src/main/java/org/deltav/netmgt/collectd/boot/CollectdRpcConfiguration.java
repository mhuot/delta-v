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
package org.deltav.netmgt.collectd.boot;

import org.deltav.core.daemon.registry.CollectorRegistryConfiguration;
import org.opennms.core.mate.api.EntityScopeProvider;
import org.opennms.core.rpc.api.RpcClientFactory;
import org.opennms.core.rpc.utils.RpcTargetHelper;
import org.opennms.netmgt.collection.api.LocationAwareCollectorClient;
import org.opennms.netmgt.collection.api.ServiceCollectorRegistry;
import org.opennms.netmgt.collection.client.rpc.CollectorClientRpcModule;
import org.opennms.netmgt.collection.client.rpc.LocationAwareCollectorClientImpl;
import org.opennms.netmgt.snmp.proxy.LocationAwareSnmpClient;
import org.opennms.netmgt.snmp.proxy.common.LocationAwareSnmpClientRpcImpl;
import org.opennms.netmgt.snmp.proxy.common.SnmpProxyRpcModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Spring Boot configuration for Collectd's Kafka RPC clients.
 *
 * <p>Sets up the RPC modules and location-aware clients that allow Collectd
 * to delegate collection execution and SNMP walks to Minion via Kafka RPC.</p>
 *
 * <p>{@link RpcClientFactory} and {@link RpcTargetHelper} are provided by
 * {@code KafkaRpcClientConfiguration} in daemon-common (auto-scanned via
 * {@code org.deltav.core.daemon.common}). {@link EntityScopeProvider} is
 * provided by {@code DaemonProvisioningConfiguration} as a no-op default.</p>
 */
@Configuration
@Import(CollectorRegistryConfiguration.class)
public class CollectdRpcConfiguration {

    /**
     * Executor for CollectorClientRpcModule async response handling.
     * Satisfies the {@code @Autowired @Qualifier("collectorExecutor")} on
     * {@link CollectorClientRpcModule}.
     */
    @Bean(name = "collectorExecutor")
    public Executor collectorExecutor() {
        return Executors.newCachedThreadPool();
    }

    /**
     * Stateless RPC protocol definition for collector requests to Minion.
     *
     * <p>{@link CollectorClientRpcModule} uses {@code @Autowired} to receive
     * the {@link ServiceCollectorRegistry} and the {@code collectorExecutor}
     * after construction.</p>
     */
    @Bean
    public CollectorClientRpcModule collectorClientRpcModule() {
        return new CollectorClientRpcModule();
    }

    /**
     * Location-aware collector client that delegates collection execution to Minion.
     *
     * <p>{@link LocationAwareCollectorClientImpl} implements
     * {@link org.springframework.beans.factory.InitializingBean}, so Spring calls
     * {@code afterPropertiesSet()} automatically after {@code @Autowired} field
     * injection, which creates the RPC delegate client.</p>
     */
    @Bean
    public LocationAwareCollectorClient locationAwareCollectorClient() {
        return new LocationAwareCollectorClientImpl();
    }

    /**
     * Stateless RPC module for SNMP proxy requests through Minion.
     */
    @Bean
    public SnmpProxyRpcModule snmpProxyRpcModule() {
        return new SnmpProxyRpcModule();
    }

    /**
     * Location-aware SNMP client that delegates SNMP walks and gets to Minion.
     *
     * <p>{@link LocationAwareSnmpClientRpcImpl} implements
     * {@link org.springframework.beans.factory.InitializingBean}, so Spring calls
     * {@code afterPropertiesSet()} automatically after {@code @Autowired}
     * {@link RpcClientFactory} injection, which creates the RPC delegate client.</p>
     */
    @Bean
    public LocationAwareSnmpClient locationAwareSnmpClient() {
        return new LocationAwareSnmpClientRpcImpl();
    }
}
