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
package org.deltav.netmgt.provision.nodecontext;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.Collections;

import javax.sql.DataSource;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.opennms.core.tracing.api.TracerRegistry;
import org.opennms.netmgt.dao.api.CategoryDao;
import org.opennms.netmgt.dao.api.IpInterfaceDao;
import org.opennms.netmgt.dao.api.MonitoredServiceDao;
import org.opennms.netmgt.dao.api.MonitoringLocationDao;
import org.opennms.netmgt.dao.api.MonitoringSystemDao;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.dao.api.RequisitionedCategoryAssociationDao;
import org.opennms.netmgt.dao.api.ServiceTypeDao;
import org.opennms.netmgt.dao.api.SessionUtils;
import org.opennms.netmgt.dao.api.SnmpInterfaceDao;
import org.deltav.core.event.forwarder.kafka.KafkaEventForwarder;
import org.deltav.core.event.forwarder.kafka.KafkaEventSubscriptionService;
import org.opennms.netmgt.events.api.AnnotationBasedEventListenerAdapter;
import org.opennms.netmgt.events.api.EventForwarder;
import org.opennms.core.rpc.api.RpcClientFactory;
import org.opennms.netmgt.config.SnmpAssetAdapterConfig;
import org.opennms.netmgt.config.SnmpAssetAdapterConfigFactory;
import org.opennms.netmgt.config.api.SnmpAgentConfigFactory;
import org.opennms.netmgt.config.snmpmetadata.SnmpMetadataConfigDao;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Real-main-class Spring context IT. Declares {@link org.deltav.netmgt.provision.boot.ProvisiondApplication}
 * as the {@code @SpringBootTest.classes} so {@code SpringApplication.run()}'s
 * component scan walks the exact same path as production — if
 * {@code scanBasePackages} is missing the
 * {@code "org.deltav.netmgt.provision.nodecontext"} entry, the assertions below
 * fail and the scan-package trap is caught at build time rather than at runtime
 * (cf. feedback_spring_boot_scan_package_trap).
 *
 * <p>{@code @MockitoBean} fields (Spring 7 / Boot 4 replacement for
 * {@code @MockBean}) are registered before any {@code @Configuration} class fires,
 * so they short-circuit every bean that would require a live PostgreSQL database,
 * a real filesystem, or a running Kafka broker.</p>
 *
 * <p>{@link CategoryStubBeans} provides {@link CategoryDao} via JDK dynamic proxy
 * instead of Mockito because the {@code CategoryDao} interface references
 * {@code org.hibernate.criterion.Criterion} (a Hibernate 3 legacy type absent from
 * Hibernate 7). Byte Buddy cannot instrument the class when the referenced type is
 * missing from the classpath, so Mockito cannot be used for this specific DAO.</p>
 */
@SpringBootTest(
        classes = org.deltav.netmgt.provision.boot.ProvisiondApplication.class,
        properties = {
                "deltav.node-context.enabled=true",
                "spring.main.web-application-type=none",
                "spring.main.banner-mode=off",
                "opennms.home=/tmp/deltav-test",
                "spring.autoconfigure.exclude=" +
                        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration," +
                        "org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration",
                "spring.kafka.bootstrap-servers=localhost:1",
                "spring.cloud.stream.kafka.binder.brokers=localhost:1",
                // Disable the Kafka RPC client — it connects to Kafka at startup and
                // needs DistPollerDao which triggers JPA/SQL against the mock DataSource.
                "opennms.rpc.kafka.enabled=false",
                // Allow @MockitoBean overrides of any same-named beans declared in
                // ProvisiondBootConfiguration or DaemonProvisioningConfiguration.
                "spring.main.allow-bean-definition-overriding=true"
        }
)
@Import(NodeContextProducerSpringContextIT.CategoryStubBeans.class)
class NodeContextProducerSpringContextIT {

    /**
     * Beans that cannot be provided via {@code @MockitoBean} because either:
     * <ul>
     *   <li>{@link CategoryDao} — references {@code org.hibernate.criterion.Criterion}
     *       (Hibernate 3 legacy type absent from Hibernate 7). Byte Buddy cannot
     *       instrument the class, so Mockito cannot be used.</li>
     *   <li>SNMP adapter beans — read config files from the filesystem at construction;
     *       mocking the factory beans avoids the file-not-found failure.</li>
     * </ul>
     */
    @TestConfiguration
    static class CategoryStubBeans {

