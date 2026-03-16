package org.opennms.core.daemon.sink.kafka;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.opennms.core.ipc.sink.api.Message;
import org.opennms.core.ipc.sink.api.SinkModule;
import org.opennms.core.ipc.sink.model.SinkMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * Bridges Kafka Sink topic consumption to the local {@link LocalMessageConsumerManager}.
 *
 * <p>Minion forwards messages (traps, syslogs, telemetry) to Kafka Sink topics
 * ({@code OpenNMS.Sink.{moduleId}}). This bridge consumes from that topic and
 * dispatches to the local consumer manager.</p>
 *
 * <p>Reusable by any daemon that consumes from Minion Sink topics.</p>
 */
public class KafkaSinkBridge implements InitializingBean, DisposableBean {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaSinkBridge.class);
    private static final Duration POLL_DURATION = Duration.ofMillis(100);

    private final LocalMessageConsumerManager consumerManager;
    private final String bootstrapServers;
    private final String groupId;

    private volatile SinkModule<?, Message> module;
    private volatile Thread consumerThread;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public KafkaSinkBridge(LocalMessageConsumerManager consumerManager,
                           String bootstrapServers,
                           String groupId) {
        this.consumerManager = consumerManager;
        this.bootstrapServers = bootstrapServers;
        this.groupId = groupId;
    }

    public void setModule(SinkModule<?, Message> module) {
        this.module = module;
    }

    @Override
    public void afterPropertiesSet() {
        consumerThread = new Thread(this::pollLoop, "kafka-sink-bridge");
        consumerThread.setDaemon(true);
        consumerThread.start();
    }

    private void pollLoop() {
        while (module == null && !closed.get()) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (closed.get()) return;

        final String topic = "OpenNMS.Sink." + module.getId();
        LOG.info("KafkaSinkBridge starting: topic={}, bootstrapServers={}, groupId={}",
                topic, bootstrapServers, groupId);

        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("group.id", groupId);
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", ByteArrayDeserializer.class.getName());
        props.put("enable.auto.commit", "true");
        props.put("auto.commit.interval.ms", "1000");
        props.put("auto.offset.reset", "latest");

        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(topic));

            while (!closed.get()) {
                try {
                    ConsumerRecords<String, byte[]> records = consumer.poll(POLL_DURATION);
                    for (ConsumerRecord<String, byte[]> record : records) {
                        try {
                            SinkMessage sinkMessage = SinkMessage.parseFrom(record.value());
                            byte[] content = sinkMessage.getContent().toByteArray();
                            Message message = module.unmarshal(content);
                            consumerManager.dispatch(module, message);
                        } catch (Exception e) {
                            LOG.warn("Error processing Sink message (offset={}): {}",
                                    record.offset(), e.getMessage(), e);
                        }
                    }
                } catch (Throwable t) {
                    if (closed.get()) break;
                    LOG.error("Error in KafkaSinkBridge poll loop: {}", t.getMessage(), t);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            if (!closed.get()) {
                LOG.error("Fatal error in KafkaSinkBridge: {}", t.getMessage(), t);
            }
        }
        LOG.info("KafkaSinkBridge stopped");
    }

    @Override
    public void destroy() {
        closed.set(true);
        if (consumerThread != null) {
            consumerThread.interrupt();
        }
    }
}
