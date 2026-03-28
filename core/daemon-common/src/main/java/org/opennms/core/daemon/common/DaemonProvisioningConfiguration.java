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

import org.opennms.core.daemon.common.registry.LocalServiceDetectorRegistry;
import org.opennms.core.mate.api.EntityScopeProvider;
import org.opennms.core.spring.BeanUtils;
import org.opennms.netmgt.provision.detector.registry.api.ServiceDetectorRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared infrastructure for all Spring Boot daemons.
 *
 * <p>Provides default beans that satisfy autowired dependencies in
 * provisioning-related classes and legacy static lookup bridges.
 * Conditional beans use {@code @ConditionalOnMissingBean} so any daemon
 * can override with a real implementation.</p>
 *
 * <ul>
 *   <li>{@link NoOpEntityScopeProvider} — disables MATE variable interpolation.
 *       Daemons needing real MATE (Provisiond, Thresholding, Pollerd) override
 *       with a bean backed by database DAOs.</li>
 *   <li>{@link LocalServiceDetectorRegistry} — SPI-based detector discovery.
 *       Returns empty unless detector factory JARs are on the classpath.</li>
 *   <li>{@link BeanUtils} — bridges legacy static bean lookups to this
 *       daemon's ApplicationContext.</li>
 * </ul>
 */
@Configuration
public class DaemonProvisioningConfiguration {

    @Bean
    @ConditionalOnMissingBean(EntityScopeProvider.class)
    public EntityScopeProvider entityScopeProvider() {
        return new NoOpEntityScopeProvider();
    }

    @Bean
    @ConditionalOnMissingBean(ServiceDetectorRegistry.class)
    public ServiceDetectorRegistry serviceDetectorRegistry() {
        return new LocalServiceDetectorRegistry();
    }

    /**
     * Bridges legacy {@link BeanUtils} static lookups to the daemon's Spring
     * Boot ApplicationContext.
     *
     * <p>{@code BeanUtils} implements {@code ApplicationContextAware}. Registering
     * it as a bean causes Spring to call {@code setApplicationContext()}, setting
     * the static {@code m_context} field. This prevents the legacy fallback path
     * through {@link org.opennms.core.spring.ContextRegistry} which would load
     * {@code applicationContext-commonConfigs.xml} and fail on missing config
     * files in per-daemon containers.</p>
     */
    @Bean
    public BeanUtils beanUtils() {
        return new BeanUtils();
    }
}
