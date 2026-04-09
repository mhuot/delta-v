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
package org.deltav.netmgt.collectd.boot;

import java.io.IOException;
import java.io.UncheckedIOException;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.opennms.core.tsid.TsidFactory;
import org.opennms.netmgt.config.api.DefaultDatabaseSchemaConfig;
import org.opennms.netmgt.config.filter.DatabaseSchema;
import org.opennms.netmgt.dao.api.SessionUtils;
import org.opennms.netmgt.filter.FilterDaoFactory;
import org.opennms.netmgt.filter.JdbcFilterDao;
import org.opennms.netmgt.model.OnmsApplication;
import org.opennms.netmgt.model.OnmsCategory;
import org.opennms.netmgt.model.OnmsDistPoller;
import org.opennms.netmgt.model.OnmsIpInterface;
import org.opennms.netmgt.model.OnmsMonitoredService;
import org.opennms.netmgt.model.OnmsMonitoringSystem;
import org.opennms.netmgt.model.OnmsAssetRecord;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.OnmsServiceType;
import org.opennms.netmgt.model.OnmsSnmpInterface;
import org.opennms.netmgt.model.monitoringLocations.OnmsMonitoringLocation;
import org.opennms.netmgt.model.jakarta.converter.InetAddressConverter;
import org.opennms.netmgt.model.jakarta.converter.NodeLabelSourceConverter;
import org.opennms.netmgt.model.jakarta.converter.NodeTypeConverter;
import org.opennms.netmgt.model.jakarta.converter.OnmsSeverityConverter;
import org.opennms.netmgt.model.jakarta.converter.PrimaryTypeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Spring Boot @Configuration for Collectd JPA entities, DAOs, and infrastructure beans.
 *
 * <p>Follows the same pattern as PollerdJpaConfiguration: explicit entity listing
 * (not @EntityScan) and SessionUtils for transaction management.</p>
 *
 * <p>Entity classes are listed explicitly via a custom {@link PersistenceManagedTypes}
 * bean instead of using package-based {@code @EntityScan} because the legacy
 * opennms-model module shares the same package ({@code org.opennms.netmgt.model})
 * and contains classes with incompatible javax.persistence / Hibernate 3.x
 * annotations that cause scanning failures with Hibernate 7.</p>
 *
 * <p>Notably absent are {@code OnmsOutage} and {@code OnmsApplication} — Collectd
 * manages data collection, not service outages. PersisterFactory and
 * ThresholdingService are wired in CollectdDaemonConfiguration with real
 * TSS-backed implementations.</p>
 */
@Configuration
@EnableTransactionManagement
public class CollectdJpaConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(CollectdJpaConfiguration.class);

    private static final XmlMapper XML_MAPPER;
    static {
        XML_MAPPER = XmlMapper.builder().defaultUseWrapper(false).build();
        XML_MAPPER.registerModule(new JaxbAnnotationModule());
        XML_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

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
     * Explicitly lists the Jakarta entity classes needed by Collectd.
     * This replaces {@code @EntityScan(basePackages = "org.opennms.netmgt.model")}
     * which would scan ALL classes in the package, including legacy entities
     * with incompatible javax.persistence annotations.
     *
     * <p>OnmsOutage and OnmsApplication are intentionally excluded — Collectd
     * does not manage outages or applications.</p>
     */
    @Bean
    public PersistenceManagedTypes persistenceManagedTypes() {
        return PersistenceManagedTypes.of(
            OnmsNode.class.getName(),
            OnmsAssetRecord.class.getName(),
            OnmsIpInterface.class.getName(),
            OnmsMonitoredService.class.getName(),
            OnmsServiceType.class.getName(),
            OnmsMonitoringSystem.class.getName(),
            OnmsMonitoringLocation.class.getName(),
            OnmsDistPoller.class.getName(),
            OnmsCategory.class.getName(),
            OnmsSnmpInterface.class.getName(),
            OnmsApplication.class.getName(),
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
     * TransactionTemplate for Collectd and DAO dependencies.
     * TransactionTemplate implements TransactionOperations, so this single bean
     * satisfies both injection points.
     */
    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager txManager) {
        return new TransactionTemplate(txManager);
    }

    // ===================================================================
    // Section 3: FilterDaoFactory initialization
    // ===================================================================

    /**
     * Initializes FilterDaoFactory with a JDBC-backed FilterDao.
     * Must happen before CollectdConfigFactory is used, because filter rule
     * validation requires FilterDaoFactory.getInstance().
     *
     * <p>The FilterDaoFactory is a static singleton. We create a JdbcFilterDao
     * backed by the Spring Boot DataSource and set it on the factory.</p>
     *
     * <p>The bean name {@code filterDaoInitializer} is intentional — CollectdConfigFactory
     * can use {@code @DependsOn("filterDaoInitializer")} to guarantee ordering.</p>
     */
    @Bean
    public JdbcFilterDao filterDaoInitializer(DataSource dataSource) {
        LOG.info("Initializing FilterDaoFactory with JdbcFilterDao");
        var jdbcFilterDao = new JdbcFilterDao();
        jdbcFilterDao.setDataSource(dataSource);
        var schemaConfig = loadDatabaseSchemaConfig();
        jdbcFilterDao.setDatabaseSchemaConfigFactory(schemaConfig);
        jdbcFilterDao.afterPropertiesSet();
        FilterDaoFactory.setInstance(jdbcFilterDao);
        return jdbcFilterDao;
    }

    private DefaultDatabaseSchemaConfig loadDatabaseSchemaConfig() {
        try (var is = getClass().getResourceAsStream("/database-schema.xml")) {
            var schema = XML_MAPPER.readValue(is, DatabaseSchema.class);
            return new DefaultDatabaseSchemaConfig(schema);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load database-schema.xml from classpath", e);
        }
    }

    // ===================================================================
    // Section 4: TsidFactory
    // ===================================================================

    @Bean
    public TsidFactory tsidFactory(@Value("${opennms.tsid.node-id:0}") long nodeId) {
        return new TsidFactory(nodeId);
    }

}
