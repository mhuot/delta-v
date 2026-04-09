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
package org.deltav.netmgt.telemetry.boot;

import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.opennms.netmgt.dao.api.SessionUtils;
import org.opennms.netmgt.model.OnmsDistPoller;
import org.opennms.netmgt.model.OnmsMonitoringSystem;
import org.opennms.netmgt.model.monitoringLocations.OnmsMonitoringLocation;
import org.opennms.netmgt.model.jakarta.converter.InetAddressConverter;
import org.opennms.netmgt.model.jakarta.converter.NodeLabelSourceConverter;
import org.opennms.netmgt.model.jakarta.converter.NodeTypeConverter;
import org.opennms.netmgt.model.jakarta.converter.OnmsSeverityConverter;
import org.opennms.netmgt.model.jakarta.converter.PrimaryTypeConverter;
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
            OnmsDistPoller.class.getName(),
            // AttributeConverters (autoApply=true)
            NodeTypeConverter.class.getName(),
            PrimaryTypeConverter.class.getName(),
            InetAddressConverter.class.getName(),
            NodeLabelSourceConverter.class.getName(),
            OnmsSeverityConverter.class.getName()
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

}
