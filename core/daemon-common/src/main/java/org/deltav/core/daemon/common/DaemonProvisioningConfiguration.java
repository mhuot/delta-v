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

import org.opennms.core.mate.api.EntityScopeProvider;
import org.opennms.core.spring.BeanUtils;
import org.opennms.features.scv.api.SecureCredentialsVault;
import org.opennms.features.scv.jceks.JCEKSSecureCredentialsVault;
import org.opennms.netmgt.dao.api.IpInterfaceDao;
import org.opennms.netmgt.dao.api.MonitoredServiceDao;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.dao.api.SessionUtils;
import org.opennms.netmgt.dao.api.SnmpInterfaceDao;
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
