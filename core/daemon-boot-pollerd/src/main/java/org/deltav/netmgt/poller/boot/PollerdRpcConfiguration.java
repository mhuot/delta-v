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
package org.deltav.netmgt.poller.boot;

import org.deltav.core.daemon.registry.MonitorRegistryConfiguration;
import org.opennms.core.mate.api.EntityScopeProvider;
import org.opennms.core.rpc.api.RpcClientFactory;
import org.opennms.core.rpc.utils.RpcTargetHelper;
import org.opennms.netmgt.icmp.Pinger;
import org.opennms.netmgt.icmp.PingerFactory;
import org.opennms.netmgt.icmp.proxy.LocationAwarePingClient;
import org.opennms.netmgt.icmp.proxy.LocationAwarePingClientImpl;
import org.opennms.netmgt.icmp.proxy.PingProxyRpcModule;
import org.opennms.netmgt.icmp.proxy.PingSweepRpcModule;
import org.opennms.netmgt.poller.LocationAwarePollerClient;
import org.opennms.netmgt.poller.ServiceMonitorRegistry;
import org.opennms.netmgt.poller.client.rpc.LocationAwarePollerClientImpl;
import org.opennms.netmgt.poller.client.rpc.PollerClientRpcModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Spring Boot configuration for Pollerd's Kafka RPC clients.
 *
 * <p>Sets up the RPC modules and location-aware clients that allow Pollerd
 * to delegate poll execution and ICMP pings to Minion via Kafka RPC.</p>
 *
 * <p>{@link RpcClientFactory} and {@link RpcTargetHelper} are provided by
 * {@code KafkaRpcClientConfiguration} in daemon-common (auto-scanned via
 * {@code org.deltav.core.daemon.common}). {@link EntityScopeProvider} is
 * provided by {@code DaemonProvisioningConfiguration} as a no-op default.</p>
 */
@Configuration
@Import(MonitorRegistryConfiguration.class)
public class PollerdRpcConfiguration {

    /**
     * Executor for PollerClientRpcModule async response handling.
     * Satisfies the @Autowired @Qualifier("pollerExecutor") on PollerClientRpcModule.
     */
    @Bean(name = "pollerExecutor")
    public Executor pollerExecutor() {
        return Executors.newCachedThreadPool();
    }

    /**
     * No-op PingerFactory — Pollerd never pings locally, it delegates to Minion.
     * This bean satisfies the @Autowired PingerFactory field on PingProxyRpcModule.
     */
    @Bean
    public PingerFactory pingerFactory() {
        return new PingerFactory() {
            @Override public Pinger getInstance() { return null; }
            @Override public Pinger getInstance(int tc, boolean allowFragmentation) { return null; }
        };
    }

    /**
     * Stateless RPC protocol definition for poller requests to Minion.
     */
    @Bean
    public PollerClientRpcModule pollerClientRpcModule() {
        return new PollerClientRpcModule();
    }

    /**
     * Stateless RPC module for ICMP ping requests.
     */
    @Bean
    public PingProxyRpcModule pingProxyRpcModule() {
        return new PingProxyRpcModule();
    }

    /**
     * Stateless RPC module for ICMP ping sweep requests.
     */
    @Bean
    public PingSweepRpcModule pingSweepRpcModule() {
        return new PingSweepRpcModule();
    }

    /**
     * Location-aware poller client that delegates poll execution to Minion.
     *
     * <p>{@link LocationAwarePollerClientImpl} implements {@link org.springframework.beans.factory.InitializingBean},
     * so Spring will call {@code afterPropertiesSet()} automatically after construction,
     * which creates the RPC delegate client.</p>
     */
    @Bean
    public LocationAwarePollerClient locationAwarePollerClient(
            ServiceMonitorRegistry serviceMonitorRegistry,
            PollerClientRpcModule pollerClientRpcModule,
            RpcClientFactory rpcClientFactory,
            RpcTargetHelper rpcTargetHelper,
            EntityScopeProvider entityScopeProvider) {
        return new LocationAwarePollerClientImpl(serviceMonitorRegistry,
                pollerClientRpcModule, rpcClientFactory, rpcTargetHelper, entityScopeProvider);
    }

    /**
     * Location-aware ping client that delegates ICMP pings and sweeps to Minion.
     *
     * <p>{@link LocationAwarePingClientImpl} uses {@code @PostConstruct} to initialize
     * the RPC delegate clients after construction.</p>
     */
    @Bean
    public LocationAwarePingClient locationAwarePingClient(
            RpcClientFactory rpcClientFactory,
            PingProxyRpcModule pingProxyRpcModule,
            PingSweepRpcModule pingSweepRpcModule) {
        return new LocationAwarePingClientImpl(rpcClientFactory, pingProxyRpcModule, pingSweepRpcModule);
    }
}
