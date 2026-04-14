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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
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

    @Test
    void sendRejectsNullMessage() {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        assertThatThrownBy(() -> dispatcher.send(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("message");
    }

    /**
     * Stress test for the thread-safety contract documented on
     * {@link CapturingDispatcher}. Reproduces the production race observed on
     * the Netflow9 path: horizon's {@code Netflow9UdpParser} dispatches flow
     * records on an internal thread pool, so a single batch parse fans
     * {@code dispatcher.send(...)} calls across many worker threads. The
     * earlier {@link java.util.ArrayList}-backed implementation either threw
     * {@code ArrayIndexOutOfBoundsException} on capacity expansion or left
     * {@code null} slots that downstream iteration tripped over with NPE in
     * {@code AbstractProtocolMessageProcessor.synthesizeParsedLog}. With the
     * synchronised implementation, all messages must be captured and every
     * captured slot must be non-null. A {@link CountDownLatch} fires every
     * worker simultaneously to maximise contention.
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void concurrentSendsAreThreadSafe() throws Exception {
        final int threadCount = 16;
        final int sendsPerThread = 2_000;
        final int totalSends = threadCount * sendsPerThread;

        CapturingDispatcher dispatcher = new CapturingDispatcher();
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadIndex = t;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < sendsPerThread; i++) {
                        TelemetryMessage msg = new TelemetryMessage(
                                new InetSocketAddress("10.0." + threadIndex + "." + (i & 0xff), 1234),
                                ByteBuffer.wrap(new byte[]{(byte) threadIndex, (byte) i}));
                        dispatcher.send(msg);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        boolean finished = done.await(20, TimeUnit.SECONDS);
        pool.shutdown();
        assertThat(finished).as("all worker threads completed within 20s").isTrue();

        List<TelemetryMessage> captured = dispatcher.getCaptured();
        assertThat(captured)
                .as("every send() call must produce exactly one captured entry")
                .hasSize(totalSends);
        assertThat(captured)
                .as("no captured slot should be null — that was the production bug signature")
                .doesNotContainNull();
        assertThat(dispatcher.getQueueSize())
                .as("getQueueSize() must agree with getCaptured().size() under concurrency")
                .isEqualTo(totalSends);
    }

    @Test
    void getCapturedReturnsImmutableSnapshot() {
        CapturingDispatcher dispatcher = new CapturingDispatcher();
        dispatcher.send(new TelemetryMessage(
                new InetSocketAddress("10.0.0.1", 1234),
                ByteBuffer.wrap(new byte[]{1})));

        List<TelemetryMessage> snapshot = dispatcher.getCaptured();
        // Subsequent sends must NOT mutate the snapshot the caller already holds.
        dispatcher.send(new TelemetryMessage(
                new InetSocketAddress("10.0.0.2", 5678),
                ByteBuffer.wrap(new byte[]{2})));

        assertThat(snapshot).hasSize(1);
        assertThat(dispatcher.getCaptured()).hasSize(2);
    }
}
