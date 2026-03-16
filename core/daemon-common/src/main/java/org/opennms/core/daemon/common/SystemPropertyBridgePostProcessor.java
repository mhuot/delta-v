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
package org.opennms.core.daemon.common;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Bridges Spring Boot properties to JVM system properties for legacy classes
 * that read {@code System.getProperty()} in static initializers or before
 * Spring bean creation.
 *
 * <p>Runs before any {@code @Configuration} class is processed, guaranteeing
 * that static initializers (e.g., {@code SystemInfoUtils}) see the correct
 * values.</p>
 *
 * <p>Registered via
 * {@code META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports}.</p>
 *
 * <h3>Bridged properties:</h3>
 * <table>
 *   <tr><th>Spring property</th><th>System property</th><th>Consumer</th></tr>
 *   <tr><td>{@code opennms.instance.id}</td>
 *       <td>{@code org.opennms.instance.id}</td>
 *       <td>{@code SystemInfoUtils} static init, Kafka topic names</td></tr>
 *   <tr><td>{@code opennms.tsid.node-id}</td>
 *       <td>{@code org.opennms.tsid.node-id}</td>
 *       <td>{@code KafkaEventSubscriptionService} TSID generation</td></tr>
 * </table>
 */
public class SystemPropertyBridgePostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                        SpringApplication application) {
        bridge(environment, "opennms.instance.id", "org.opennms.instance.id", "OpenNMS");
        bridge(environment, "opennms.tsid.node-id", "org.opennms.tsid.node-id", "0");
    }

    private void bridge(ConfigurableEnvironment env, String springKey,
                         String systemKey, String defaultValue) {
        String value = env.getProperty(springKey, defaultValue);
        System.setProperty(systemKey, value);
    }
}
