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

import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.opennms.netmgt.dao.api.SessionUtils;
import org.opennms.netmgt.eventd.EventUtil;
import org.opennms.netmgt.model.OnmsDistPoller;
import org.opennms.netmgt.model.OnmsMonitoringSystem;
import org.opennms.netmgt.model.monitoringLocations.OnmsMonitoringLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Spring Boot @Configuration for Telemetryd JPA entities, DAOs, and infrastructure beans.
 *
 * <p>Telemetryd has minimal DB needs -- only monitoring system identity entities
 * are required (OnmsMonitoringSystem, OnmsMonitoringLocation, OnmsDistPoller).
 * No Node, Outage, or Service entities are needed.</p>
 *
 * <p>Entity classes are listed explicitly via a custom {@link PersistenceManagedTypes}
 * bean instead of using package-based {@code @EntityScan} because the legacy
 * opennms-model module shares the same package ({@code org.opennms.netmgt.model})
 * and contains classes with incompatible javax.persistence / Hibernate 3.x
 * annotations that cause scanning failures with Hibernate 7.</p>
 */
@Configuration
@EnableTransactionManagement
public class TelemetrydJpaConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(TelemetrydJpaConfiguration.class);

    // ===================================================================
    // Section 1: JPA / Naming
    // ===================================================================

    /**
     * Use standard JPA naming -- table/column names from @Table/@Column annotations
     * are used as-is, without Spring Boot's default CamelCase to snake_case conversion.
     * Required because the OpenNMS schema uses camelCase table names
     * (e.g., monitoringSystems, ipInterface, ifServices).
     */
    @Bean
    public PhysicalNamingStrategyStandardImpl physicalNamingStrategy() {
        return new PhysicalNamingStrategyStandardImpl();
    }

    /**
     * Explicitly lists the Jakarta entity classes needed by Telemetryd.
     * Minimal set: only monitoring system identity entities.
     */
    @Bean
    public PersistenceManagedTypes persistenceManagedTypes() {
        return PersistenceManagedTypes.of(
            OnmsMonitoringSystem.class.getName(),
            OnmsMonitoringLocation.class.getName(),
            OnmsDistPoller.class.getName()
        );
    }

    // ===================================================================
    // Section 2: Transaction / SessionUtils
    // ===================================================================

    /**
     * SessionUtils implementation backed by Spring's TransactionTemplate.
     * Replaces the OSGi-era SessionUtils that was used to bridge Hibernate
     * sessions across OSGi bundles.
     */
    @Bean
    public SessionUtils sessionUtils(PlatformTransactionManager txManager) {
        var txTemplate = new TransactionTemplate(txManager);
        var readOnlyTxTemplate = new TransactionTemplate(txManager);
        readOnlyTxTemplate.setReadOnly(true);
        return new SessionUtils() {
            @Override
            public <V> V withTransaction(java.util.function.Supplier<V> supplier) {
                return txTemplate.execute(status -> supplier.get());
            }
            @Override
            public <V> V withReadOnlyTransaction(java.util.function.Supplier<V> supplier) {
                return readOnlyTxTemplate.execute(status -> supplier.get());
            }
            @Override
            public <V> V withManualFlush(java.util.function.Supplier<V> supplier) {
                return supplier.get();
            }
        };
    }

    /**
     * TransactionTemplate for daemon components that need explicit transaction control.
     */
    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager txManager) {
        return new TransactionTemplate(txManager);
    }

    // ===================================================================
    // Section 3: No-op stubs
    // ===================================================================

    /**
     * Minimal EventUtil implementation for parameter expansion.
     * The full EventUtilDaoImpl depends on the legacy Hibernate DAO layer.
     * This pass-through implementation returns inputs unchanged when no
     * database-backed token resolution is available.
     */
    @Bean
    public EventUtil eventUtil() {
        return new EventUtil() {
            @Override public String expandParms(String inp, org.opennms.netmgt.xml.event.Event event) { return inp; }
            @Override public String expandParms(String inp, org.opennms.netmgt.xml.event.Event event, java.util.Map<String, java.util.Map<String, String>> decode) { return inp; }
            @Override public String getNamedParmValue(String string, org.opennms.netmgt.xml.event.Event event) { return ""; }
            @Override public void expandMapValues(java.util.Map<String, String> parmMap, org.opennms.netmgt.xml.event.Event event) {}
            @Override public String getHardwareFieldValue(String parm, long nodeId) { return ""; }
            @Override public String getHostName(int nodeId, String hostip) { return hostip; }
            @Override public String getEventHost(org.opennms.netmgt.xml.event.Event event) { return ""; }
            @Override public String getIfAlias(long nodeId, String ipAddr) { return ""; }
            @Override public String getAssetFieldValue(String parm, long nodeId) { return ""; }
            @Override public String getForeignId(long nodeId) { return ""; }
            @Override public String getForeignSource(long nodeId) { return ""; }
            @Override public String getNodeLabel(long nodeId) { return ""; }
            @Override public String getNodeLocation(long nodeId) { return ""; }
            @Override public org.opennms.netmgt.eventd.processor.expandable.ExpandableParameterResolver getResolver(String token) { return null; }
            @Override public java.util.Date decodeSnmpV2TcDateAndTime(java.math.BigInteger value) { return new java.util.Date(); }
            @Override public String getPrimaryInterface(long nodeId) { return ""; }
        };
    }
}
