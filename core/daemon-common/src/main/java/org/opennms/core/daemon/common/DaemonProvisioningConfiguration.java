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

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.opennms.core.mate.api.EntityScopeProvider;
import org.opennms.core.spring.BeanUtils;
import org.opennms.features.scv.api.SecureCredentialsVault;
import org.opennms.features.scv.jceks.JCEKSSecureCredentialsVault;
import org.opennms.netmgt.dao.api.IpInterfaceDao;
import org.opennms.netmgt.dao.api.MonitoredServiceDao;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.dao.api.SessionUtils;
import org.opennms.netmgt.dao.api.SnmpInterfaceDao;
import org.opennms.netmgt.provision.ServiceDetector;
import org.opennms.netmgt.provision.ServiceDetectorFactory;
import org.opennms.netmgt.provision.detector.registry.api.ServiceDetectorRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared infrastructure for all Spring Boot daemons.
 *
 * <p>Provides default beans that satisfy autowired dependencies in
 * provisioning-related classes and legacy static lookup bridges.
 * Conditional beans use {@code @ConditionalOnMissingBean} /
 * {@code @ConditionalOnBean} so any daemon can override with a real
 * implementation or the correct tier is selected automatically.</p>
 *
 * <ul>
 *   <li>{@link DaemonEntityScopeProvider} — real MATE variable interpolation
 *       backed by database DAOs. Created only when {@link NodeDao} is present
 *       (Provisiond, Pollerd, Collectd, Enlinkd). Resolves {@code ${node:label}},
 *       {@code ${scv:alias:password}}, {@code ${asset:region}}, etc.</li>
 *   <li>{@link NoOpEntityScopeProvider} — fallback no-op when no {@link NodeDao}
 *       is available. Disables MATE variable interpolation for lightweight
 *       daemons that do not require it.</li>
 *   <li>{@link JCEKSSecureCredentialsVault} — JCEKS-based SCV backing
 *       {@code ${scv:...}} MATE expressions. Falls back to an empty vault
 *       if the keystore does not yet exist.</li>
 *   <li>Empty {@link ServiceDetectorRegistry} — default no-op implementation.
 *       Daemons that need real detectors provide their own via {@code @Import}.</li>
 *   <li>{@link BeanUtils} — bridges legacy static bean lookups to this
 *       daemon's ApplicationContext.</li>
 * </ul>
 */
@Configuration
public class DaemonProvisioningConfiguration {

    /**
     * Real {@link EntityScopeProvider} backed by database DAOs.
     *
     * <p>Created only when {@link NodeDao} is present in the context,
     * which indicates the daemon has JPA database access. Resolves MATE
     * expressions like {@code ${node:label}}, {@code ${scv:alias:password}},
     * {@code ${asset:region}}, etc.</p>
     */
    @Bean
    @ConditionalOnBean(NodeDao.class)
    public EntityScopeProvider entityScopeProvider(
            NodeDao nodeDao,
            IpInterfaceDao ipInterfaceDao,
            SnmpInterfaceDao snmpInterfaceDao,
            MonitoredServiceDao monitoredServiceDao,
            SessionUtils sessionUtils,
            SecureCredentialsVault scv) {
        return new DaemonEntityScopeProvider(nodeDao, ipInterfaceDao,
                snmpInterfaceDao, monitoredServiceDao, sessionUtils, scv);
    }

    /**
     * No-op fallback for daemons without database access.
     */
    @Bean
    @ConditionalOnMissingBean(EntityScopeProvider.class)
    public EntityScopeProvider noOpEntityScopeProvider() {
        return new NoOpEntityScopeProvider();
    }

    /**
     * JCEKS-based Secure Credentials Vault for MATE {@code ${scv:...}} expressions.
     *
     * <p>Reads credentials from {@code ${opennms.home}/etc/scv.jce}.
     * If the keystore does not exist, an empty vault is created on first access.</p>
     */
    @Bean
    @ConditionalOnMissingBean(SecureCredentialsVault.class)
    public SecureCredentialsVault secureCredentialsVault(
            @Value("${opennms.home:/opt/deltav}") String opennmsHome) {
        return new JCEKSSecureCredentialsVault(opennmsHome + "/etc/scv.jce", "notReallyASecret");
    }

    /**
     * Default empty {@link ServiceDetectorRegistry} for daemons that do not
     * supply their own. Daemons that need real detectors should provide their
     * own {@code @Bean} or {@code @Import} a configuration that does.
     */
    @Bean
    @ConditionalOnMissingBean(ServiceDetectorRegistry.class)
    public ServiceDetectorRegistry serviceDetectorRegistry() {
        return new ServiceDetectorRegistry() {
            @Override public Map<String, String> getTypes() { return Collections.emptyMap(); }
            @Override public Set<String> getClassNames() { return Collections.emptySet(); }
            @Override public ServiceDetector getDetectorByClassName(String className, Map<String, String> properties) { return null; }
            @Override public ServiceDetectorFactory<?> getDetectorFactoryByClassName(String className) { return null; }
            @Override public Set<String> getServiceNames() { return Collections.emptySet(); }
            @Override public String getDetectorClassNameFromServiceName(String serviceName) { return null; }
            @Override public Class<?> getDetectorClassByServiceName(String serviceName) { return null; }
        };
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
