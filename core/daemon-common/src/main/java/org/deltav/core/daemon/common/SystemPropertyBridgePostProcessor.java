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
package org.deltav.core.daemon.common;

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
