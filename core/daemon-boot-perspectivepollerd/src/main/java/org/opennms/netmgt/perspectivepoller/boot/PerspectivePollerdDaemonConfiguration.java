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
package org.opennms.netmgt.perspectivepoller.boot;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;

import org.opennms.core.mate.api.EntityScopeProvider;
import org.opennms.core.tracing.api.TracerRegistry;
import org.opennms.netmgt.collection.api.CollectionAgentFactory;
import org.opennms.netmgt.collection.api.PersisterFactory;
import org.opennms.netmgt.config.PollerConfig;
import org.opennms.netmgt.config.PollerConfigFactory;
import org.opennms.netmgt.config.SnmpPeerFactory;
import org.opennms.netmgt.config.poller.PollerConfiguration;
import org.opennms.netmgt.filter.api.FilterDao;
import org.opennms.netmgt.config.api.SnmpAgentConfigFactory;
import org.opennms.netmgt.config.snmp.SnmpConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.opennms.netmgt.dao.api.ApplicationDao;
import org.opennms.netmgt.dao.api.MonitoredServiceDao;
import org.opennms.netmgt.dao.api.MonitoringLocationDao;
import org.opennms.netmgt.dao.api.OutageDao;
import org.opennms.netmgt.dao.api.SessionUtils;
import org.opennms.netmgt.events.api.EventIpcManager;
import org.opennms.netmgt.events.api.AnnotationBasedEventListenerAdapter;
import org.opennms.netmgt.perspectivepoller.PerspectivePollerd;
import org.opennms.netmgt.perspectivepoller.PerspectiveServiceTracker;
import org.opennms.netmgt.poller.LocationAwarePollerClient;
import org.opennms.netmgt.threshd.api.ThresholdingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.opennms.core.daemon.common.SpringServiceDaemonSmartLifecycle;
import org.springframework.context.SmartLifecycle;

/**
 * Spring Boot configuration for the PerspectivePollerd daemon and its core dependencies.
 *
 * <p>Wires the {@link PerspectivePollerd} daemon with its configuration,
 * service tracker, event adapters, and lifecycle management. The daemon is
 * started via {@link SpringServiceDaemonSmartLifecycle} which calls
 * {@code afterPropertiesSet()} then {@code start()}.</p>
 *
 * <p>PerspectivePollerd polls services from perspective (remote) monitoring
 * locations to detect location-specific outages. It shares poller-configuration.xml
 * with Pollerd but uses its own scheduling and outage tracking logic.</p>
 *
 * <p>The {@link PollerConfigFactory} is created with a constructor-injected
 * {@link FilterDao}, eliminating the hidden {@code FilterDaoFactory.getInstance()}
 * coupling and the need for {@code @DependsOn} bean ordering.</p>
 */
