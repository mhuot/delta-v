package org.deltav.core.daemon.sink.kafka;

import org.opennms.core.ipc.sink.api.Message;
import org.opennms.core.ipc.sink.api.SinkModule;
import org.opennms.core.ipc.sink.common.AbstractMessageConsumerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-process MessageConsumerManager for Spring Boot daemon containers.
 *
 * <p>When a consumer registers (e.g., TrapSinkConsumer), this manager notifies
 * the {@link KafkaSinkBridge} with the registered module so it can start
 * consuming from the Kafka Sink topic.</p>
 */
public class LocalMessageConsumerManager extends AbstractMessageConsumerManager {

    private static final Logger LOG = LoggerFactory.getLogger(LocalMessageConsumerManager.class);

    private KafkaSinkBridge kafkaSinkBridge;

    public void setKafkaSinkBridge(KafkaSinkBridge kafkaSinkBridge) {
        this.kafkaSinkBridge = kafkaSinkBridge;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void startConsumingForModule(SinkModule<?, Message> module) {
        LOG.info("Local sink consumer started for module: {}", module.getId());
        if (kafkaSinkBridge != null) {
            kafkaSinkBridge.setModule(module);
        }
    }

    @Override
    protected void stopConsumingForModule(SinkModule<?, Message> module) {
        LOG.info("Local sink consumer stopped for module: {}", module.getId());
    }
}
