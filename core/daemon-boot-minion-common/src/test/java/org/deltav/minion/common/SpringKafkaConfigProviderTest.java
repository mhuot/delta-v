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
package org.deltav.minion.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.junit.jupiter.api.Test;

class SpringKafkaConfigProviderTest {

    @Test
    void returnsConfiguredProperties() {
        Properties input = new Properties();
        input.setProperty("bootstrap.servers", "kafka:9092");

        SpringKafkaConfigProvider provider = new SpringKafkaConfigProvider(input);

        Properties result = provider.getProperties();
        assertThat(result.getProperty("bootstrap.servers")).isEqualTo("kafka:9092");
    }

    @Test
    void returnsDefensiveCopy() {
        Properties input = new Properties();
        input.setProperty("bootstrap.servers", "kafka:9092");

        SpringKafkaConfigProvider provider = new SpringKafkaConfigProvider(input);

        Properties firstCopy = provider.getProperties();
        firstCopy.setProperty("bootstrap.servers", "tampered:9999");

        Properties secondCopy = provider.getProperties();
        assertThat(secondCopy.getProperty("bootstrap.servers")).isEqualTo("kafka:9092");
    }
}
