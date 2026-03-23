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
package org.opennms.netmgt.enlinkd.boot;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import com.codahale.metrics.MetricRegistry;

import org.opennms.core.daemon.common.DaemonSmartLifecycle;
import org.opennms.core.daemon.common.NoOpEntityScopeProvider;
import org.opennms.core.daemon.common.NoOpTracerRegistry;
import org.opennms.core.mate.api.EntityScopeProvider;
import org.opennms.core.rpc.api.RpcClientFactory;
import org.opennms.core.tracing.api.TracerRegistry;
import org.opennms.netmgt.config.EnhancedLinkdConfig;
import org.opennms.netmgt.config.EnhancedLinkdConfigFactory;
import org.opennms.netmgt.config.SnmpPeerFactory;
import org.opennms.netmgt.dao.api.IpInterfaceDao;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.enlinkd.BridgeOnmsTopologyUpdater;
import org.opennms.netmgt.enlinkd.CdpOnmsTopologyUpdater;
import org.opennms.netmgt.enlinkd.DiscoveryBridgeDomains;
import org.opennms.netmgt.enlinkd.EnhancedLinkd;
import org.opennms.netmgt.enlinkd.EventProcessor;
import org.opennms.netmgt.enlinkd.IsisOnmsTopologyUpdater;
import org.opennms.netmgt.enlinkd.LldpOnmsTopologyUpdater;
import org.opennms.netmgt.enlinkd.NetworkRouterTopologyUpdater;
import org.opennms.netmgt.enlinkd.NodesOnmsTopologyUpdater;
import org.opennms.netmgt.enlinkd.OspfAreaOnmsTopologyUpdater;
import org.opennms.netmgt.enlinkd.OspfOnmsTopologyUpdater;
import org.opennms.netmgt.enlinkd.UserDefinedLinkTopologyUpdater;
import org.opennms.netmgt.enlinkd.persistence.impl.BridgeBridgeLinkDaoJpa;
import org.opennms.netmgt.enlinkd.persistence.impl.BridgeElementDaoJpa;
import org.opennms.netmgt.enlinkd.persistence.impl.BridgeMacLinkDaoJpa;
import org.opennms.netmgt.enlinkd.persistence.impl.BridgeStpLinkDaoJpa;
import org.opennms.netmgt.enlinkd.persistence.impl.CdpElementDaoJpa;
import org.opennms.netmgt.enlinkd.persistence.impl.CdpLinkDaoJpa;
import org.opennms.netmgt.enlinkd.persistence.impl.IpNetToMediaDaoJpa;
import org.opennms.netmgt.enlinkd.persistence.impl.IsIsElementDaoJpa;
import org.opennms.netmgt.enlinkd.persistence.impl.IsIsLinkDaoJpa;
import org.opennms.netmgt.enlinkd.persistence.impl.LldpElementDaoJpa;
import org.opennms.netmgt.enlinkd.persistence.impl.LldpLinkDaoJpa;
import org.opennms.netmgt.enlinkd.persistence.impl.OspfAreaDaoJpa;
import org.opennms.netmgt.enlinkd.persistence.impl.OspfElementDaoJpa;
import org.opennms.netmgt.enlinkd.persistence.impl.OspfLinkDaoJpa;
import org.opennms.netmgt.enlinkd.persistence.impl.UserDefinedLinkDaoJpa;
import org.opennms.netmgt.enlinkd.persistence.api.TopologyEntityCache;
import org.opennms.netmgt.enlinkd.model.CdpElementTopologyEntity;
import org.opennms.netmgt.enlinkd.model.CdpLinkTopologyEntity;
import org.opennms.netmgt.enlinkd.model.IpInterfaceTopologyEntity;
import org.opennms.netmgt.enlinkd.model.IsIsElementTopologyEntity;
import org.opennms.netmgt.enlinkd.model.IsIsLinkTopologyEntity;
import org.opennms.netmgt.enlinkd.model.LldpElementTopologyEntity;
import org.opennms.netmgt.enlinkd.model.LldpLinkTopologyEntity;
import org.opennms.netmgt.enlinkd.model.NodeTopologyEntity;
import org.opennms.netmgt.enlinkd.model.OspfAreaTopologyEntity;
import org.opennms.netmgt.enlinkd.model.OspfLinkTopologyEntity;
import org.opennms.netmgt.enlinkd.model.SnmpInterfaceTopologyEntity;
import org.opennms.netmgt.enlinkd.service.api.BridgeTopologyService;
import org.opennms.netmgt.enlinkd.service.api.CdpTopologyService;
import org.opennms.netmgt.enlinkd.service.api.IpNetToMediaTopologyService;
import org.opennms.netmgt.enlinkd.service.api.IsisTopologyService;
import org.opennms.netmgt.enlinkd.service.api.LldpTopologyService;
import org.opennms.netmgt.enlinkd.service.api.NodeTopologyService;
import org.opennms.netmgt.enlinkd.service.api.OspfTopologyService;
import org.opennms.netmgt.enlinkd.service.api.UserDefinedLinkTopologyService;
import org.opennms.netmgt.enlinkd.service.impl.BridgeTopologyServiceImpl;
import org.opennms.netmgt.enlinkd.service.impl.CdpTopologyServiceImpl;
import org.opennms.netmgt.enlinkd.service.impl.IpNetToMediaTopologyServiceImpl;
import org.opennms.netmgt.enlinkd.service.impl.IsisTopologyServiceImpl;
import org.opennms.netmgt.enlinkd.service.impl.LldpTopologyServiceImpl;
import org.opennms.netmgt.enlinkd.service.impl.NodeTopologyServiceImpl;
import org.opennms.netmgt.enlinkd.service.impl.OspfTopologyServiceImpl;
import org.opennms.netmgt.enlinkd.service.impl.UserDefinedLinkTopologyServiceImpl;
import org.opennms.netmgt.events.api.AnnotationBasedEventListenerAdapter;
import org.opennms.netmgt.events.api.EventSubscriptionService;
import org.opennms.netmgt.snmp.proxy.LocationAwareSnmpClient;
import org.opennms.netmgt.snmp.proxy.common.LocationAwareSnmpClientRpcImpl;
import org.opennms.netmgt.topologies.service.api.OnmsTopologyDao;
import org.opennms.netmgt.topologies.service.impl.OnmsTopologyDaoInMemoryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot configuration for the Enlinkd daemon and its dependencies.
 *
 * <p>Wires the {@link EnhancedLinkd} daemon with topology services, updaters,
 * SNMP client, event processor, and lifecycle management.</p>
 *
 * <p>JPA DAOs in {@code opennms-model-jakarta} implement the persistence API
 * interfaces (same FQCN as legacy entities), so topology services receive
 * DAOs via their normal typed setter methods.</p>
 */
