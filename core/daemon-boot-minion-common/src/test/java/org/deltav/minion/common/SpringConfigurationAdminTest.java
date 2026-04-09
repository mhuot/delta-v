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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Dictionary;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.osgi.service.cm.Configuration;

class SpringConfigurationAdminTest {

    @Test
    void getConfigurationReturnsSinkProperties() throws Exception {
        Properties kafkaProps = new Properties();
        kafkaProps.setProperty("bootstrap.servers", "kafka:9092");

        SpringConfigurationAdmin admin = new SpringConfigurationAdmin(kafkaProps);
        Configuration config = admin.getConfiguration("org.opennms.core.ipc.sink.kafka");

        Dictionary<String, Object> dict = config.getProperties();
        assertThat(dict.get("bootstrap.servers")).isEqualTo("kafka:9092");
    }

    @Test
    void anyPidReturnsSameProperties() throws Exception {
        Properties kafkaProps = new Properties();
        kafkaProps.setProperty("bootstrap.servers", "localhost:9092");

        SpringConfigurationAdmin admin = new SpringConfigurationAdmin(kafkaProps);

        assertThat(admin.getConfiguration("any.pid").getProperties().get("bootstrap.servers"))
            .isEqualTo("localhost:9092");
        assertThat(admin.getConfiguration("other.pid", "location").getProperties().get("bootstrap.servers"))
            .isEqualTo("localhost:9092");
    }

    @Test
    void getPidReturnsSpringKafka() throws Exception {
        SpringConfigurationAdmin admin = new SpringConfigurationAdmin(new Properties());
        assertThat(admin.getConfiguration("any.pid").getPid()).isEqualTo("spring-kafka");
    }

    @Test
    void createFactoryConfigurationThrowsUnsupported() {
        SpringConfigurationAdmin admin = new SpringConfigurationAdmin(new Properties());

        assertThatThrownBy(() -> admin.createFactoryConfiguration("any.factory"))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> admin.createFactoryConfiguration("any.factory", "location"))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> admin.getFactoryConfiguration("factory", "name"))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> admin.getFactoryConfiguration("factory", "name", "location"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void listConfigurationsReturnsSingleElement() throws Exception {
        SpringConfigurationAdmin admin = new SpringConfigurationAdmin(new Properties());
        Configuration[] result = admin.listConfigurations(null);
        assertThat(result).hasSize(1);
    }

    @Test
    void multiplePropertiesAreAllPopulated() throws Exception {
        Properties kafkaProps = new Properties();
        kafkaProps.setProperty("bootstrap.servers", "kafka:9092");
        kafkaProps.setProperty("security.protocol", "SASL_SSL");
        kafkaProps.setProperty("sasl.mechanism", "PLAIN");

        SpringConfigurationAdmin admin = new SpringConfigurationAdmin(kafkaProps);
        Dictionary<String, Object> dict = admin.getConfiguration("any.pid").getProperties();

        assertThat(dict.get("bootstrap.servers")).isEqualTo("kafka:9092");
        assertThat(dict.get("security.protocol")).isEqualTo("SASL_SSL");
        assertThat(dict.get("sasl.mechanism")).isEqualTo("PLAIN");
    }
}
