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

import org.opennms.core.event.forwarder.kafka.KafkaEventForwarder;
import org.opennms.core.event.forwarder.kafka.KafkaEventForwarderFactory;
import org.opennms.core.event.forwarder.kafka.KafkaEventIpcManagerAdapter;
import org.opennms.core.event.forwarder.kafka.KafkaEventSubscriptionService;
import org.opennms.netmgt.events.api.EventIpcManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring {@link Configuration} that replaces the OSGi Blueprint
 * {@code blueprint-event-forwarder-kafka.xml} for Spring Boot daemon containers.
 *
 * <p>Creates the Kafka-backed event transport stack:</p>
 * <ol>
 *   <li>{@link KafkaEventForwarder} — publishes events to Kafka topics</li>
 *   <li>{@link KafkaEventSubscriptionService} — consumes events from Kafka topics
 *       and dispatches to registered listeners</li>
 *   <li>{@link KafkaEventIpcManagerAdapter} — composes forwarder + subscription
 *       into the {@link EventIpcManager} interface expected by daemon code</li>
 * </ol>
 *
 * <p>The forwarder's event expander uses {@code NoOpEventProcessor} (the legacy Eventd
 * expansion pipeline is not available). Instead, eventconf enrichment (alarm-data,
 * severity, reduction-key expansion) is handled by {@link EventConfEnrichmentService}
 * which loads event configurations from the database. This enrichment is wired into
 * the forwarder via {@code setEventConfDao()} so ALL events from ALL daemons are
 * enriched before reaching Kafka.</p>
 */
@Configuration
public class KafkaEventTransportConfiguration {

    @Value("${opennms.kafka.bootstrap-servers:kafka:9092}")
    private String bootstrapServers;

    @Value("${opennms.kafka.event-topic:opennms-fault-events}")
    private String eventTopic;

    @Value("${opennms.kafka.ipc-topic:opennms-ipc-events}")
    private String ipcTopic;

    @Value("${opennms.kafka.consumer-group:opennms-core}")
    private String consumerGroup;

    @Value("${opennms.kafka.poll-timeout-ms:100}")
    private long pollTimeoutMs;

    @Bean
    public KafkaEventForwarder kafkaEventForwarder(
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            EventConfEnrichmentService eventConfEnrichmentService) {
        KafkaEventForwarder forwarder = KafkaEventForwarderFactory.create(bootstrapServers, eventTopic);
        forwarder.setIpcTopicName(ipcTopic);
        if (eventConfEnrichmentService != null) {
            forwarder.setEventConfDao(eventConfEnrichmentService.getEventConfDao());
        }
        return forwarder;
    }

    @Bean(destroyMethod = "stop")
    public KafkaEventSubscriptionService kafkaEventSubscriptionService() {
        return KafkaEventSubscriptionService.create(
                bootstrapServers,
                consumerGroup,
                eventTopic + "," + ipcTopic,
                pollTimeoutMs);
    }

    @Bean
    @org.springframework.context.annotation.Primary
    public EventIpcManager eventIpcManager(KafkaEventForwarder forwarder,
                                           KafkaEventSubscriptionService subscriptionService) {
        return new KafkaEventIpcManagerAdapter(forwarder, subscriptionService);
    }

    /**
     * Starts the Kafka event consumer AFTER all InitializingBean callbacks
     * have fired (i.e., after AnnotationBasedEventListenerAdapter has
     * registered its listeners). SmartLifecycle runs after bean init but
     * before the application is considered started. Phase -10 ensures this
     * fires before daemon SmartLifecycles at the default phase (0).
     */
    @Bean
    public SmartLifecycle kafkaEventSubscriptionLifecycle(KafkaEventSubscriptionService subscriptionService) {
        return new SmartLifecycle() {
            private volatile boolean running = false;

            @Override
            public void start() {
                subscriptionService.start();
                running = true;
            }

            @Override
            public void stop() {
                running = false;
            }

            @Override
            public boolean isRunning() {
                return running;
            }

            @Override
            public int getPhase() {
                return -10;
            }
        };
    }
}
