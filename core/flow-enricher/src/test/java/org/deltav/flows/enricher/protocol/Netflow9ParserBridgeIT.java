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
import org.opennms.netmgt.telemetry.protocols.netflow.parser.Netflow9UdpParser;
import org.opennms.netmgt.telemetry.protocols.netflow.parser.ie.InformationElementDatabase;

import com.codahale.metrics.MetricRegistry;
import com.google.protobuf.ByteString;

/**
 * Integration test for the Netflow v9 parser bridge using a real
 * {@link Netflow9UdpParser}, real {@link InformationElementDatabase},
 * and captured wire bytes from horizon's own test fixtures. Verifies
 * the whole Stage 1 path works against the actual parser code —
 * including the per-exporter template cache in
 * {@code UdpSessionManager} — not a mocked stub.
 *
 * <p>Fixture strategy: the test feeds {@code nf9_template.dat} first
 * (a template-only packet — expected to produce zero flows because the
 * parser caches the template and waits for data), then {@code nf9_valid.dat}
 * (a data packet referencing that template — expected to produce at
 * least one flow because the parser decodes it against the cached template).
 * Both packets use the same simulated remote address so the
 * {@code UdpSessionManager} session key matches across calls.
 *
 * <p>This mirrors horizon's own {@code IllegalFlowTest} sequence
 * ({@code sendTemplate()} then {@code sendValid()}) and is the canonical
 * end-to-end verification that Phase 2's singleton-parser + per-call
 * capturing dispatcher architecture works.
 */
class Netflow9ParserBridgeIT {

    private static final String EXPORTER_ADDRESS = "192.0.2.100";
    private static final int EXPORTER_PORT = 54321;

    private ScheduledExecutorService scheduler;
    private Netflow9UdpParser parser;
    private Netflow9MessageProcessor processor;

    @BeforeEach
    void setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        ThreadLocalDispatcher tld = new ThreadLocalDispatcher();
        MetricRegistry metricRegistry = new MetricRegistry();
        InformationElementDatabase ied = new InformationElementDatabase(
                new org.opennms.netmgt.telemetry.protocols.netflow.parser.ipfix.InformationElementProvider(),
                new org.opennms.netmgt.telemetry.protocols.netflow.parser.netflow9.InformationElementProvider());

        parser = new Netflow9UdpParser(
                "test-netflow9",
                tld,
                new LoggingEventForwarder(),
                new StaticIdentity("test-id", "Default", "test"),
                new NoOpDnsResolver(),
                metricRegistry,
                ied);
        parser.start(scheduler);

        processor = new Netflow9MessageProcessor(
                parser,
                TestAdapterDefinitions.testAdapterDefinition("netflow9-it"),
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
     * Feed the template packet first; expect zero flows and no exceptions.
     * The parser should have cached the template in its UdpSessionManager,
     * keyed by EXPORTER_ADDRESS. The next call feeding a data packet
     * that references the same template should succeed.
     */
    @Test
    void templatePacketProducesZeroFlows() throws Exception {
        byte[] templateBytes = readFixture("fixtures/nf9_template.dat");
        TelemetryProtos.TelemetryMessageLog rawLog = rawLog(templateBytes);

        List<Flow> flows = processor.process(rawLog);

        // Template-only packet: zero data records decoded, zero flows.
        assertThat(flows).isEmpty();
    }

    /**
     * Feed template then data using the same parser instance. The first
     * call caches the template; the second call decodes the data packet
     * against the cached template and emits flows.
     *
     * <p>This is the critical test: it proves that the Phase 2 singleton
     * parser + per-call CapturingDispatcher architecture preserves
     * template state across calls, which is the whole reason parsers are
     * singletons and not per-call.
     */
    @Test
    void templatePlusDataPacketProducesFlows() throws Exception {
        byte[] templateBytes = readFixture("fixtures/nf9_template.dat");
        byte[] dataBytes = readFixture("fixtures/nf9_valid.dat");

        // First call: seed the template cache.
        List<Flow> templateResult = processor.process(rawLog(templateBytes));
        assertThat(templateResult).as("template packet produces zero flows").isEmpty();

        // Second call: data record referencing the cached template.
        List<Flow> dataResult = processor.process(rawLog(dataBytes));

        // The fixture's exact flow count is an implementation detail of
        // horizon's test data. What matters for this test is that the
        // parser successfully decoded at least one flow from the data
        // packet, proving the template cache persisted across the two
        // process() calls.
        assertThat(dataResult)
                .as("data packet with cached template produces at least one flow")
                .isNotEmpty();
        assertThat(dataResult).allSatisfy(flow -> {
            assertThat(flow.getProtocol()).isNotNull();
            // Netflow v9 packets from the horizon fixture should set these
            // basic fields. Don't assert specific values because the
            // fixture content is opaque to us; just verify they are present.
        });
    }

    /**
     * Feeding the data packet WITHOUT first seeding the template should
     * produce zero flows (the parser has no template to decode with) but
     * must not throw. This documents the expected behavior when data
     * arrives before its template.
     */
    @Test
    void dataPacketWithoutTemplateProducesZeroFlows() throws Exception {
        byte[] dataBytes = readFixture("fixtures/nf9_valid.dat");
        TelemetryProtos.TelemetryMessageLog rawLog = rawLog(dataBytes);

        List<Flow> flows = processor.process(rawLog);

        // No template cached -> parser cannot decode data records -> zero flows.
        // It must not throw.
        assertThat(flows).isEmpty();
    }

    private static TelemetryProtos.TelemetryMessageLog rawLog(byte[] wireBytes) {
        return TelemetryProtos.TelemetryMessageLog.newBuilder()
                .setLocation("Default")
                .setSystemId("test-system")
                .setSourceAddress(EXPORTER_ADDRESS)
                .setSourcePort(EXPORTER_PORT)
                .addMessage(TelemetryProtos.TelemetryMessage.newBuilder()
                        .setTimestamp(System.currentTimeMillis())
                        .setBytes(ByteString.copyFrom(wireBytes)))
                .build();
    }

    private static byte[] readFixture(String resourcePath) throws Exception {
        try (InputStream in = Netflow9ParserBridgeIT.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Fixture not found on classpath: " + resourcePath);
            }
            return in.readAllBytes();
        }
    }
}
