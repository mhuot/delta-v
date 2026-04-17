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
package org.deltav.netmgt.provision.nodecontext;

import io.micrometer.core.instrument.MeterRegistry;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.dao.api.SessionUtils;
import org.opennms.netmgt.events.api.AnnotationBasedEventListenerAdapter;
import org.opennms.netmgt.events.api.EventSubscriptionService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Feature-flagged configuration for the provisiond → deltav-node-context producer.
 *
 * <p>When {@code deltav.node-context.enabled=true} (default — the flag is
 * matchIfMissing=true so the feature is on unless explicitly disabled), the
 * seven producer beans (translator, publisher, debouncer, listener,
 * event-subscription adapter, bootstrap runner, NewTopic) wire up and
 * provisiond starts publishing on every node-lifecycle UEI plus one
 * bootstrap pass.</p>
 */
@Configuration
@ConditionalOnProperty(name = "deltav.node-context.enabled", havingValue = "true", matchIfMissing = true)
public class NodeContextProducerConfiguration {

    @Bean
    public NodeToProtobufTranslator nodeToProtobufTranslator() {
        return new NodeToProtobufTranslator();
    }

    @Bean
    public NodeContextPublisher nodeContextPublisher(StreamBridge streamBridge,
                                                     NodeDao nodeDao,
                                                     SessionUtils sessionUtils,
                                                     NodeToProtobufTranslator translator,
                                                     MeterRegistry meterRegistry) {
        return new NodeContextPublisher(streamBridge, nodeDao, sessionUtils, translator, meterRegistry);
    }

    @Bean
    public NodeContextDebouncer nodeContextDebouncer(
            NodeContextPublisher publisher,
            @Value("${deltav.node-context.debounce-ms:250}") long debounceMs,
            @Value("${deltav.node-context.debounce-threads:2}") int debounceThreads,
            MeterRegistry meterRegistry) {
        return new NodeContextDebouncer(publisher, debounceMs, debounceThreads, meterRegistry);
    }

    @Bean
    public NodeContextChangeFeedListener nodeContextChangeFeedListener(
            NodeContextDebouncer debouncer,
            NodeContextPublisher publisher,
            NodeDao nodeDao,
            MeterRegistry meterRegistry) {
        return new NodeContextChangeFeedListener(debouncer, publisher, nodeDao, meterRegistry);
    }

    @Bean
    public AnnotationBasedEventListenerAdapter nodeContextChangeFeedEventListener(
            NodeContextChangeFeedListener listener,
            @Qualifier("kafkaEventSubscriptionService") EventSubscriptionService eventSubscriptionService) {
        AnnotationBasedEventListenerAdapter adapter = new AnnotationBasedEventListenerAdapter();
        adapter.setAnnotatedListener(listener);
        adapter.setEventSubscriptionService(eventSubscriptionService);
        return adapter;
    }

    @Bean
    public NodeContextBootstrapRunner nodeContextBootstrapRunner(
            NodeContextPublisher publisher,
            NodeDao nodeDao,
            SessionUtils sessionUtils,
            MeterRegistry meterRegistry) {
        return new NodeContextBootstrapRunner(publisher, nodeDao, sessionUtils, meterRegistry);
    }

    /**
     * Moved from daemon-boot-collectd/TimeseriesKafkaPublisherConfiguration.
     * {@code KafkaAdmin.createTopics} is idempotent so the handoff is race-free.
     */
    @Bean
    public NewTopic deltavNodeContextTopic(
            @Value("${deltav.node-context.partitions:8}") int partitions,
            @Value("${deltav.node-context.replication-factor:1}") short replicationFactor) {
        return TopicBuilder.name("deltav-node-context")
                .partitions(partitions)
                .replicas(replicationFactor)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT)
                .config(TopicConfig.RETENTION_MS_CONFIG, "-1")
                .config(TopicConfig.MIN_COMPACTION_LAG_MS_CONFIG, "60000")
                .config(TopicConfig.DELETE_RETENTION_MS_CONFIG, "86400000")
                .build();
    }
}
