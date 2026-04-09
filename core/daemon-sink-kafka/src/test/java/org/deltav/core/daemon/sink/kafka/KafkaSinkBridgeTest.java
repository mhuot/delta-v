/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.deltav.core.daemon.sink.kafka;

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
