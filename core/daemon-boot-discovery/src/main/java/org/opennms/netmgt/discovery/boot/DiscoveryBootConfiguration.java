package org.opennms.netmgt.discovery.boot;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.sql.DataSource;

import org.opennms.core.daemon.common.SpringServiceDaemonSmartLifecycle;
import org.opennms.core.daemon.common.JdbcDistPollerDao;
import org.opennms.core.daemon.common.JdbcInterfaceToNodeCache;
import org.opennms.netmgt.config.DiscoveryConfigFactory;
import org.opennms.netmgt.dao.api.DistPollerDao;
import org.opennms.netmgt.dao.api.InterfaceToNodeCache;
import org.opennms.netmgt.discovery.Discovery;
import org.opennms.netmgt.discovery.DiscoveryTaskExecutorImpl;
import org.opennms.netmgt.discovery.RangeChunker;
import org.opennms.netmgt.discovery.UnmanagedInterfaceFilter;
import org.opennms.netmgt.icmp.best.BestMatchPingerFactory;
import org.opennms.netmgt.icmp.PingerFactory;
import org.opennms.core.rpc.api.RpcClientFactory;
import org.opennms.netmgt.icmp.proxy.LocationAwarePingClientImpl;
import org.opennms.netmgt.icmp.proxy.PingProxyRpcModule;
import org.opennms.netmgt.icmp.proxy.PingSweepRpcModule;
import org.opennms.netmgt.provision.LocationAwareDetectorClient;
import org.opennms.netmgt.provision.detector.client.rpc.DetectorClientRpcModule;
import org.opennms.netmgt.provision.detector.client.rpc.LocationAwareDetectorClientRpcImpl;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot @Configuration that wires all Discovery beans.
 *
 * <p>Replaces the Karaf-era {@code applicationContext-daemon-loader-discovery.xml}.</p>
 */
@Configuration
public class DiscoveryBootConfiguration {

    // -- DAO / Cache --

    @Bean
    public DistPollerDao distPollerDao(DataSource dataSource) {
        return new JdbcDistPollerDao(dataSource);
    }

    @Bean
    public InterfaceToNodeCache interfaceToNodeCache(DataSource dataSource) {
        return new JdbcInterfaceToNodeCache(dataSource);
    }

    // -- Discovery Config --

    /**
     * Reads discovery-configuration.xml from ${opennms.home}/etc/.
     * The Docker overlay MUST include this file or startup will fail with IOException.
     */
    @Bean
    public DiscoveryConfigFactory discoveryConfigFactory() throws Exception {
        return new DiscoveryConfigFactory();
    }

    // -- ICMP Ping RPC --

    @Bean
    public PingerFactory pingerFactory() {
        return new BestMatchPingerFactory();
    }

    @Bean
    public PingProxyRpcModule pingProxyRpcModule() {
        return new PingProxyRpcModule();
    }

    @Bean
    public PingSweepRpcModule pingSweepRpcModule() {
        return new PingSweepRpcModule();
    }

    /**
     * IMPORTANT: Uses {@code javax.annotation.PostConstruct} which Spring 7
     * does NOT recognize. Must use {@code initMethod = "init"}.
     */
    @Bean(initMethod = "init")
    public LocationAwarePingClientImpl locationAwarePingClient(
            RpcClientFactory rpcClientFactory,
            PingProxyRpcModule pingProxyRpcModule,
            PingSweepRpcModule pingSweepRpcModule) {
        return new LocationAwarePingClientImpl(rpcClientFactory, pingProxyRpcModule, pingSweepRpcModule);
    }

    // -- Detector RPC --

    @Bean(name = "scanExecutor")
    public Executor scanExecutor() {
        return Executors.newCachedThreadPool();
    }

    @Bean
    public DetectorClientRpcModule detectorClientRpcModule() {
        return new DetectorClientRpcModule();
    }

    @Bean
    public LocationAwareDetectorClient locationAwareDetectorClient() {
        // Implements InitializingBean — Spring 7 calls afterPropertiesSet() natively
        return new LocationAwareDetectorClientRpcImpl();
    }

    // -- Discovery Core --

    @Bean
    public UnmanagedInterfaceFilter unmanagedInterfaceFilter(InterfaceToNodeCache cache) {
        return new UnmanagedInterfaceFilter(cache);
    }

    @Bean
    public RangeChunker rangeChunker(UnmanagedInterfaceFilter filter) {
        return new RangeChunker(filter);
    }

    @Bean
    public DiscoveryTaskExecutorImpl discoveryTaskExecutor() {
        // @Autowired fields: rangeChunker, locationAwarePingClient,
        //   eventForwarder (@Primary), locationAwareDetectorClient (optional)
        return new DiscoveryTaskExecutorImpl();
    }

    @Bean
    public Discovery discovery() {
        // @Autowired fields: discoveryConfigFactory, discoveryTaskExecutor,
        //   eventForwarder (@Qualifier("eventIpcManager"))
        return new Discovery();
    }

    @Bean
    public SmartLifecycle discoveryLifecycle(Discovery discovery) {
        return new SpringServiceDaemonSmartLifecycle(discovery);
    }

    // Note: Config reload via RELOAD_DAEMON_CONFIG_UEI events is NOT wired.
    // Discovery.reloadAndReStart() is private with no @EventHandler annotation.
    // To reload config, restart the container. This is acceptable for the
    // initial migration; event-driven reload can be added later.
}
