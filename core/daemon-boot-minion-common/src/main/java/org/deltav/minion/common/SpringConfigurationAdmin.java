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

import java.io.IOException;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Set;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

/**
 * Minimal {@link ConfigurationAdmin} adapter that exposes Kafka properties from Spring
 * configuration to OSGi-designed components such as
 * {@code KafkaRemoteMessageDispatcherFactory} (Sink client).
 *
 * <p>Only {@link #getConfiguration(String)} and {@link #getConfiguration(String, String)}
 * return meaningful results; all factory/create methods throw
 * {@link UnsupportedOperationException} since they are not needed in a Spring Boot context.
 */
public class SpringConfigurationAdmin implements ConfigurationAdmin {

    private final Configuration configuration;

    public SpringConfigurationAdmin(Properties kafkaProperties) {
        Hashtable<String, Object> dict = new Hashtable<>();
        kafkaProperties.forEach((key, value) -> dict.put(key.toString(), value));
        this.configuration = new SimpleConfiguration(dict);
    }

    @Override
    public Configuration getConfiguration(String pid) throws IOException {
        return configuration;
    }

    @Override
    public Configuration getConfiguration(String pid, String location) throws IOException {
        return configuration;
    }

    @Override
    public Configuration getFactoryConfiguration(String factoryPid, String name) throws IOException {
        throw new UnsupportedOperationException("Not supported in Spring Boot context");
    }

    @Override
    public Configuration getFactoryConfiguration(String factoryPid, String name, String location) throws IOException {
        throw new UnsupportedOperationException("Not supported in Spring Boot context");
    }

    @Override
    public Configuration createFactoryConfiguration(String factoryPid) throws IOException {
        throw new UnsupportedOperationException("Not supported in Spring Boot context");
    }

    @Override
    public Configuration createFactoryConfiguration(String factoryPid, String location) throws IOException {
        throw new UnsupportedOperationException("Not supported in Spring Boot context");
    }

    @Override
    public Configuration[] listConfigurations(String filter) throws IOException, InvalidSyntaxException {
        return new Configuration[]{configuration};
    }

    private static class SimpleConfiguration implements Configuration {

        private final Dictionary<String, Object> properties;

        SimpleConfiguration(Dictionary<String, Object> properties) {
            this.properties = properties;
        }

        @Override
        public Dictionary<String, Object> getProperties() {
            return properties;
        }

        @Override
        public Dictionary<String, Object> getProcessedProperties(ServiceReference<?> reference) {
            return properties;
        }

        @Override
        public String getPid() {
            return "spring-kafka";
        }

        @Override
        public String getFactoryPid() {
            return null;
        }

        @Override
        public String getBundleLocation() {
            return null;
        }

        @Override
        public void setBundleLocation(String location) {
            // no-op: bundle location is not applicable in a Spring Boot context
        }

        @Override
        public long getChangeCount() {
            return 0L;
        }

        @Override
        public void update() throws IOException {
            // no-op: properties are provided by Spring and do not change at runtime
        }

        @Override
        public void update(Dictionary<String, ?> updatedProperties) throws IOException {
            // no-op: properties are provided by Spring and do not change at runtime
        }

        @Override
        public boolean updateIfDifferent(Dictionary<String, ?> updatedProperties) throws IOException {
            return false;
        }

        @Override
        public void delete() throws IOException {
            // no-op: lifecycle is managed by Spring, not OSGi CM
        }

        @Override
        public void addAttributes(Configuration.ConfigurationAttribute... attributes) throws IOException {
            // no-op: attributes are not used in Spring Boot context
        }

        @Override
        public Set<Configuration.ConfigurationAttribute> getAttributes() {
            return Collections.emptySet();
        }

        @Override
        public void removeAttributes(Configuration.ConfigurationAttribute... attributes) throws IOException {
            // no-op: attributes are not used in Spring Boot context
        }

        @Override
        public boolean equals(Object other) {
            return this == other;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }
    }
}
