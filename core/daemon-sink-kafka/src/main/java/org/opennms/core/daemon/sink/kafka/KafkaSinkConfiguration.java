package org.opennms.core.daemon.sink.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaSinkConfiguration {

    @Value("${opennms.kafka.bootstrap-servers:kafka:9092}")
    private String bootstrapServers;

    @Value("${opennms.kafka.sink.consumer-group:opennms-sink}")
    private String sinkConsumerGroup;

    @Bean
    public LocalMessageConsumerManager localMessageConsumerManager() {
        return new LocalMessageConsumerManager();
    }

    @Bean(initMethod = "afterPropertiesSet", destroyMethod = "destroy")
    public KafkaSinkBridge kafkaSinkBridge(LocalMessageConsumerManager consumerManager) {
        var bridge = new KafkaSinkBridge(consumerManager, bootstrapServers, sinkConsumerGroup);
        consumerManager.setKafkaSinkBridge(bridge);
        return bridge;
    }
}
