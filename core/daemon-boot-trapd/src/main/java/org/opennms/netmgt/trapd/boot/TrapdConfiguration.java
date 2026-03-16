package org.opennms.netmgt.trapd.boot;

import javax.sql.DataSource;

import org.opennms.netmgt.config.DefaultEventConfDao;
import org.opennms.netmgt.config.api.EventConfDao;
import org.opennms.netmgt.dao.api.DistPollerDao;
import org.opennms.netmgt.dao.api.InterfaceToNodeCache;
import org.opennms.netmgt.config.TrapdConfig;
import org.opennms.netmgt.trapd.TrapdConfigBean;
import org.opennms.netmgt.trapd.TrapSinkConsumer;
import org.opennms.netmgt.trapd.TrapSinkModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot @Configuration that wires all Trapd beans.
 *
 * <p>Replaces the Karaf-era {@code applicationContext-daemon-loader-trapd.xml}.</p>
 *
 * <p>{@code TrapSinkConsumer} uses {@code @Autowired} field injection internally.
 * Spring's {@code AutowiredAnnotationBeanPostProcessor} processes these annotations
 * on beans returned from {@code @Bean} methods, injecting the matching beans from
 * the application context (MessageConsumerManager, EventConfDao, EventIpcManager,
 * InterfaceToNodeCache, TrapdConfig, DistPollerDao).</p>
 *
 * <p>Note: {@code EventCreator} is package-private in {@code org.opennms.netmgt.trapd}
 * and is instantiated inside {@code TrapSinkConsumer.init()}, not here.</p>
 */
@Configuration
public class TrapdConfiguration {

    @Bean
    public TrapdConfig trapdConfig() {
        return new TrapdConfigBean();
    }

    @Bean
    public EventConfDao eventConfDao() {
        return new DefaultEventConfDao();
    }

    @Bean
    public InterfaceToNodeCache interfaceToNodeCache(DataSource dataSource) {
        return new JdbcInterfaceToNodeCache(dataSource);
    }

    @Bean
    public TrapSinkModule trapSinkModule(TrapdConfig config, DistPollerDao distPollerDao) {
        return new TrapSinkModule(config, distPollerDao.whoami());
    }

    @Bean
    public TrapSinkConsumer trapSinkConsumer() {
        // TrapSinkConsumer uses @Autowired field injection for its 6 dependencies.
        // Spring's AutowiredAnnotationBeanPostProcessor injects them after construction.
        // @PostConstruct init() runs after injection, registering with MessageConsumerManager,
        // which notifies KafkaSinkBridge.setModule(), starting Kafka polling.
        return new TrapSinkConsumer();
    }
}
