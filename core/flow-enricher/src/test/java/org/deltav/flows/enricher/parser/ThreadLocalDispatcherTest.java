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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.opennms.core.ipc.sink.api.AsyncDispatcher.DispatchStatus;
import org.opennms.netmgt.telemetry.api.receiver.TelemetryMessage;

class ThreadLocalDispatcherTest {

    private static TelemetryMessage msg(int lastByte) {
        return new TelemetryMessage(
                new InetSocketAddress("10.0.0.1", 1234),
                ByteBuffer.wrap(new byte[]{(byte) lastByte}));
    }

    @Test
    void sendDelegatesToInstalledCapturingDispatcher() {
        ThreadLocalDispatcher tld = new ThreadLocalDispatcher();
        CapturingDispatcher capture = new CapturingDispatcher();
        tld.install(capture);
        try {
            var future = tld.send(msg(1));
            assertThat(future.join()).isEqualTo(DispatchStatus.DISPATCHED);
            assertThat(capture.getCaptured()).hasSize(1);
        } finally {
            tld.clear();
        }
    }

    @Test
    void sendWithoutInstallThrowsClearError() {
        ThreadLocalDispatcher tld = new ThreadLocalDispatcher();
        assertThatThrownBy(() -> tld.send(msg(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No CapturingDispatcher installed");
    }

    @Test
    void clearRemovesDelegatesSoSubsequentSendFails() {
        ThreadLocalDispatcher tld = new ThreadLocalDispatcher();
        tld.install(new CapturingDispatcher());
        tld.clear();
        assertThatThrownBy(() -> tld.send(msg(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void currentReturnsInstalledDispatcherOrNull() {
        ThreadLocalDispatcher tld = new ThreadLocalDispatcher();
        assertThat(tld.current()).isNull();
        CapturingDispatcher capture = new CapturingDispatcher();
        tld.install(capture);
        try {
            assertThat(tld.current()).isSameAs(capture);
        } finally {
            tld.clear();
        }
        assertThat(tld.current()).isNull();
    }

    /**
     * Verify that {@code send()} works from a background thread — the key
     * behavioral difference from the previous {@code ThreadLocal} design.
     * The parser's internal thread pool calls {@code send()} on a thread that
     * never called {@code install()}; this test confirms the
     * {@link ThreadLocalDispatcher} serves those calls correctly.
     */
    @Test
    void sendFromBackgroundThreadSeesInstalledDispatcher() throws Exception {
        ThreadLocalDispatcher tld = new ThreadLocalDispatcher();
        CapturingDispatcher capture = new CapturingDispatcher();
        tld.install(capture);
        try {
            CountDownLatch done = new CountDownLatch(1);
            Thread background = new Thread(() -> {
                // Background thread calls send() — no install() on this thread.
                tld.send(msg(42));
                done.countDown();
            });
            background.start();
            assertThat(done.await(2, TimeUnit.SECONDS)).as("background send completed").isTrue();
            assertThat(capture.getCaptured()).hasSize(1);
        } finally {
            tld.clear();
        }
    }

    /**
     * Verify that a second {@code install()} call blocks until the first
     * caller clears. This is the serialization contract — two concurrent
     * process() calls on the same parser instance are interleaved safely.
     */
    @Test
    void secondInstallBlocksUntilFirstClears() throws Exception {
        ThreadLocalDispatcher tld = new ThreadLocalDispatcher();
        CapturingDispatcher captureA = new CapturingDispatcher();
        CapturingDispatcher captureB = new CapturingDispatcher();

        CountDownLatch firstInstalled = new CountDownLatch(1);
        CountDownLatch secondInstallBlocked = new CountDownLatch(1);
        CountDownLatch firstCleared = new CountDownLatch(1);

        tld.install(captureA);
        firstInstalled.countDown();

        Thread second = new Thread(() -> {
            secondInstallBlocked.countDown();
            tld.install(captureB);
            try {
                tld.send(msg(99));
            } finally {
                tld.clear();
            }
        });
        second.start();

        // Give the second thread a moment to hit the blocked install().
        assertThat(secondInstallBlocked.await(1, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(50); // let it block on the lock

        // Second thread should be blocked — captureB has nothing yet.
        assertThat(captureB.getCaptured()).isEmpty();

        // Now clear; the second thread should proceed.
        tld.clear();
        firstCleared.countDown();
        second.join(2000);

        assertThat(captureB.getCaptured()).hasSize(1);
    }

    @Test
    void getQueueSizeDelegatesOrReturnsZero() {
        ThreadLocalDispatcher tld = new ThreadLocalDispatcher();
        assertThat(tld.getQueueSize()).isZero();
        CapturingDispatcher capture = new CapturingDispatcher();
        tld.install(capture);
        try {
            capture.send(msg(1));
            capture.send(msg(2));
            assertThat(tld.getQueueSize()).isEqualTo(2);
        } finally {
            tld.clear();
        }
    }
}
