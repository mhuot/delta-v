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

import java.io.IOException;

import org.opennms.core.tracing.api.TracerRegistry;
import org.opennms.netmgt.collection.api.CollectionAgentFactory;
import org.opennms.netmgt.collection.api.PersisterFactory;
import org.opennms.netmgt.config.PollerConfig;
import org.opennms.netmgt.config.PollerConfigFactory;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
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
 * <p>Bean ordering: {@link PollerConfigFactory#init()} calls
 * {@code FilterDaoFactory.getInstance()} internally, so the
 * {@code filterDaoInitializer} bean in {@link PerspectivePollerdJpaConfiguration}
 * must be initialized first. This is enforced via {@code @DependsOn}.</p>
 */
@Configuration
public class PerspectivePollerdDaemonConfiguration {

    /**
     * Loads poller-configuration.xml via the singleton PollerConfigFactory.
     *
     * <p>Must run after FilterDaoFactory initialization because
     * {@code PollerConfigFactory.init()} validates filter rules against
     * {@code FilterDaoFactory.getInstance()}.</p>
     */
    @Bean
    @DependsOn("filterDaoInitializer")
    public PollerConfig pollerConfig() throws IOException {
        LOG.info("Initializing PollerConfigFactory");
        PollerConfigFactory.init();
        return PollerConfigFactory.getInstance();
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
     *
     * <p>Uses the 0-arg constructor + setters so that Spring's InitializingBean
     * callback calls afterPropertiesSet() exactly once. The 2-arg constructor
     * calls afterPropertiesSet() internally, which causes double registration
     * when Spring also calls it as an InitializingBean.</p>
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
