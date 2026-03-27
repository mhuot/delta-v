/*
 * Licensed to The OpenNMS Group, Inc (TOG) under one or more
 * contributor license agreements.  See the LICENSE.md file
 * distributed with this work for additional information
 * regarding copyright ownership.
 *
 * TOG licenses this file to You under the GNU Affero General
 * Public License Version 3 (the "License") or (at your option)
 * any later version.  You may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at:
 *
 *      https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */
package org.opennms.netmgt.telemetry.boot;

import org.opennms.core.ipc.sink.api.MessageConsumerManager;
import org.opennms.core.ipc.sink.api.MessageDispatcherFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sink configuration for Telemetryd multi-bridge pattern.
 *
 * <p>Provides a {@link TelemetryMessageConsumerManager} that spawns one
 * {@link KafkaSinkBridge} per telemetry protocol
 * (Netflow-5, IPFIX, sFlow, etc.). Each bridge consumes from its own Kafka
 * Sink topic (e.g., OpenNMS.Sink.Telemetry-Netflow-5).</p>
 *
 * <p>The {@link LocalMessageDispatcherFactory} routes dispatched messages
 * directly to the consumer manager in-process -- no remote transport.</p>
 */
@Configuration
public class TelemetrydSinkConfiguration {

    @Bean
    public TelemetryMessageConsumerManager messageConsumerManager() {
        return new TelemetryMessageConsumerManager();
    }

    @Bean
    public MessageDispatcherFactory messageDispatcherFactory(
            TelemetryMessageConsumerManager messageConsumerManager) {
        return new LocalMessageDispatcherFactory(messageConsumerManager);
    }
}
