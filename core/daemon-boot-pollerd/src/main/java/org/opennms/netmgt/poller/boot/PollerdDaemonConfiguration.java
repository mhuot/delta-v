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
package org.opennms.netmgt.poller.boot;

import java.io.IOException;

import org.opennms.core.daemon.common.SpringServiceDaemonSmartLifecycle;
import org.opennms.core.tsid.TsidFactory;
import org.opennms.core.utils.InetAddressUtils;
import org.opennms.features.distributed.kvstore.json.noop.NoOpJsonStore;
import org.opennms.netmgt.collection.api.PersisterFactory;
import org.opennms.netmgt.config.PollerConfig;
import org.opennms.netmgt.config.PollerConfigFactory;
import org.opennms.netmgt.config.dao.outages.api.ReadablePollOutagesDao;
import org.opennms.netmgt.config.dao.outages.impl.OnmsPollOutagesDao;
import org.opennms.netmgt.dao.api.MonitoredServiceDao;
import org.opennms.netmgt.dao.api.OutageDao;
import org.opennms.netmgt.events.api.EventIpcManager;
import org.opennms.netmgt.icmp.proxy.LocationAwarePingClient;
import org.opennms.netmgt.poller.LocationAwarePollerClient;
import org.opennms.netmgt.poller.Poller;
import org.opennms.netmgt.poller.QueryManager;
import org.opennms.netmgt.poller.pollables.PollContext;
import org.opennms.netmgt.poller.pollables.PollableNetwork;
import org.opennms.netmgt.threshd.api.ThresholdingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.SmartLifecycle;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Spring Boot configuration for the Poller daemon and its core dependencies.
 *
 * <p>Wires the {@link Poller} daemon with its configuration, poll context,
 * pollable network tree, and lifecycle management. The Poller is started via
 * {@link SpringServiceDaemonSmartLifecycle} which calls {@code init()} then {@code start()}.</p>
 *
 * <p>During {@code init()}, Poller creates a LegacyScheduler, closes outages
 * for unmanaged services, schedules existing services, and creates the
 * PollerEventProcessor event listener. Event handling is self-registered
 * via {@code EventIpcManager.addEventListener()} inside {@code Poller.init()} --
 * no AnnotationBasedEventListenerAdapter bean is needed.</p>
 *
 * <p>Bean ordering: {@link PollerConfigFactory#init()} calls
 * {@code FilterDaoFactory.getInstance()} internally, so the
 * {@code filterDaoInitializer} bean in {@link PollerdJpaConfiguration}
 * must be initialized first. This is enforced via {@code @DependsOn}.</p>
 */
@Configuration
public class PollerdDaemonConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(PollerdDaemonConfiguration.class);

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
     * Loads poll-outages.xml via {@link OnmsPollOutagesDao}.
     *
     * <p>Uses a {@link NoOpJsonStore} because standalone Pollerd does not
     * need distributed config synchronization -- it reads directly from
     * the local filesystem.</p>
     */
    @Bean
    public ReadablePollOutagesDao pollOutagesDao() throws IOException {
        LOG.info("Initializing OnmsPollOutagesDao with NoOpJsonStore");
        return new OnmsPollOutagesDao(new NoOpJsonStore());
    }

    /**
     * PollContext that skips AsyncPollingEngine creation.
     *
     * <p>{@link StandalonePollContext} overrides {@code afterPropertiesSet()} as a
     * no-op to avoid loading resilience4j Bulkhead, which is not needed when polls
     * execute via Kafka RPC to Minion.</p>
     */
    @Bean
    public PollContext pollContext(EventIpcManager eventIpcManager,
                                  PollerConfig pollerConfig,
                                  QueryManager queryManager,
                                  LocationAwarePingClient locationAwarePingClient,
                                  TsidFactory tsidFactory) {
        String localHostName = InetAddressUtils.getLocalHostName();
        return new StandalonePollContext(eventIpcManager, pollerConfig, queryManager,
                locationAwarePingClient, tsidFactory, localHostName, "OpenNMS.Poller.DefaultPollContext");
    }

    /**
     * In-memory tree of pollable nodes, interfaces, and services.
     */
    @Bean
    public PollableNetwork pollableNetwork(PollContext pollContext) {
        return new PollableNetwork(pollContext);
    }

    /**
     * The Poller daemon.
     *
     * <p>Constructor injection handles the 8 core dependencies. The remaining
     * three (pollerConfig, network, eventIpcManager) are set via setters because
     * they were kept as setter-injected fields during the constructor injection
     * migration (Task 2).</p>
     */
    @Bean
    public Poller poller(QueryManager queryManager,
                         MonitoredServiceDao monitoredServiceDao,
                         OutageDao outageDao,
                         TransactionTemplate transactionTemplate,
                         PersisterFactory persisterFactory,
                         ThresholdingService thresholdingService,
                         LocationAwarePollerClient locationAwarePollerClient,
                         ReadablePollOutagesDao pollOutagesDao,
                         PollerConfig pollerConfig,
                         PollContext pollContext,
                         PollableNetwork pollableNetwork,
                         EventIpcManager eventIpcManager) {
        var poller = new Poller(queryManager, monitoredServiceDao, outageDao,
                transactionTemplate, persisterFactory, thresholdingService,
                locationAwarePollerClient, pollOutagesDao);
        poller.setPollerConfig(pollerConfig);
        poller.setNetwork(pollableNetwork);
        poller.setEventIpcManager(eventIpcManager);
        return poller;
    }

    /**
     * Wraps the Poller daemon in a {@link SmartLifecycle} so Spring Boot
     * manages its startup and shutdown. Phase is {@code Integer.MAX_VALUE}
     * so the daemon starts last (after all infrastructure beans) and stops first.
     */
    @Bean
    public SmartLifecycle pollerLifecycle(Poller poller) {
        return new SpringServiceDaemonSmartLifecycle(poller);
    }
}
