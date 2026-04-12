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
package org.deltav.minion.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import org.deltav.minion.telemetry.proto.TelemetryProtos.TelemetryMessageLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opennms.core.ipc.sink.api.AsyncDispatcher;
import org.opennms.core.ipc.sink.api.AsyncDispatcher.DispatchStatus;

class FlowUdpListenerTest {

    private FlowUdpListener listener;
    private Map<FlowProtocol, AsyncDispatcher<FlowTelemetryMessage>> dispatchers;
    private Map<FlowProtocol, List<FlowTelemetryMessage>> captured;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        captured = new EnumMap<>(FlowProtocol.class);
        dispatchers = new EnumMap<>(FlowProtocol.class);
        for (FlowProtocol p : FlowProtocol.values()) {
            CopyOnWriteArrayList<FlowTelemetryMessage> list = new CopyOnWriteArrayList<>();
            captured.put(p, list);
            dispatchers.put(p, newCapturingDispatcher(list));
        }
        // Bind to port 0 to get an ephemeral port from the kernel
        listener = new FlowUdpListener(0, "127.0.0.1", dispatchers, "Default", "minion-test-01");
        listener.start();
        port = listener.getBoundPort();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (listener != null) {
            listener.stop();
        }
    }

    @Test
    void receivesNetflow9DatagramAndDispatchesToCorrectProtocol() throws Exception {
        byte[] payload = new byte[] { 0x00, 0x09, 0x00, 0x01, 0x02, 0x03 };

        sendDatagram(payload);

        await().atMost(Duration.ofSeconds(5)).until(() -> !captured.get(FlowProtocol.NETFLOW_9).isEmpty());

        List<FlowTelemetryMessage> netflow9 = captured.get(FlowProtocol.NETFLOW_9);
        assertThat(netflow9).hasSize(1);

        TelemetryMessageLog log = netflow9.get(0).getTelemetryMessageLog();
        assertThat(log.getLocation()).isEqualTo("Default");
        assertThat(log.getSystemId()).isEqualTo("minion-test-01");
        assertThat(log.getSourceAddress()).isEqualTo("127.0.0.1");
        assertThat(log.getSourcePort()).isGreaterThan(0);
        assertThat(log.getMessageCount()).isEqualTo(1);
        assertThat(log.getMessage(0).getBytes().toByteArray()).isEqualTo(payload);
        assertThat(log.getMessage(0).getTimestamp()).isGreaterThan(0L);

        // Other protocols should remain empty
        assertThat(captured.get(FlowProtocol.NETFLOW_5)).isEmpty();
        assertThat(captured.get(FlowProtocol.IPFIX)).isEmpty();
        assertThat(captured.get(FlowProtocol.SFLOW)).isEmpty();
    }

    @Test
    void receivesSflowDatagram() throws Exception {
        byte[] payload = new byte[] { 0x00, 0x00, 0x00, 0x05, 0x10, 0x20 };

        sendDatagram(payload);

        await().atMost(Duration.ofSeconds(5)).until(() -> !captured.get(FlowProtocol.SFLOW).isEmpty());
        assertThat(captured.get(FlowProtocol.SFLOW)).hasSize(1);
    }

    @Test
    void dropsUnknownProtocolDatagrams() throws Exception {
        byte[] payload = new byte[] { 0x00, 0x01, 0x00, 0x00 };

        sendDatagram(payload);

        // Give the listener time to process; then verify no dispatcher received anything
        Thread.sleep(200);
        for (FlowProtocol p : FlowProtocol.values()) {
            assertThat(captured.get(p)).isEmpty();
        }
    }

    private void sendDatagram(byte[] payload) throws Exception {
        try (DatagramSocket sock = new DatagramSocket()) {
            DatagramPacket packet = new DatagramPacket(
                    payload, payload.length, InetAddress.getByName("127.0.0.1"), port);
            sock.send(packet);
        }
    }

    private AsyncDispatcher<FlowTelemetryMessage> newCapturingDispatcher(List<FlowTelemetryMessage> sink) {
        return new AsyncDispatcher<>() {
            @Override
            public CompletableFuture<DispatchStatus> send(FlowTelemetryMessage message) {
                sink.add(message);
                return CompletableFuture.completedFuture(DispatchStatus.DISPATCHED);
            }

            @Override
            public int getQueueSize() {
                return 0;
            }

            @Override
            public void close() {
                // no-op
            }
        };
    }
}
