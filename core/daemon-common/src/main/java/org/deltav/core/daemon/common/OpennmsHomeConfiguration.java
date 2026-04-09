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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Bridges the Spring Boot {@code opennms.home} property to the JVM system
 * property expected by legacy classes such as {@code ConfigFileConstants}.
 *
 * <p>This replaces the previous pattern of passing {@code -Dopennms.home=...}
 * on the Docker command line. The value is resolved from:
 * <ol>
 *   <li>The {@code OPENNMS_HOME} environment variable (via {@code application.yml})</li>
 *   <li>The default {@code /opt/opennms}</li>
 * </ol>
 */
@Configuration
public class OpennmsHomeConfiguration implements InitializingBean {

    private static final Logger LOG = LoggerFactory.getLogger(OpennmsHomeConfiguration.class);

    @Value("${opennms.home:/opt/deltav}")
    private String opennmsHome;

    @Override
    public void afterPropertiesSet() {
        System.setProperty("opennms.home", opennmsHome);
        LOG.info("Set opennms.home={}", opennmsHome);
    }
}
