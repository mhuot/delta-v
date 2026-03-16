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
