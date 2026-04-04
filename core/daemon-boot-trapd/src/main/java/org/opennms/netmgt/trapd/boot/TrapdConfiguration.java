package org.opennms.netmgt.trapd.boot;

import java.io.File;
import java.io.IOException;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;

import org.opennms.core.daemon.common.DaemonEventConfDao;
import org.opennms.netmgt.config.api.EventConfDao;
import org.opennms.netmgt.dao.api.DistPollerDao;
import org.opennms.netmgt.dao.api.InterfaceToNodeCache;
import org.opennms.core.ipc.sink.api.MessageConsumerManager;
import org.opennms.netmgt.config.TrapdConfig;
import org.opennms.netmgt.events.api.EventForwarder;
import org.opennms.netmgt.trapd.TrapdConfigBean;
import org.opennms.netmgt.trapd.TrapSinkConsumer;
import org.opennms.netmgt.trapd.TrapSinkModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot @Configuration that wires all Trapd beans.
 *
 * <p>Replaces the Karaf-era {@code applicationContext-daemon-loader-trapd.xml}.</p>
 *
 * <p>All beans use constructor injection. {@code TrapSinkConsumer} receives its
 * dependencies (MessageConsumerManager, EventConfDao, EventForwarder,
 * InterfaceToNodeCache, TrapdConfig, DistPollerDao) via constructor parameters.</p>
 *
 * <p>Note: {@code EventCreator} is package-private in {@code org.opennms.netmgt.trapd}
 * and is instantiated inside {@code TrapSinkConsumer.init()}, not here.</p>
 */
@Configuration
public class TrapdConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(TrapdConfiguration.class);

    private static final XmlMapper XML_MAPPER;
    static {
        XML_MAPPER = XmlMapper.builder()
                .defaultUseWrapper(false)
                .build();
        XML_MAPPER.registerModule(new JaxbAnnotationModule());
        XML_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Bean
    public TrapdConfig trapdConfig(@Value("${opennms.home}") String opennmsHome) throws IOException {
        var configFile = new File(opennmsHome, "etc/trapd-configuration.xml");
        if (configFile.exists()) {
            LOG.info("Loading trapd configuration from {}", configFile);
            var xmlConfig = XML_MAPPER.readValue(configFile,
                    org.opennms.netmgt.config.trapd.TrapdConfiguration.class);
            return new TrapdConfigBean(xmlConfig);
        }
        LOG.warn("trapd-configuration.xml not found at {}, using defaults", configFile);
        return new TrapdConfigBean();
    }

    @Bean
    public EventConfDao eventConfDao() {
        return new DaemonEventConfDao();
    }

    @Bean
    public DistPollerDao distPollerDao(DataSource dataSource) {
        return new org.opennms.core.daemon.common.JdbcDistPollerDao(dataSource);
    }

    @Bean
    public InterfaceToNodeCache interfaceToNodeCache(DataSource dataSource) {
        return new org.opennms.core.daemon.common.JdbcInterfaceToNodeCache(dataSource);
    }

    @Bean
    public TrapSinkModule trapSinkModule(TrapdConfig config, DistPollerDao distPollerDao) {
        return new TrapSinkModule(config, distPollerDao.whoami());
    }

    /**
     * Creates the TrapSinkConsumer bean with all dependencies via constructor injection.
     *
     * <p>IMPORTANT: TrapSinkConsumer uses {@code javax.annotation.PostConstruct}
     * which Spring Boot 4 (Spring 7 / Jakarta EE) does NOT recognize. Spring 7
     * only processes {@code jakarta.annotation.PostConstruct}. We use
     * {@code initMethod} to explicitly trigger {@code init()} after construction.</p>
     */
    @Bean(initMethod = "init")
    public TrapSinkConsumer trapSinkConsumer(MessageConsumerManager messageConsumerManager,
                                             EventConfDao eventConfDao,
                                             @Qualifier("eventIpcManager") EventForwarder eventForwarder,
                                             InterfaceToNodeCache interfaceToNodeCache,
                                             TrapdConfig trapdConfig,
                                             DistPollerDao distPollerDao) {
        return new TrapSinkConsumer(messageConsumerManager, eventConfDao, eventForwarder,
                interfaceToNodeCache, trapdConfig, distPollerDao);
    }
}
