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
package org.opennms.netmgt.poller.boot;

import javax.sql.DataSource;

import com.codahale.metrics.MetricRegistry;

import org.opennms.core.daemon.loader.InlineIdentity;
import org.opennms.core.ipc.twin.common.LocalTwinSubscriberImpl;
import org.opennms.core.ipc.twin.kafka.publisher.KafkaTwinPublisher;
import org.opennms.core.tracing.api.TracerRegistry;
import org.opennms.distributed.core.api.Identity;
import org.opennms.netmgt.events.api.EventIpcManager;
import org.opennms.netmgt.passive.PassiveStatusKeeper;
import org.opennms.netmgt.passive.PassiveStatusTwinPublisher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot configuration for passive status monitoring and Twin API publishing.
 *
 * <p>Wires the passive status subsystem so that Pollerd receives
 * {@code passiveServiceStatus} events via Kafka, {@link PassiveStatusKeeper}
 * tracks the state, and {@link PassiveStatusTwinPublisher} pushes updates
 * to Minion via {@link KafkaTwinPublisher}.</p>
 *
 * <p>Mirrors the bean definitions from the legacy Karaf XML context
 * {@code applicationContext-daemon-loader-pollerd.xml}.</p>
 */
@Configuration
public class PollerdPassiveStatusConfiguration {

    @Bean(initMethod = "init", destroyMethod = "stop")
    public PassiveStatusKeeper passiveStatusKeeper(
            EventIpcManager eventIpcManager,
            DataSource dataSource) {
        var keeper = new PassiveStatusKeeper();
        keeper.setEventManager(eventIpcManager);
        keeper.setDataSource(dataSource);
        PassiveStatusKeeper.setInstance(keeper);
        return keeper;
    }

    @Bean
    public Identity twinIdentity() {
        return new InlineIdentity();
    }

    @Bean("twinPublisherMetricRegistry")
    public MetricRegistry twinPublisherMetricRegistry() {
        return new MetricRegistry();
    }

    @Bean
    public LocalTwinSubscriberImpl localTwinSubscriber(Identity twinIdentity) {
        return new LocalTwinSubscriberImpl(twinIdentity);
    }

    @Bean(initMethod = "init", destroyMethod = "close")
    public KafkaTwinPublisher kafkaTwinPublisher(
            LocalTwinSubscriberImpl localTwinSubscriber,
            TracerRegistry tracerRegistry,
            @Qualifier("twinPublisherMetricRegistry") MetricRegistry metricRegistry) {
        return new KafkaTwinPublisher(localTwinSubscriber, tracerRegistry, metricRegistry);
    }

    @Bean(initMethod = "init", destroyMethod = "close")
    public PassiveStatusTwinPublisher passiveStatusTwinPublisher(
            KafkaTwinPublisher kafkaTwinPublisher,
            PassiveStatusKeeper passiveStatusKeeper,
            EventIpcManager eventIpcManager) {
        return new PassiveStatusTwinPublisher(kafkaTwinPublisher, passiveStatusKeeper, eventIpcManager);
    }
}
