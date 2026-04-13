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
import org.opennms.netmgt.telemetry.protocols.netflow.parser.IpfixUdpParser;
import org.opennms.netmgt.telemetry.protocols.netflow.parser.ie.InformationElementDatabase;

import com.codahale.metrics.MetricRegistry;
import com.google.protobuf.ByteString;

/**
 * Integration test for the IPFIX parser bridge using a real
 * {@link IpfixUdpParser}, real {@link InformationElementDatabase},
 * and captured wire bytes from horizon's own test fixture ({@code ipfix.dat}).
 * Verifies the whole Stage 1 path works against the actual parser code —
 * including the per-exporter template cache in {@code UdpSessionManager}.
 *
 * <p>Fixture strategy: {@code ipfix.dat} contains 3 concatenated IPFIX UDP
 * packets. The first packet (484 bytes) is a combined template+options packet.
 * The subsequent packets (64 and 240 bytes) contain data records referencing
 * those templates. All 3 packets are fed as separate
 * {@link TelemetryProtos.TelemetryMessage} entries in a single log, using the
 * same simulated exporter address so the {@code UdpSessionManager} reuses the
 * same session across all 3 parse calls, enabling the template-then-data
 * sequence that produces decodable flows.
 *
 * <p>IPFIX header layout (big-endian):
 * <ul>
 *   <li>Offset 0, 2 bytes: version (0x000a)</li>
 *   <li>Offset 2, 2 bytes: total message length (including this header)</li>
 *   <li>Offset 4, 4 bytes: export time</li>
 *   <li>Offset 8, 4 bytes: sequence number</li>
 *   <li>Offset 12, 4 bytes: observation domain id</li>
 * </ul>
 */
class IpfixParserBridgeIT {

    private static final String EXPORTER_ADDRESS = "192.0.2.100";
    private static final int EXPORTER_PORT = 54321;

    /**
     * IPFIX header is 16 bytes; the 2-byte length field at offset 2 gives
     * the total message length including the header.
     */
    private static final int IPFIX_LENGTH_OFFSET = 2;

    private ScheduledExecutorService scheduler;
    private IpfixUdpParser parser;
    private IpfixMessageProcessor processor;

    @BeforeEach
    void setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        ThreadLocalDispatcher tld = new ThreadLocalDispatcher();
        MetricRegistry metricRegistry = new MetricRegistry();
        InformationElementDatabase ied = new InformationElementDatabase(
                new org.opennms.netmgt.telemetry.protocols.netflow.parser.ipfix.InformationElementProvider(),
                new org.opennms.netmgt.telemetry.protocols.netflow.parser.netflow9.InformationElementProvider());

        parser = new IpfixUdpParser(
                "test-ipfix",
                tld,
                new LoggingEventForwarder(),
                new StaticIdentity("test-id", "Default", "test"),
                new NoOpDnsResolver(),
                metricRegistry,
                ied);
        parser.start(scheduler);

        processor = new IpfixMessageProcessor(
                parser,
                TestAdapterDefinitions.testAdapterDefinition("ipfix-it"),
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
     * Feeds all 3 IPFIX packets from the fixture as separate messages in one
     * log, using the same source address so the session manager caches the
     * template from the first packet and uses it to decode the data packets.
     * Assert that at least one flow is returned from the combined batch,
     * proving the template-caching bridge works end-to-end.
     */
    @Test
    void ipfixPacketProducesFlows() throws Exception {
        byte[] rawFixture = readFixture("fixtures/ipfix.dat");
        List<byte[]> packets = splitIpfixPackets(rawFixture);

        assertThat(packets).as("fixture should contain at least one IPFIX packet").isNotEmpty();

        TelemetryProtos.TelemetryMessageLog log = buildLog(packets);
        List<Flow> flows = processor.process(log);

        assertThat(flows)
                .as("IPFIX fixture packets (template + data) should produce at least one flow")
                .isNotEmpty();
    }

    /**
     * Splits a concatenated IPFIX binary blob into individual UDP packet byte
     * arrays. Each IPFIX message begins with a 2-byte version (0x000a) followed
     * by a 2-byte total length (big-endian unsigned, includes the 16-byte
     * header).
     */
    private static List<byte[]> splitIpfixPackets(byte[] raw) {
        ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN);
        List<byte[]> packets = new ArrayList<>();
        while (buf.hasRemaining()) {
            if (buf.remaining() < 4) {
                break;
            }
            int packetStart = buf.position();
            // version at offset 0 (skip), length at offset 2
            buf.position(packetStart + IPFIX_LENGTH_OFFSET);
            int totalLength = Short.toUnsignedInt(buf.getShort());
            if (totalLength <= 0 || totalLength > buf.remaining() + 2) {
                break;
            }
            byte[] packet = new byte[totalLength];
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
        try (InputStream in = IpfixParserBridgeIT.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Fixture not found on classpath: " + resourcePath);
            }
            return in.readAllBytes();
        }
    }
}
