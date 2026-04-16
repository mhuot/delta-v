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
package org.deltav.collectd.timeseries;

import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.opennms.netmgt.collection.api.AttributeGroup;
import org.opennms.netmgt.collection.api.CollectionAttribute;
import org.opennms.netmgt.collection.api.CollectionResource;
import org.opennms.netmgt.collection.api.CollectionSet;
import org.opennms.netmgt.collection.api.Persister;
import org.opennms.netmgt.collection.api.PersisterFactory;
import org.opennms.netmgt.collection.api.ServiceParameters;
import org.opennms.netmgt.rrd.RrdRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Feature-flagged configuration for the Kafka Time Series producer. When
 * {@code deltav.timeseries.enabled=true}, publishes one TimeseriesBatch
 * protobuf record per CollectionSet poll to the deltav-timeseries topic.
 * When the flag is false (default), none of the beans are created and the
 * persister chain stays on the existing InMemoryStorage-backed
 * TimeseriesPersisterFactory path.
 */
@Configuration
@ConditionalOnProperty(name = "deltav.timeseries.enabled", havingValue = "true")
public class TimeseriesKafkaPublisherConfiguration {

    @Bean
    public CollectionSetToProtobufTranslator collectionSetToProtobufTranslator() {
        return new CollectionSetToProtobufTranslator();
    }

    @Bean
    public TimeseriesKafkaPublisher timeseriesKafkaPublisher(
            StreamBridge streamBridge,
            CollectionSetToProtobufTranslator translator,
            MeterRegistry meterRegistry) {
        return new TimeseriesKafkaPublisher(streamBridge, translator, meterRegistry);
    }

    @Bean
    public NewTopic deltavTimeseriesTopic(
            @Value("${deltav.timeseries.partitions:16}") int partitions,
            @Value("${deltav.timeseries.replication-factor:1}") short replicationFactor,
            @Value("${deltav.timeseries.retention-days:7}") int retentionDays) {
        return TopicBuilder.name("deltav-timeseries")
                .partitions(partitions)
                .replicas(replicationFactor)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .config(TopicConfig.RETENTION_MS_CONFIG,
                        String.valueOf(Duration.ofDays(retentionDays).toMillis()))
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "lz4")
                .build();
    }

    /**
     * Initial dual-ownership per design doc: the deltav-node-context topic's
     * producer is provisiond, shipping in a separate future PR. The NewTopic
     * bean is declared here now so the topic is provisioned on first start;
     * when provisiond's change-feed PR lands, this bean moves to that module
     * and is deleted from here.
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

    /**
     * Composite factory that fans out each createPersister() call to both the
     * existing InMemoryStorage-backed TimeseriesPersisterFactory and a fresh
     * TimeseriesKafkaPersister. Declared @Primary so Collectd's constructor
     * resolves to this bean when the feature flag is on.
     */
    @Bean
    @Primary
    public PersisterFactory compositePersisterFactory(
            @Qualifier("timeseriesPersisterFactory") PersisterFactory innerFactory,
            TimeseriesKafkaPublisher publisher) {
        return new FanoutPersisterFactory(innerFactory, publisher);
    }

    /**
     * Package-private composite factory. Kept as a static nested class so the
     * public API of this module remains the four files listed in the spec.
     */
    static final class FanoutPersisterFactory implements PersisterFactory {
        private final PersisterFactory innerFactory;
        private final TimeseriesKafkaPublisher publisher;

        FanoutPersisterFactory(PersisterFactory innerFactory, TimeseriesKafkaPublisher publisher) {
            this.innerFactory = innerFactory;
            this.publisher = publisher;
        }

        @Override
        public Persister createPersister(ServiceParameters params, RrdRepository repository) {
            return new FanoutPersister(
                    innerFactory.createPersister(params, repository),
                    new TimeseriesKafkaPersister(publisher, params));
        }

        @Override
        public Persister createPersister(ServiceParameters params, RrdRepository repository,
                                         boolean dontPersistCounters, boolean forceStoreByGroup,
                                         boolean dontReorderAttributes) {
            return new FanoutPersister(
                    innerFactory.createPersister(params, repository, dontPersistCounters,
                            forceStoreByGroup, dontReorderAttributes),
                    new TimeseriesKafkaPersister(publisher, params));
        }
    }

    /**
     * Forwards each visitor callback to both delegate persisters, inner first
     * then Kafka. If the inner persister throws unexpectedly, the Kafka path
     * is skipped for that poll cycle — this is an accepted tradeoff for
     * Phase 0 because the inner TimeseriesPersisterFactory is assumed
     * well-behaved. The Kafka side's own error paths are isolated inside
     * {@link TimeseriesKafkaPublisher#publish} and never propagate up.
     */
    static final class FanoutPersister implements Persister {
        private final Persister innerPersister;
        private final TimeseriesKafkaPersister kafkaPersister;

        FanoutPersister(Persister innerPersister, TimeseriesKafkaPersister kafkaPersister) {
            this.innerPersister = innerPersister;
            this.kafkaPersister = kafkaPersister;
        }

        @Override
        public void visitCollectionSet(CollectionSet s) {
            innerPersister.visitCollectionSet(s);
            kafkaPersister.visitCollectionSet(s);
        }

        @Override
        public void visitResource(CollectionResource r) {
            innerPersister.visitResource(r);
            kafkaPersister.visitResource(r);
        }

        @Override
        public void visitGroup(AttributeGroup g) {
            innerPersister.visitGroup(g);
            kafkaPersister.visitGroup(g);
        }

        @Override
        public void visitAttribute(CollectionAttribute a) {
            innerPersister.visitAttribute(a);
            kafkaPersister.visitAttribute(a);
        }

        @Override
        public void completeAttribute(CollectionAttribute a) {
            innerPersister.completeAttribute(a);
            kafkaPersister.completeAttribute(a);
        }

        @Override
        public void completeGroup(AttributeGroup g) {
            innerPersister.completeGroup(g);
            kafkaPersister.completeGroup(g);
        }

        @Override
        public void completeResource(CollectionResource r) {
            innerPersister.completeResource(r);
            kafkaPersister.completeResource(r);
        }

        @Override
        public void completeCollectionSet(CollectionSet s) {
            innerPersister.completeCollectionSet(s);
            kafkaPersister.completeCollectionSet(s);
        }

        @Override
        public void persistNumericAttribute(CollectionAttribute a) {
            innerPersister.persistNumericAttribute(a);
            kafkaPersister.persistNumericAttribute(a);
        }

        @Override
        public void persistStringAttribute(CollectionAttribute a) {
            innerPersister.persistStringAttribute(a);
            kafkaPersister.persistStringAttribute(a);
        }
    }
}