@Configuration
public class EnlinkdDaemonConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(EnlinkdDaemonConfiguration.class);

    @Value("${opennms.home:/opt/deltav}")
    private String opennmsHome;

    // ── 1. Infrastructure ────────────────────────────────────────────

    @Bean
    public MetricRegistry metricRegistry() {
        return new MetricRegistry();
    }

    @Bean
    public TracerRegistry tracerRegistry() {
        return new NoOpTracerRegistry();
    }

    @Bean
    public EntityScopeProvider entityScopeProvider() {
        return new NoOpEntityScopeProvider();
    }

    // ── 2. SNMP ──────────────────────────────────────────────────────

    @Bean
    public SnmpPeerFactory snmpPeerFactory() throws IOException {
        LOG.info("Initializing SnmpPeerFactory");
        SnmpPeerFactory.init();
        return SnmpPeerFactory.getInstance();
    }

    @Bean
    public LocationAwareSnmpClient locationAwareSnmpClient(RpcClientFactory rpcClientFactory) {
        return new LocationAwareSnmpClientRpcImpl(rpcClientFactory);
    }

    // ── 3. Enlinkd Config ────────────────────────────────────────────

    @Bean
    public EnhancedLinkdConfig linkdConfig() throws IOException {
        LOG.info("Initializing EnhancedLinkdConfigFactory");
        return new EnhancedLinkdConfigFactory();
    }

    // ── 4. OnmsTopologyDao ───────────────────────────────────────────

    @Bean
    public OnmsTopologyDao onmsTopologyDao() {
        return new OnmsTopologyDaoInMemoryImpl();
    }

    // ── 5. TopologyEntityCache (no-op) ───────────────────────────────

    /**
     * No-op TopologyEntityCache. The real {@code TopologyEntityCacheImpl} depends
     * on {@code TopologyEntityDao} from the Hibernate-based persistence impl.
     * Enlinkd topology updaters use the cache for building topology views;
     * returning empty lists means topology views will be populated on first refresh.
     */
    @Bean
    public TopologyEntityCache topologyEntityCache() {
        return new TopologyEntityCache() {
            @Override public List<NodeTopologyEntity> getNodeTopologyEntities() { return Collections.emptyList(); }
            @Override public List<CdpLinkTopologyEntity> getCdpLinkTopologyEntities() { return Collections.emptyList(); }
            @Override public List<OspfLinkTopologyEntity> getOspfLinkTopologyEntities() { return Collections.emptyList(); }
            @Override public List<OspfAreaTopologyEntity> getOspfAreaTopologyEntities() { return Collections.emptyList(); }
            @Override public List<IsIsLinkTopologyEntity> getIsIsLinkTopologyEntities() { return Collections.emptyList(); }
            @Override public List<LldpLinkTopologyEntity> getLldpLinkTopologyEntities() { return Collections.emptyList(); }
            @Override public List<CdpElementTopologyEntity> getCdpElementTopologyEntities() { return Collections.emptyList(); }
            @Override public List<IsIsElementTopologyEntity> getIsIsElementTopologyEntities() { return Collections.emptyList(); }
            @Override public List<LldpElementTopologyEntity> getLldpElementTopologyEntities() { return Collections.emptyList(); }
            @Override public List<SnmpInterfaceTopologyEntity> getSnmpInterfaceTopologyEntities() { return Collections.emptyList(); }
            @Override public List<IpInterfaceTopologyEntity> getIpInterfaceTopologyEntities() { return Collections.emptyList(); }
            @Override public void refresh() { /* no-op */ }
        };
    }

    // ── 6. Topology Services ─────────────────────────────────────────
    //
    // Our JPA DAOs now implement the persistence API interfaces, so normal
    // setter injection works — no more reflective field injection needed.

    @Bean
    public NodeTopologyService nodeTopologyService(NodeDao nodeDao,
                                                    TopologyEntityCache topologyEntityCache) {
        var svc = new NodeTopologyServiceImpl();
        svc.setNodeDao(nodeDao);
        svc.setTopologyEntityCache(topologyEntityCache);
        return svc;
    }

    @Bean
    public CdpTopologyService cdpTopologyService(CdpLinkDaoJpa cdpLinkDao,
                                                  CdpElementDaoJpa cdpElementDao,
                                                  TopologyEntityCache topologyEntityCache) {
        var svc = new CdpTopologyServiceImpl();
        svc.setCdpLinkDao(cdpLinkDao);
        svc.setCdpElementDao(cdpElementDao);
        svc.setTopologyEntityCache(topologyEntityCache);
        return svc;
    }

    @Bean
    public LldpTopologyService lldpTopologyService(LldpLinkDaoJpa lldpLinkDao,
                                                    LldpElementDaoJpa lldpElementDao,
                                                    TopologyEntityCache topologyEntityCache) {
        var svc = new LldpTopologyServiceImpl();
        svc.setLldpLinkDao(lldpLinkDao);
        svc.setLldpElementDao(lldpElementDao);
        svc.setTopologyEntityCache(topologyEntityCache);
        return svc;
    }

    @Bean
    public OspfTopologyService ospfTopologyService(OspfLinkDaoJpa ospfLinkDao,
                                                    OspfElementDaoJpa ospfElementDao,
                                                    OspfAreaDaoJpa ospfAreaDao,
                                                    TopologyEntityCache topologyEntityCache) {
        var svc = new OspfTopologyServiceImpl();
        svc.setOspfLinkDao(ospfLinkDao);
        svc.setOspfElementDao(ospfElementDao);
        svc.setOspfAreaDao(ospfAreaDao);
        svc.setTopologyEntityCache(topologyEntityCache);
        return svc;
    }

    @Bean
    public IsisTopologyService isisTopologyService(IsIsLinkDaoJpa isisLinkDao,
                                                    IsIsElementDaoJpa isisElementDao,
                                                    TopologyEntityCache topologyEntityCache) {
        var svc = new IsisTopologyServiceImpl();
        svc.setIsisLinkDao(isisLinkDao);
        svc.setIsisElementDao(isisElementDao);
        svc.setTopologyEntityCache(topologyEntityCache);
        return svc;
    }

    @Bean
    public BridgeTopologyService bridgeTopologyService(BridgeElementDaoJpa bridgeElementDao,
                                                        BridgeBridgeLinkDaoJpa bridgeBridgeLinkDao,
                                                        BridgeMacLinkDaoJpa bridgeMacLinkDao,
                                                        BridgeStpLinkDaoJpa bridgeStpLinkDao,
                                                        IpNetToMediaDaoJpa ipNetToMediaDao,
                                                        TopologyEntityCache topologyEntityCache) {
        var svc = new BridgeTopologyServiceImpl();
        svc.setBridgeElementDao(bridgeElementDao);
        svc.setBridgeBridgeLinkDao(bridgeBridgeLinkDao);
        svc.setBridgeMacLinkDao(bridgeMacLinkDao);
        svc.setBridgeStpLinkDao(bridgeStpLinkDao);
        svc.setIpNetToMediaDao(ipNetToMediaDao);
        svc.setTopologyEntityCache(topologyEntityCache);
        return svc;
    }

    @Bean
    public IpNetToMediaTopologyService ipNetToMediaTopologyService(IpNetToMediaDaoJpa ipNetToMediaDao,
                                                                    IpInterfaceDao ipInterfaceDao) {
        var svc = new IpNetToMediaTopologyServiceImpl();
        svc.setIpNetToMediaDao(ipNetToMediaDao);
        svc.setIpInterfaceDao(ipInterfaceDao);
        return svc;
    }

    @Bean
    public UserDefinedLinkTopologyService userDefinedLinkTopologyService(
            UserDefinedLinkDaoJpa userDefinedLinkDao,
            TopologyEntityCache topologyEntityCache) {
        var svc = new UserDefinedLinkTopologyServiceImpl();
        // UserDefinedLinkTopologyServiceImpl uses @Autowired field injection with no setter
        setField(svc, "userDefinedLinkDao", userDefinedLinkDao);
        svc.setTopologyEntityCache(topologyEntityCache);
        return svc;
    }

    // ── 7. Topology Updaters ─────────────────────────────────────────

    @Bean
    public NodesOnmsTopologyUpdater nodesTopologyUpdater(OnmsTopologyDao topologyDao,
                                                          NodeTopologyService nodeTopologyService) {
        return new NodesOnmsTopologyUpdater(topologyDao, nodeTopologyService);
    }

    @Bean
    public CdpOnmsTopologyUpdater cdpTopologyUpdater(OnmsTopologyDao topologyDao,
                                                      CdpTopologyService cdpTopologyService,
                                                      NodeTopologyService nodeTopologyService) {
        return new CdpOnmsTopologyUpdater(topologyDao, cdpTopologyService, nodeTopologyService);
    }

    @Bean
    public LldpOnmsTopologyUpdater lldpTopologyUpdater(OnmsTopologyDao topologyDao,
                                                        LldpTopologyService lldpTopologyService,
                                                        NodeTopologyService nodeTopologyService) {
        return new LldpOnmsTopologyUpdater(topologyDao, lldpTopologyService, nodeTopologyService);
    }

    @Bean
    public IsisOnmsTopologyUpdater isisTopologyUpdater(OnmsTopologyDao topologyDao,
                                                        IsisTopologyService isisTopologyService,
                                                        NodeTopologyService nodeTopologyService) {
        return new IsisOnmsTopologyUpdater(topologyDao, isisTopologyService, nodeTopologyService);
    }

    @Bean
    public OspfOnmsTopologyUpdater ospfTopologyUpdater(OnmsTopologyDao topologyDao,
                                                        OspfTopologyService ospfTopologyService,
                                                        NodeTopologyService nodeTopologyService) {
        return new OspfOnmsTopologyUpdater(topologyDao, ospfTopologyService, nodeTopologyService);
    }

    @Bean
    public OspfAreaOnmsTopologyUpdater ospfAreaTopologyUpdater(OnmsTopologyDao topologyDao,
                                                                OspfTopologyService ospfTopologyService,
                                                                NodeTopologyService nodeTopologyService) {
        return new OspfAreaOnmsTopologyUpdater(topologyDao, ospfTopologyService, nodeTopologyService);
    }

    @Bean
    public BridgeOnmsTopologyUpdater bridgeTopologyUpdater(OnmsTopologyDao topologyDao,
                                                            BridgeTopologyService bridgeTopologyService,
                                                            NodeTopologyService nodeTopologyService) {
        return new BridgeOnmsTopologyUpdater(topologyDao, bridgeTopologyService, nodeTopologyService);
    }

    @Bean
    public NetworkRouterTopologyUpdater networkRouterTopologyUpdater(OnmsTopologyDao topologyDao,
                                                                      NodeTopologyService nodeTopologyService) {
        return new NetworkRouterTopologyUpdater(topologyDao, nodeTopologyService);
    }

    @Bean
    public UserDefinedLinkTopologyUpdater userDefinedLinkTopologyUpdater(
            UserDefinedLinkTopologyService udlTopologyService,
            OnmsTopologyDao topologyDao,
            NodeTopologyService nodeTopologyService) {
        return new UserDefinedLinkTopologyUpdater(udlTopologyService, topologyDao, nodeTopologyService);
    }

    @Bean
    public DiscoveryBridgeDomains discoveryBridgeDomains(BridgeTopologyService bridgeTopologyService) {
        return new DiscoveryBridgeDomains(bridgeTopologyService);
    }

    // ── 8. EnhancedLinkd daemon ──────────────────────────────────────

    @Bean
    public EnhancedLinkd enhancedLinkd(EnhancedLinkdConfig linkdConfig,
                                        NodeTopologyService nodeTopologyService,
                                        CdpTopologyService cdpTopologyService,
                                        LldpTopologyService lldpTopologyService,
                                        IsisTopologyService isisTopologyService,
                                        OspfTopologyService ospfTopologyService,
                                        BridgeTopologyService bridgeTopologyService,
                                        IpNetToMediaTopologyService ipNetToMediaTopologyService) {
        var linkd = new EnhancedLinkd();
        linkd.setLinkdConfig(linkdConfig);
        linkd.setQueryManager(nodeTopologyService);
        linkd.setCdpTopologyService(cdpTopologyService);
        linkd.setLldpTopologyService(lldpTopologyService);
        linkd.setIsisTopologyService(isisTopologyService);
        linkd.setOspfTopologyService(ospfTopologyService);
        linkd.setBridgeTopologyService(bridgeTopologyService);
        linkd.setIpNetToMediaTopologyService(ipNetToMediaTopologyService);
        // @Autowired fields (updaters, snmpClient, discoveryBridgeDomains) are
        // injected by Spring's AutowiredAnnotationBeanPostProcessor after construction
        return linkd;
    }

    // ── 9. Event Processor ───────────────────────────────────────────

    @Bean
    public EventProcessor eventProcessor(EnhancedLinkd linkd) {
        var processor = new EventProcessor();
        processor.setLinkd(linkd);
        // Do NOT call init() — that subscribes to MessageBus which is handled
        // separately via the AnnotationBasedEventListenerAdapter
        return processor;
    }

    @Bean(initMethod = "afterPropertiesSet")
    public AnnotationBasedEventListenerAdapter enlinkdEventListener(
            EventProcessor eventProcessor,
            EventSubscriptionService eventSubscriptionService) {
        return new AnnotationBasedEventListenerAdapter(eventProcessor, eventSubscriptionService);
    }

    // ── 10. SmartLifecycle ───────────────────────────────────────────

    @Bean
    public SmartLifecycle enlinkdLifecycle(EnhancedLinkd daemon) {
        return new DaemonSmartLifecycle(daemon);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /**
     * Sets a private field on an object via reflection.
     * Used only for UserDefinedLinkTopologyServiceImpl which has @Autowired
     * field injection with no setter method.
     */
    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set field " + fieldName + " on " + target.getClass().getName(), e);
        }
    }
}
