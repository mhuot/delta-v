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

import java.util.Properties;

import org.opennms.core.ipc.common.kafka.KafkaConfigProvider;

public class SpringKafkaConfigProvider implements KafkaConfigProvider {

    private final Properties kafkaProperties;

    public SpringKafkaConfigProvider(Properties kafkaProperties) {
        this.kafkaProperties = new Properties();
        this.kafkaProperties.putAll(kafkaProperties);
    }

    @Override
    public Properties getProperties() {
        Properties copy = new Properties();
        copy.putAll(kafkaProperties);
        return copy;
    }
}
