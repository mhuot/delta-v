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
    void clearRemovesThreadLocalSoSubsequentSendFails() {
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

    @Test
    void twoThreadsHaveIndependentInstalls() throws Exception {
        ThreadLocalDispatcher tld = new ThreadLocalDispatcher();
        CapturingDispatcher captureA = new CapturingDispatcher();
        CapturingDispatcher captureB = new CapturingDispatcher();
        tld.install(captureA);
        try {
            Thread t = new Thread(() -> {
                tld.install(captureB);
                try {
                    tld.send(msg(42));
                } finally {
                    tld.clear();
                }
            });
            t.start();
            t.join();
            // Thread B's send landed in captureB; captureA stays empty.
            assertThat(captureA.getCaptured()).isEmpty();
            assertThat(captureB.getCaptured()).hasSize(1);
            // Direct assertion: the main thread's ThreadLocal slot still holds
            // captureA after the worker finished. This is the actual
            // cross-thread-isolation invariant — a bug that mutated the parent's
            // slot would not be caught by the getCaptured() checks above alone.
            assertThat(tld.current()).isSameAs(captureA);
        } finally {
            tld.clear();
        }
    }

    @Test
    void installTwiceOnSameThreadThrows() {
        ThreadLocalDispatcher tld = new ThreadLocalDispatcher();
        tld.install(new CapturingDispatcher());
        try {
            assertThatThrownBy(() -> tld.install(new CapturingDispatcher()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already installed");
        } finally {
            tld.clear();
        }
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
