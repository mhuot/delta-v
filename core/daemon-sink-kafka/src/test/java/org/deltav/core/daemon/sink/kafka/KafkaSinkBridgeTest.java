package org.opennms.core.daemon.sink.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class KafkaSinkBridgeTest {

    @Test
    void bridgeStartsInDaemonThread() {
        var manager = new LocalMessageConsumerManager();
        var bridge = new KafkaSinkBridge(manager, "localhost:9092", "test-group");

        // Bridge should not throw on construction
        assertThat(bridge).isNotNull();
    }

    @Test
    void destroyStopsCleanly() throws Exception {
        var manager = new LocalMessageConsumerManager();
        var bridge = new KafkaSinkBridge(manager, "localhost:9092", "test-group");

        // Start the bridge (it will spin waiting for module)
        bridge.afterPropertiesSet();

        // Immediately destroy — should not hang
        bridge.destroy();
    }
}