        /**
         * JDK proxy instead of Mockito because {@code CategoryDao} references
         * {@code org.hibernate.criterion.Criterion} which is absent from Hibernate 7.
         */
        @Bean @Primary
        CategoryDao categoryDao() {
            return (CategoryDao) Proxy.newProxyInstance(
                    CategoryDao.class.getClassLoader(),
                    new Class<?>[]{ CategoryDao.class },
                    (proxy, method, args) -> {
                        Class<?> returnType = method.getReturnType();
                        if (returnType == java.util.List.class) {
                            return Collections.emptyList();
                        }
                        if (returnType == boolean.class) {
                            return false;
                        }
                        if (returnType == int.class || returnType == long.class) {
                            return 0;
                        }
                        if (returnType == void.class) {
                            return null;
                        }
                        // Primitive identity methods required by JDK proxy infrastructure.
                        if ("hashCode".equals(method.getName()) && args == null) {
                            return System.identityHashCode(proxy);
                        }
                        if ("equals".equals(method.getName()) && args != null && args.length == 1) {
                            return proxy == args[0];
                        }
                        if ("toString".equals(method.getName())) {
                            return "StubCategoryDao";
                        }
                        return null;
                    });
        }

    }

    // ---- DAO layer (@MockitoBean — Spring 7 / Boot 4 replacement for @MockBean) ----
    @MockitoBean NodeDao nodeDao;
    @MockitoBean SessionUtils sessionUtils;
    @MockitoBean MonitoringLocationDao monitoringLocationDao;
    @MockitoBean IpInterfaceDao ipInterfaceDao;
    @MockitoBean SnmpInterfaceDao snmpInterfaceDao;
    @MockitoBean MonitoredServiceDao monitoredServiceDao;
    @MockitoBean ServiceTypeDao serviceTypeDao;
    @MockitoBean RequisitionedCategoryAssociationDao categoryAssociationDao;
    @MockitoBean MonitoringSystemDao monitoringSystemDao;

    // ---- Event layer ----
    @MockitoBean EventForwarder eventForwarder;
    // kafkaEventForwarder is a concrete KafkaEventForwarder bean in KafkaEventTransportConfiguration
    // that tries to create a real Kafka producer at context startup. Override it with a mock so
    // the context loads without a running Kafka broker.
    @MockitoBean(name = "kafkaEventForwarder") KafkaEventForwarder kafkaEventForwarder;
    // kafkaEventSubscriptionService must be mocked as the concrete KafkaEventSubscriptionService
    // type (not just the EventSubscriptionService interface) because kafkaEventSubscriptionLifecycle
    // in KafkaEventTransportConfiguration expects the concrete type.
    @MockitoBean(name = "kafkaEventSubscriptionService") KafkaEventSubscriptionService kafkaEventSubscriptionService;

    // ---- Infrastructure ----
    @MockitoBean TracerRegistry tracerRegistry;
    @MockitoBean PlatformTransactionManager platformTransactionManager;
    @MockitoBean DataSource dataSource;
    @MockitoBean Scheduler quartzScheduler;
    // snmpPeerFactory reads /opt/deltav/etc/snmp-config.xml at startup — mock it to
    // avoid filesystem dependency. Named "snmpPeerFactory" to match ProvisiondBootConfiguration.
    @MockitoBean(name = "snmpPeerFactory") SnmpAgentConfigFactory snmpAgentConfigFactory;
    // RpcClientFactory is normally provided by KafkaRpcClientConfiguration which we disable
    // via opennms.rpc.kafka.enabled=false. Mock it directly so LocationAwareSnmpClientRpcImpl wires.
    @MockitoBean RpcClientFactory rpcClientFactory;
    // SNMP adapter factory beans that read config files from the filesystem.
    @MockitoBean(name = "snmpAssetAdapterConfigFactory") SnmpAssetAdapterConfigFactory snmpAssetAdapterConfigFactory;
    @MockitoBean(name = "snmpAssetAdapterConfig") SnmpAssetAdapterConfig snmpAssetAdapterConfig;
    @MockitoBean(name = "snmpMetadataConfigDao") SnmpMetadataConfigDao snmpMetadataConfigDao;

    @Autowired
    private ApplicationContext ctx;

    @Test
    void allProducerBeansWiredWhenFlagOn() {
        assertThat(ctx.getBean(NodeToProtobufTranslator.class)).isNotNull();
        assertThat(ctx.getBean(NodeContextPublisher.class)).isNotNull();
        assertThat(ctx.getBean(NodeContextDebouncer.class)).isNotNull();
        assertThat(ctx.getBean(NodeContextChangeFeedListener.class)).isNotNull();
        assertThat(ctx.getBean(NodeContextBootstrapRunner.class)).isNotNull();
        assertThat(ctx.getBean("nodeContextChangeFeedEventListener", AnnotationBasedEventListenerAdapter.class))
                .isNotNull();
        assertThat(ctx.getBean("deltavNodeContextTopic", NewTopic.class)).isNotNull();
    }
}
