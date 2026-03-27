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
package org.opennms.netmgt.collectd.boot;

import org.opennms.core.daemon.common.registry.LocalServiceCollectorRegistry;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
 * {@code org.opennms.core.daemon.common}). {@link EntityScopeProvider} is
 * provided by {@code DaemonProvisioningConfiguration} as a no-op default.</p>
 */
@Configuration
public class CollectdRpcConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(CollectdRpcConfiguration.class);

    /**
     * ServiceCollectorRegistry that discovers ServiceCollector implementations
     * via {@link java.util.ServiceLoader}.
     *
     * <p>After loading, injects the {@link LocationAwareSnmpClient} into any
     * {@link AbstractSnmpCollector} instances. In monolithic OpenNMS, the
     * collector obtains this via {@code BeanUtils.getBean("daoContext", ...)}
     * which relies on a legacy ApplicationContext that doesn't exist in
     * Spring Boot.</p>
     */
    @Bean
    public ServiceCollectorRegistry serviceCollectorRegistry(LocationAwareSnmpClient snmpClient) {
        LocalServiceCollectorRegistry registry = new LocalServiceCollectorRegistry();
        // Inject the SNMP client into any SNMP collectors loaded via ServiceLoader.
        // In monolithic OpenNMS, SnmpCollector obtains this via BeanUtils.getBean("daoContext", ...)
        // which doesn't work in Spring Boot. Pre-inject it here.
        for (String className : registry.getCollectorClassNames()) {
            var collector = registry.getCollectorFutureByClassName(className).getNow(null);
            if (collector != null) {
                try {
                    var method = collector.getClass().getMethod("setLocationAwareSnmpClient", LocationAwareSnmpClient.class);
                    method.invoke(collector, snmpClient);
                    LOG.info("Injected LocationAwareSnmpClient into {}", collector.getClass().getSimpleName());
                } catch (NoSuchMethodException e) {
                    // Not an SNMP collector — skip
                } catch (Exception e) {
                    LOG.warn("Failed to inject LocationAwareSnmpClient into {}", collector.getClass().getName(), e);
                }
            }
        }
        return registry;
    }

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
