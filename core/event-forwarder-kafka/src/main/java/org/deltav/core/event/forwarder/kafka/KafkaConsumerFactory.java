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
package org.deltav.core.event.forwarder.kafka;

import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.LongDeserializer;

/**
 * Factory for creating {@link KafkaConsumer} instances configured for the
 * event-forwarder-kafka module.
 *
 * <p>Uses {@link LongDeserializer} for keys (event node IDs) and
 * {@link ByteArrayDeserializer} for values (XML-serialized event payloads).</p>
 */
public class KafkaConsumerFactory {

    private KafkaConsumerFactory() {
        // static factory — prevent instantiation
    }

    /**
     * Builds Kafka consumer properties for the given bootstrap servers and consumer group.
     *
     * @param bootstrapServers comma-separated list of Kafka broker addresses
     * @param groupId          the consumer group ID
     * @return configured {@link Properties} for a {@link KafkaConsumer}
     */
    public static Properties buildProperties(String bootstrapServers, String groupId) {
        Properties props = new Properties();
        props.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, LongDeserializer.class.getName());
        props.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        props.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        return props;
    }

    /**
     * Creates a new {@link KafkaConsumer} connected to the given bootstrap servers.
     * Uses direct deserializer instances instead of class names to avoid OSGi
     * classloading issues with {@code Class.forName()} in Karaf.
     *
     * @param bootstrapServers comma-separated list of Kafka broker addresses
     * @param groupId          the consumer group ID
     * @return a new {@link KafkaConsumer} instance; caller is responsible for closing it
     */
    public static KafkaConsumer<Long, byte[]> create(String bootstrapServers, String groupId) {
        Properties props = new Properties();
        props.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        return new KafkaConsumer<>(props, new LongDeserializer(), new ByteArrayDeserializer());
    }
}
