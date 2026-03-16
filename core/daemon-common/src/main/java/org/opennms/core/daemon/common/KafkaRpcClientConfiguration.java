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
package org.opennms.core.daemon.common;

import com.codahale.metrics.MetricRegistry;

import org.opennms.core.ipc.rpc.kafka.KafkaRpcClientFactory;
import org.opennms.core.rpc.utils.RpcTargetHelper;
import org.opennms.core.tracing.api.TracerRegistry;
import org.opennms.netmgt.dao.api.DistPollerDao;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring {@link Configuration} for the Kafka RPC client infrastructure.
 *
 * <p>Shared by all daemons that send Kafka RPC requests to Minions:
 * Discovery, Pollerd, Collectd, Enlinkd, PerspectivePoller, Provisiond.</p>
 *
 * <p>Replaces the shared {@code kafka-rpc-client-factory.xml} used by
 * Karaf daemon loaders.</p>
 *
 * <p>{@code KafkaRpcClientFactory} reads bootstrap servers from the system
 * property {@code org.opennms.core.ipc.rpc.kafka.bootstrap.servers}.
 * The Docker command must include
 * {@code -Dorg.opennms.core.ipc.rpc.kafka.bootstrap.servers=kafka:9092}.</p>
 */
@Configuration
@ConditionalOnProperty(name = "opennms.rpc.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class KafkaRpcClientConfiguration {

    @Bean
    public TracerRegistry tracerRegistry() {
        return new NoOpTracerRegistry();
    }

    @Bean
    public MetricRegistry kafkaRpcMetricRegistry() {
        return new MetricRegistry();
    }

    @Bean
    public RpcTargetHelper rpcTargetHelper() {
        return new RpcTargetHelper();
    }

    /**
     * Conditional on {@code opennms.rpc.kafka.enabled=true} to avoid breaking
     * daemons that don't use RPC (Trapd, Syslogd). Without the system property
     * {@code org.opennms.core.ipc.rpc.kafka.bootstrap.servers}, the
     * KafkaProducer constructor in {@code start()} will throw.
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnProperty(name = "opennms.rpc.kafka.enabled", havingValue = "true", matchIfMissing = false)
    public KafkaRpcClientFactory rpcClientFactory(DistPollerDao distPollerDao,
                                                   MetricRegistry kafkaRpcMetricRegistry) {
        var factory = new KafkaRpcClientFactory();
        factory.setLocation(distPollerDao.whoami().getLocation());
        factory.setMetrics(kafkaRpcMetricRegistry);
        return factory;
    }
}
