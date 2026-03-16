package org.opennms.netmgt.syslogd.boot;

import javax.sql.DataSource;

import com.codahale.metrics.MetricRegistry;

import org.opennms.core.daemon.common.JdbcDistPollerDao;
import org.opennms.core.daemon.common.JdbcInterfaceToNodeCache;
import org.opennms.netmgt.config.SyslogdConfigFactory;
import org.opennms.netmgt.config.SyslogdConfig;
import org.opennms.netmgt.dao.api.DistPollerDao;
import org.opennms.netmgt.dao.api.InterfaceToNodeCache;
import org.opennms.netmgt.provision.LocationAwareDnsLookupClient;
import org.opennms.netmgt.syslogd.SyslogSinkConsumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot @Configuration that wires all Syslogd beans.
 *
 * <p>Replaces the Karaf-era {@code applicationContext-daemon-loader-syslogd.xml}.</p>
 *
 * <p>{@code SyslogSinkConsumer} implements {@code InitializingBean} -- Spring
 * calls {@code afterPropertiesSet()} natively after {@code @Autowired} injection.
 * No {@code initMethod} workaround needed (unlike Trapd's {@code javax.annotation.PostConstruct}).</p>
 */
@Configuration
public class SyslogdConfiguration {

    @Value("${opennms.syslogd.dnscache.config:maximumSize=1000,expireAfterWrite=8h}")
    private String dnsCacheConfig;

    @Bean
    public SyslogdConfig syslogdConfig() throws Exception {
        return new SyslogdConfigFactory();
    }

    @Bean
    public MetricRegistry syslogdMetricRegistry() {
        return new MetricRegistry();
    }

    @Bean
    public DistPollerDao distPollerDao(DataSource dataSource) {
        return new JdbcDistPollerDao(dataSource);
    }

    @Bean
    public InterfaceToNodeCache interfaceToNodeCache(DataSource dataSource) {
        // AbstractInterfaceToNodeCache.setInstance() is called inside refresh(),
        // which fires immediately via @Scheduled(initialDelayString = "0").
        // This ensures the singleton is set after data is loaded, not before.
        return new JdbcInterfaceToNodeCache(dataSource);
    }

    @Bean
    public LocationAwareDnsLookupClient locationAwareDnsLookupClient() {
        return new LocalDnsLookupClient();
    }

    @Bean
    public SyslogSinkConsumer syslogSinkConsumer(MetricRegistry metricRegistry) {
        // Bridge DNS cache config for SyslogSinkConsumer constructor
        System.setProperty("org.opennms.netmgt.syslogd.dnscache.config", dnsCacheConfig);

        // SyslogSinkConsumer implements InitializingBean -- Spring calls
        // afterPropertiesSet() after @Autowired injection completes.
        // afterPropertiesSet() registers consumer with MessageConsumerManager,
        // which triggers KafkaSinkBridge.setModule(), starting Kafka polling.
        //
        // Note: SyslogSinkConsumer.getModule() internally creates its own
        // SyslogSinkModule using its @Autowired syslogdConfig and distPollerDao.
        // No separate SyslogSinkModule @Bean is needed.
        return new SyslogSinkConsumer(metricRegistry);
    }
}
