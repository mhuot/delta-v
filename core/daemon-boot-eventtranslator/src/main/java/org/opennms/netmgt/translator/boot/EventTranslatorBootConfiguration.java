package org.opennms.netmgt.translator.boot;

import javax.sql.DataSource;

import org.opennms.core.daemon.common.DaemonSmartLifecycle;
import org.opennms.core.db.DataSourceFactory;
import org.opennms.netmgt.config.EventTranslatorConfigFactory;
import org.opennms.netmgt.events.api.EventIpcManager;
import org.opennms.netmgt.translator.EventTranslator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventTranslatorBootConfiguration {

    @Bean
    public EventTranslatorConfigFactory eventTranslatorConfig(DataSource dataSource) throws Exception {
        String opennmsHome = System.getProperty("opennms.home",
                System.getenv().getOrDefault("OPENNMS_HOME", "/opt/sentinel"));
        System.setProperty("opennms.home", opennmsHome);

        DataSourceFactory.setInstance(dataSource);
        EventTranslatorConfigFactory.init();
        return (EventTranslatorConfigFactory) EventTranslatorConfigFactory.getInstance();
    }

    @Bean
    public EventTranslator eventTranslator(
            @Qualifier("eventIpcManager") EventIpcManager eventIpcManager,
            EventTranslatorConfigFactory config,
            DataSource dataSource) {
        var translator = new EventTranslator();
        translator.setEventManager(eventIpcManager);
        translator.setConfig(config);
        translator.setDataSource(dataSource);
        return translator;
    }

    @Bean
    public SmartLifecycle eventTranslatorLifecycle(EventTranslator eventTranslator) {
        return new DaemonSmartLifecycle(eventTranslator);
    }
}
