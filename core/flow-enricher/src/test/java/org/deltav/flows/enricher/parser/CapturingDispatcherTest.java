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
package org.deltav.flows.enricher.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.opennms.core.ipc.sink.api.AsyncDispatcher.DispatchStatus;
import org.opennms.netmgt.telemetry.api.receiver.TelemetryMessage;

class CapturingDispatcherTest {

    @Test
    void capturesMessagesInOrder() {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        TelemetryMessage m1 = new TelemetryMessage(
                new InetSocketAddress("10.0.0.1", 1234),
                ByteBuffer.wrap(new byte[]{1, 2, 3}));
        TelemetryMessage m2 = new TelemetryMessage(
                new InetSocketAddress("10.0.0.2", 5678),
                ByteBuffer.wrap(new byte[]{4, 5}));

        CompletableFuture<DispatchStatus> f1 = dispatcher.send(m1);
        CompletableFuture<DispatchStatus> f2 = dispatcher.send(m2);

        assertThat(f1).isCompleted();
        assertThat(f2).isCompleted();
        assertThat(f1.join()).isEqualTo(DispatchStatus.DISPATCHED);
        assertThat(f2.join()).isEqualTo(DispatchStatus.DISPATCHED);
        assertThat(dispatcher.getCaptured()).containsExactly(m1, m2);
    }

    @Test
    void getCapturedReturnsUnmodifiableView() {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        dispatcher.send(new TelemetryMessage(
                new InetSocketAddress("10.0.0.1", 1234),
                ByteBuffer.wrap(new byte[]{1})));

        var captured = dispatcher.getCaptured();
        assertThat(captured).hasSize(1);
        // Mutating the returned list must not affect the internal state
        // and must throw to make accidental writes obvious.
        assertThatThrownBy(() -> captured.add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getQueueSizeReflectsCapturedCount() {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        assertThat(dispatcher.getQueueSize()).isZero();
        dispatcher.send(new TelemetryMessage(
                new InetSocketAddress("10.0.0.1", 1234),
                ByteBuffer.wrap(new byte[]{1})));
        assertThat(dispatcher.getQueueSize()).isEqualTo(1);
    }

    @Test
    void closeIsNoop() throws Exception {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        dispatcher.send(new TelemetryMessage(
                new InetSocketAddress("10.0.0.1", 1234),
                ByteBuffer.wrap(new byte[]{1})));
        dispatcher.close();
        // After close, the captured list is still accessible
        assertThat(dispatcher.getCaptured()).hasSize(1);
    }
}
