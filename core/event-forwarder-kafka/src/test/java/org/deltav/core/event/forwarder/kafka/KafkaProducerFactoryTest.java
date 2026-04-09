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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.LongSerializer;
import org.junit.Test;

public class KafkaProducerFactoryTest {

    @Test
    public void buildPropertiesShouldSetBootstrapServers() {
        Properties props = KafkaProducerFactory.buildProperties("broker1:9092,broker2:9092");

        assertThat(props.getProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG))
                .isEqualTo("broker1:9092,broker2:9092");
    }

    @Test
    public void buildPropertiesShouldUseLongKeySerializer() {
        Properties props = KafkaProducerFactory.buildProperties("localhost:9092");

        assertThat(props.getProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG))
                .isEqualTo(LongSerializer.class.getName());
    }

    @Test
    public void buildPropertiesShouldUseByteArrayValueSerializer() {
        Properties props = KafkaProducerFactory.buildProperties("localhost:9092");

        assertThat(props.getProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG))
                .isEqualTo(ByteArraySerializer.class.getName());
    }

    @Test
    public void buildPropertiesShouldSetAcksToAll() {
        Properties props = KafkaProducerFactory.buildProperties("localhost:9092");

        assertThat(props.getProperty(ProducerConfig.ACKS_CONFIG))
                .isEqualTo("all");
    }

    @Test
    public void buildPropertiesShouldContainExactlyFourEntries() {
        Properties props = KafkaProducerFactory.buildProperties("localhost:9092");

        assertThat(props).hasSize(4);
    }
}
