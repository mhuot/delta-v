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
package org.deltav.flows.enricher.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.deltav.flows.enricher.parser.LoggingEventForwarder;
import org.deltav.flows.enricher.parser.NoOpDnsResolver;
import org.deltav.flows.enricher.parser.StaticIdentity;
import org.deltav.flows.enricher.parser.ThreadLocalDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opennms.netmgt.flows.api.Flow;
import org.opennms.netmgt.telemetry.common.ipc.TelemetryProtos;
import org.opennms.netmgt.telemetry.protocols.netflow.parser.Netflow5UdpParser;

import com.codahale.metrics.MetricRegistry;
import com.google.protobuf.ByteString;

/**
 * Integration test for the Netflow v5 parser bridge using a real
 * {@link Netflow5UdpParser} and captured wire bytes from horizon's own test
 * fixture ({@code netflow5.dat}). Verifies the whole Stage 1 path works
 * against the actual parser code.
 *
 * <p>Netflow v5 is stateless per packet — no template cache, no multi-packet
 * session state. Each valid packet produces flow records independently.
 *
 * <p>The fixture contains 12 concatenated Netflow v5 UDP packets. We split
 * them into individual {@link TelemetryProtos.TelemetryMessage} entries in a
 * single log so the processor calls {@code parse()} once per packet, matching
 * real-world Minion dispatch.
 *
 * <p>Netflow v5 header layout (big-endian):
 * <ul>
 *   <li>Offset 0, 2 bytes: version (0x0005)</li>
 *   <li>Offset 2, 2 bytes: count (number of flow records in this packet)</li>
 *   <li>Offset 24+: flow records, each 48 bytes</li>
 * </ul>
 * Total packet size = 24 (header) + count * 48 (records).
 */
class Netflow5ParserBridgeIT {

    private static final String EXPORTER_ADDRESS = "192.0.2.200";
    private static final int EXPORTER_PORT = 54321;

    /**
     * Netflow v5 header is 24 bytes; each record is 48 bytes.
     * Packet size = HEADER_SIZE + count * RECORD_SIZE.
     */
    private static final int NF5_HEADER_SIZE = 24;
    private static final int NF5_RECORD_SIZE = 48;
    private static final int NF5_COUNT_OFFSET = 2;

    private ScheduledExecutorService scheduler;
    private Netflow5UdpParser parser;
    private Netflow5MessageProcessor processor;

    @BeforeEach
    void setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        ThreadLocalDispatcher tld = new ThreadLocalDispatcher();
        MetricRegistry metricRegistry = new MetricRegistry();

        parser = new Netflow5UdpParser(
                "test-netflow5",
                tld,
                new LoggingEventForwarder(),
                new StaticIdentity("test-id", "Default", "test"),
                new NoOpDnsResolver(),
                metricRegistry);
        parser.start(scheduler);

        processor = new Netflow5MessageProcessor(
                parser,
                TestAdapterDefinitions.testAdapterDefinition("netflow5-it"),
                metricRegistry,
                tld);
    }

    @AfterEach
    void tearDown() {
        if (parser != null) {
            parser.stop();
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    /**
     * Feeds all 12 Netflow v5 packets from the fixture as separate messages in
     * one log. Each packet is self-contained (no template dependency), so all
     * packets should produce flows. Assert at least one flow is returned and that
     * each flow has a non-null protocol, proving the parser bridge decoded
     * actual wire bytes end-to-end.
     */
    @Test
    void netflow5PacketProducesFlows() throws Exception {
        byte[] rawFixture = readFixture("fixtures/netflow5.dat");
        List<byte[]> packets = splitNetflow5Packets(rawFixture);

        assertThat(packets).as("fixture should contain at least one Netflow v5 packet").isNotEmpty();

        TelemetryProtos.TelemetryMessageLog log = buildLog(packets);
        List<Flow> flows = processor.process(log);

        assertThat(flows)
                .as("Netflow v5 fixture packets should produce at least one flow")
                .isNotEmpty();
        assertThat(flows).allSatisfy(flow ->
                assertThat(flow.getProtocol()).isNotNull());
    }

    /**
     * Splits a concatenated Netflow v5 binary blob into individual UDP packet
     * byte arrays. Each Netflow v5 packet starts with a 2-byte version (0x0005),
     * followed by a 2-byte count of flow records. Total size =
     * {@value #NF5_HEADER_SIZE} + count * {@value #NF5_RECORD_SIZE}.
     */
    private static List<byte[]> splitNetflow5Packets(byte[] raw) {
        ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN);
        List<byte[]> packets = new ArrayList<>();
        while (buf.hasRemaining()) {
            if (buf.remaining() < NF5_HEADER_SIZE) {
                break;
            }
            int packetStart = buf.position();
            // version at offset 0 (skip), count at offset 2
            buf.position(packetStart + NF5_COUNT_OFFSET);
            int count = Short.toUnsignedInt(buf.getShort());
            int packetSize = NF5_HEADER_SIZE + count * NF5_RECORD_SIZE;
            if (buf.remaining() + 2 < packetSize - NF5_COUNT_OFFSET) {
                // Not enough data for this packet — truncated fixture
                break;
            }
            byte[] packet = new byte[packetSize];
            buf.position(packetStart);
            buf.get(packet);
            packets.add(packet);
        }
        return packets;
    }

    private static TelemetryProtos.TelemetryMessageLog buildLog(List<byte[]> packets) {
        TelemetryProtos.TelemetryMessageLog.Builder builder = TelemetryProtos.TelemetryMessageLog.newBuilder()
                .setLocation("Default")
                .setSystemId("test-system")
                .setSourceAddress(EXPORTER_ADDRESS)
                .setSourcePort(EXPORTER_PORT);
        long now = System.currentTimeMillis();
        for (byte[] packet : packets) {
            builder.addMessage(TelemetryProtos.TelemetryMessage.newBuilder()
                    .setTimestamp(now)
                    .setBytes(ByteString.copyFrom(packet)));
        }
        return builder.build();
    }

    private static byte[] readFixture(String resourcePath) throws Exception {
        try (InputStream in = Netflow5ParserBridgeIT.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Fixture not found on classpath: " + resourcePath);
            }
            return in.readAllBytes();
        }
    }
}
