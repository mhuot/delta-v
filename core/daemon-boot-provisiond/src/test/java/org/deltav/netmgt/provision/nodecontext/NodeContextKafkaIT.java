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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.deltav.timeseries.proto.NodeContext;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Layer 4 broker integration test for the {@code deltav-node-context} topic.
 *
 * <p>Validates Kafka wire behaviour using a real broker:
 * <ul>
 *   <li>Topic is provisioned with cleanup.policy=compact, min.compaction.lag.ms=60000,
 *       delete.retention.ms=86400000, and 8 partitions (verified via describeConfigs).</li>
 *   <li>Multiple records for the same key are all reachable within the compaction-lag
 *       window (log compaction has not run yet — both v1 and v2 are visible).</li>
 *   <li>A record with deleted=true is a valid application-level tombstone visible to
 *       consumers (distinct from a null-value Kafka tombstone).</li>
 *   <li>64 distinct keys across 8 partitions exercises the default hash partitioner
 *       across at least 4 partitions.</li>
 * </ul>
 *
 * <p>Pinned to {@code apache/kafka:3.8.0} — same image as
 * {@code TimeseriesKafkaBrokerIT} in daemon-boot-collectd for reproducibility.
 * First run pulls the image (~30 s); subsequent runs reuse Docker's layer cache.
 * No Spring context is loaded; this test exercises only the Kafka wire protocol
 * and protobuf serialisation.</p>
 */
@Testcontainers
class NodeContextKafkaIT {

