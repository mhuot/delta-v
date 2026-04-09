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
package org.deltav.core.daemon.sink.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaSinkConfiguration {

    @Value("${opennms.kafka.bootstrap-servers:kafka:9092}")
    private String bootstrapServers;

    @Value("${opennms.kafka.sink.consumer-group:opennms-sink}")
    private String sinkConsumerGroup;

    @Bean
    public LocalMessageConsumerManager localMessageConsumerManager() {
        return new LocalMessageConsumerManager();
    }

    @Bean
    public KafkaSinkBridge kafkaSinkBridge(LocalMessageConsumerManager consumerManager) {
        var bridge = new KafkaSinkBridge(consumerManager, bootstrapServers, sinkConsumerGroup);
        consumerManager.setKafkaSinkBridge(bridge);
        return bridge;
    }
}
