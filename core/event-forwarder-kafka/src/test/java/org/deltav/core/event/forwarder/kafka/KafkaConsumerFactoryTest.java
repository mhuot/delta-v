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

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.junit.Test;

public class KafkaConsumerFactoryTest {

    @Test
    public void buildPropertiesShouldSetBootstrapServers() {
        Properties props = KafkaConsumerFactory.buildProperties("broker1:9092", "my-group");

        assertThat(props.getProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG))
                .isEqualTo("broker1:9092");
    }

    @Test
    public void buildPropertiesShouldSetGroupId() {
        Properties props = KafkaConsumerFactory.buildProperties("localhost:9092", "opennms-core");

        assertThat(props.getProperty(ConsumerConfig.GROUP_ID_CONFIG))
                .isEqualTo("opennms-core");
    }

    @Test
    public void buildPropertiesShouldUseLongKeyDeserializer() {
        Properties props = KafkaConsumerFactory.buildProperties("localhost:9092", "test-group");

        assertThat(props.getProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG))
                .isEqualTo(LongDeserializer.class.getName());
    }

    @Test
    public void buildPropertiesShouldUseByteArrayValueDeserializer() {
        Properties props = KafkaConsumerFactory.buildProperties("localhost:9092", "test-group");

        assertThat(props.getProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG))
                .isEqualTo(ByteArrayDeserializer.class.getName());
    }

    @Test
    public void buildPropertiesShouldSetAutoOffsetResetToEarliest() {
        Properties props = KafkaConsumerFactory.buildProperties("localhost:9092", "test-group");

        assertThat(props.getProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG))
                .isEqualTo("earliest");
    }

    @Test
    public void buildPropertiesShouldEnableAutoCommit() {
        Properties props = KafkaConsumerFactory.buildProperties("localhost:9092", "test-group");

        assertThat(props.getProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG))
                .isEqualTo("true");
    }

    @Test
    public void buildPropertiesShouldContainExactlySixEntries() {
        Properties props = KafkaConsumerFactory.buildProperties("localhost:9092", "test-group");

        assertThat(props).hasSize(6);
    }
}