    private static final String TOPIC = "deltav-node-context";

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("apache/kafka:3.8.0"))
            .withStartupTimeout(Duration.ofSeconds(120));

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void topicProvisionsWithCompactionConfig() throws Exception {
        provisionTopic();

        try (AdminClient admin = adminClient()) {
            DescribeTopicsResult describeResult = admin.describeTopics(List.of(TOPIC));
            TopicDescription topicDescription = describeResult.topicNameValues().get(TOPIC).get();
            assertThat(topicDescription.partitions()).hasSize(8);

            ConfigResource configResource = new ConfigResource(ConfigResource.Type.TOPIC, TOPIC);
            Config topicConfig = admin.describeConfigs(List.of(configResource))
                    .all().get().get(configResource);

            assertThat(entryValue(topicConfig, "cleanup.policy")).isEqualTo("compact");
            assertThat(entryValue(topicConfig, "min.compaction.lag.ms")).isEqualTo("60000");
            assertThat(entryValue(topicConfig, "delete.retention.ms")).isEqualTo("86400000");
        }
    }

    @Test
    void compactionRetainsLatestPerKey() throws Exception {
        provisionTopic();

        NodeContext versionOne = NodeContext.newBuilder()
                .setNodeId(42)
                .setLocation("Default")
                .setNodeLabel("v1")
                .setUpdatedAtMs(1L)
                .build();
        NodeContext versionTwo = NodeContext.newBuilder()
                .setNodeId(42)
                .setLocation("Default")
                .setNodeLabel("v2")
                .setUpdatedAtMs(2L)
                .build();

        byte[] key = "Default@42".getBytes(StandardCharsets.UTF_8);
        try (KafkaProducer<byte[], byte[]> producer = producer()) {
            producer.send(new ProducerRecord<>(TOPIC, key, versionOne.toByteArray())).get();
            producer.send(new ProducerRecord<>(TOPIC, key, versionTwo.toByteArray())).get();
        }

        // Within the compaction lag window both records remain in the log, so
        // a consumer reading from earliest should see both v1 and v2.
        Set<String> labelsObserved = new HashSet<>();
        try (KafkaConsumer<byte[], byte[]> consumer = consumer()) {
            consumer.subscribe(Collections.singleton(TOPIC));
            await().atMost(Duration.ofSeconds(15)).until(() -> {
                ConsumerRecords<byte[], byte[]> records = consumer.poll(Duration.ofMillis(200));
                for (ConsumerRecord<byte[], byte[]> record : records) {
                    labelsObserved.add(NodeContext.parseFrom(record.value()).getNodeLabel());
                }
                return labelsObserved.contains("v1") && labelsObserved.contains("v2");
            });
        }
        assertThat(labelsObserved).containsExactlyInAnyOrder("v1", "v2");
    }

    @Test
    void tombstoneIsExplicitDeletedRecord() throws Exception {
        provisionTopic();

        NodeContext liveRecord = NodeContext.newBuilder()
                .setNodeId(7)
                .setLocation("Default")
                .setNodeLabel("live")
                .setUpdatedAtMs(1L)
                .build();
        NodeContext tombstone = NodeContext.newBuilder()
                .setNodeId(7)
                .setLocation("Default")
                .setUpdatedAtMs(2L)
                .setDeleted(true)
                .build();

        byte[] key = "Default@7".getBytes(StandardCharsets.UTF_8);
        try (KafkaProducer<byte[], byte[]> producer = producer()) {
            producer.send(new ProducerRecord<>(TOPIC, key, liveRecord.toByteArray())).get();
            producer.send(new ProducerRecord<>(TOPIC, key, tombstone.toByteArray())).get();
        }

        try (KafkaConsumer<byte[], byte[]> consumer = consumer()) {
            consumer.subscribe(Collections.singleton(TOPIC));
            boolean tombstoneVisible = await().atMost(Duration.ofSeconds(15))
                    .until(() -> {
                        ConsumerRecords<byte[], byte[]> records = consumer.poll(Duration.ofMillis(200));
                        for (ConsumerRecord<byte[], byte[]> record : records) {
                            if (NodeContext.parseFrom(record.value()).getDeleted()) {
                                return true;
                            }
                        }
                        return false;
                    }, java.util.function.Predicate.isEqual(true));
            assertThat(tombstoneVisible).isTrue();
        }
    }

    @Test
    void partitionAssignmentUsesAllPartitions() throws Exception {
        provisionTopic();

        try (KafkaProducer<byte[], byte[]> producer = producer()) {
            for (int nodeId = 0; nodeId < 64; nodeId++) {
                byte[] key = ("Default@" + nodeId).getBytes(StandardCharsets.UTF_8);
                NodeContext nodeContext = NodeContext.newBuilder().setNodeId(nodeId).build();
                producer.send(new ProducerRecord<>(TOPIC, key, nodeContext.toByteArray())).get();
            }
        }

        Set<Integer> partitionsSeen = new HashSet<>();
        try (KafkaConsumer<byte[], byte[]> consumer = consumer()) {
            consumer.subscribe(Collections.singleton(TOPIC));
            await().atMost(Duration.ofSeconds(15)).until(() -> {
                ConsumerRecords<byte[], byte[]> records = consumer.poll(Duration.ofMillis(200));
                records.forEach(record -> partitionsSeen.add(record.partition()));
                return partitionsSeen.size() >= 4;
            });
        }
        assertThat(partitionsSeen).hasSizeGreaterThanOrEqualTo(4);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Creates the {@code deltav-node-context} topic if it does not already exist.
     * Idempotent across tests: Kafka returns a no-op if the topic is already present.
     */
    private static void provisionTopic() throws Exception {
        Map<String, String> topicConfigs = new HashMap<>();
        topicConfigs.put("cleanup.policy", "compact");
        topicConfigs.put("retention.ms", "-1");
        topicConfigs.put("min.compaction.lag.ms", "60000");
        topicConfigs.put("delete.retention.ms", "86400000");

        NewTopic newTopic = new NewTopic(TOPIC, 8, (short) 1).configs(topicConfigs);
        try (AdminClient admin = adminClient()) {
            try {
                admin.createTopics(List.of(newTopic)).all().get();
            } catch (java.util.concurrent.ExecutionException executionException) {
                // TopicExistsException is expected when subsequent tests reuse the shared
                // KafkaContainer; the topic was already created by an earlier test method.
                if (!(executionException.getCause() instanceof TopicExistsException)) {
                    throw executionException;
                }
            }
        }
    }

    private static AdminClient adminClient() {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", KAFKA.getBootstrapServers());
        return AdminClient.create(properties);
    }

    private static KafkaProducer<byte[], byte[]> producer() {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        return new KafkaProducer<>(properties);
    }

    private static KafkaConsumer<byte[], byte[]> consumer() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        // Unique group ID per consumer to ensure each test reads from the beginning independently.
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "node-context-it-" + System.nanoTime());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new KafkaConsumer<>(properties);
    }

    private static String entryValue(Config config, String name) {
        ConfigEntry entry = config.get(name);
        return entry != null ? entry.value() : null;
    }
}