@Configuration
public class PerspectivePollerdDaemonConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(PerspectivePollerdDaemonConfiguration.class);

    private static final XmlMapper XML_MAPPER;
    static {
        XML_MAPPER = XmlMapper.builder().defaultUseWrapper(false).build();
        XML_MAPPER.registerModule(new JaxbAnnotationModule());
        XML_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Value("${opennms.home:/opt/deltav}")
    private String opennmsHome;

    /**
     * Initializes the SNMP peer factory from snmp-config.xml via Jackson XmlMapper.
     * Required by SnmpMonitorStrategy.getRuntimeAttributes() which calls
     * SnmpPeerFactory.getInstance().getAgentConfig() to resolve SNMP
     * credentials for each polled service.
     */
    @Bean
    public SnmpAgentConfigFactory snmpPeerFactory(EntityScopeProvider entityScopeProvider) throws IOException {
        var configFile = new File(opennmsHome, "etc/snmp-config.xml");
        LOG.info("Loading SnmpPeerFactory from {}", configFile);
        var config = XML_MAPPER.readValue(configFile, SnmpConfig.class);
        var factory = new SnmpPeerFactory(config, entityScopeProvider, null);
        SnmpPeerFactory.setInstance(factory);
        return factory;
    }

    /**
     * Loads poller-configuration.xml via Jackson XmlMapper and creates a
     * PollerConfigFactory with constructor-injected FilterDao.
     */
    @Bean
    public PollerConfig pollerConfig(FilterDao filterDao) throws IOException {
        var configFile = new java.io.File(opennmsHome, "etc/poller-configuration.xml");
        LOG.info("Loading PollerConfigFactory from {}", configFile);
        var config = XML_MAPPER.readValue(configFile, PollerConfiguration.class);
        PollerConfigFactory.validate(config, filterDao);
        var factory = new PollerConfigFactory(configFile.lastModified(), config, filterDao);
        PollerConfigFactory.setInstance(factory);
        return factory;
    }

    /**
     * Tracks perspective-eligible services by monitoring application membership
     * changes via events and periodic refresh.
     */
    @Bean
    public PerspectiveServiceTracker perspectiveServiceTracker(
            SessionUtils sessionUtils,
            ApplicationDao applicationDao) {
        return new PerspectiveServiceTracker(sessionUtils, applicationDao);
    }

    /**
     * The PerspectivePollerd daemon.
     *
     * <p>Constructor parameter 9 ({@code eventForwarder}) is typed as
     * {@code EventForwarder}, but {@code EventIpcManager} extends
     * {@code EventForwarder}, so passing the EventIpcManager bean is valid.
     * Spring resolves by type compatibility.</p>
     */
    @Bean
    public PerspectivePollerd perspectivePollerd(
            SessionUtils sessionUtils,
            MonitoringLocationDao monitoringLocationDao,
            PollerConfig pollerConfig,
            MonitoredServiceDao monitoredServiceDao,
            LocationAwarePollerClient locationAwarePollerClient,
            ApplicationDao applicationDao,
            CollectionAgentFactory collectionAgentFactory,
            PersisterFactory persisterFactory,
            EventIpcManager eventIpcManager,
            ThresholdingService thresholdingService,
            OutageDao outageDao,
            TracerRegistry tracerRegistry,
            PerspectiveServiceTracker perspectiveServiceTracker) {
        return new PerspectivePollerd(sessionUtils, monitoringLocationDao, pollerConfig,
                monitoredServiceDao, locationAwarePollerClient, applicationDao,
                collectionAgentFactory, persisterFactory, eventIpcManager,
                thresholdingService, outageDao, tracerRegistry, perspectiveServiceTracker);
    }

    /**
     * Registers PerspectivePollerd's @EventHandler methods with the EventIpcManager.
     */
    @Bean
    public AnnotationBasedEventListenerAdapter perspectivePollerdEventAdapter(
            PerspectivePollerd perspectivePollerd,
            EventIpcManager eventIpcManager) {
        var adapter = new AnnotationBasedEventListenerAdapter();
        adapter.setAnnotatedListener(perspectivePollerd);
        adapter.setEventSubscriptionService(eventIpcManager);
        return adapter;
    }

    /**
     * Registers PerspectiveServiceTracker's @EventHandler methods with the EventIpcManager.
     */
    @Bean
    public AnnotationBasedEventListenerAdapter perspectiveServiceTrackerEventAdapter(
            PerspectiveServiceTracker perspectiveServiceTracker,
            EventIpcManager eventIpcManager) {
        var adapter = new AnnotationBasedEventListenerAdapter();
        adapter.setAnnotatedListener(perspectiveServiceTracker);
        adapter.setEventSubscriptionService(eventIpcManager);
        return adapter;
    }

    @Bean
    public SmartLifecycle perspectivePollerdLifecycle(PerspectivePollerd perspectivePollerd) {
        return new SpringServiceDaemonSmartLifecycle(perspectivePollerd, "PerspectivePollerd");
    }
}
