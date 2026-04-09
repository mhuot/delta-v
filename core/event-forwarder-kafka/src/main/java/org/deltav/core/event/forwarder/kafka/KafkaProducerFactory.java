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

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.LongSerializer;

/**
 * Factory for creating {@link KafkaProducer} instances configured for the
 * event-forwarder-kafka module.
 *
 * <p>Uses {@link LongSerializer} for keys (event node IDs) and
 * {@link ByteArraySerializer} for values (XML-serialized event payloads).</p>
 */
public class KafkaProducerFactory {

    private KafkaProducerFactory() {
        // static factory — prevent instantiation
    }

    /**
     * Builds Kafka producer properties for the given bootstrap servers.
     *
     * @param bootstrapServers comma-separated list of Kafka broker addresses
     * @return configured {@link Properties} for a {@link KafkaProducer}
     */
    public static Properties buildProperties(String bootstrapServers) {
        Properties props = new Properties();
        props.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, LongSerializer.class.getName());
        props.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        props.setProperty(ProducerConfig.ACKS_CONFIG, "all");
        return props;
    }

    /**
     * Creates a new {@link KafkaProducer} connected to the given bootstrap servers.
     * Uses direct serializer instances instead of class names to avoid OSGi
     * classloading issues with {@code Class.forName()} in Karaf.
     *
     * @param bootstrapServers comma-separated list of Kafka broker addresses
     * @return a new {@link KafkaProducer} instance; caller is responsible for closing it
     */
    public static KafkaProducer<Long, byte[]> create(String bootstrapServers) {
        Properties props = new Properties();
        props.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.setProperty(ProducerConfig.ACKS_CONFIG, "all");
        return new KafkaProducer<>(props, new LongSerializer(), new ByteArraySerializer());
    }
}
