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
import java.util.concurrent.TimeUnit;

import org.deltav.flows.enricher.parser.NoOpDnsResolver;
import org.deltav.flows.enricher.parser.ThreadLocalDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.opennms.netmgt.flows.api.Flow;
import org.opennms.netmgt.telemetry.common.ipc.TelemetryProtos;
import org.opennms.netmgt.telemetry.protocols.sflow.parser.SFlowUdpParser;

import com.codahale.metrics.MetricRegistry;
import com.google.protobuf.ByteString;

/**
 * Integration test for the sFlow parser bridge using a real
 * {@link SFlowUdpParser} and captured wire bytes from horizon's own test
 * fixture ({@code sflow3.dat}). Verifies the whole Stage 1 path works
 * against the actual parser code.
 *
 * <p>sFlow is stateless per packet — each sFlow v5 datagram carries inline
 * type descriptors (sample type + data format fields), so no template cache
 * is required. A single valid sFlow v5 datagram containing flow samples is
 * sufficient to produce flow records.
 *
 * <p>Fixture selection rationale: {@code sflow1.dat} and {@code sflow2.dat}
 * from horizon's test resources contain only sFlow Counter Samples (format 2),
 * which carry interface statistics rather than IP flow data.
 * {@code SFlowAdapter.convertDocument()} only processes Flow Samples
 * (format {@code "0:1"}) and Expanded Flow Samples (format {@code "0:3"}),
 * so counter-only packets correctly produce zero flows — there is no flow
 * data to extract. {@code sflow3.dat} is used here because it contains five
 * Flow Samples (format 1) with IPv4 records, which the adapter converts to
 * {@link org.opennms.netmgt.flows.api.Flow} objects.
 *
 * <p>The fixture is a single sFlow v5 UDP datagram (1252 bytes). One
 * {@code process()} call feeds it as one
 * {@link TelemetryProtos.TelemetryMessage}, exercises the Stage 1 parsing
 * path through {@code SFlowUdpParser} (which serialises to BSON and
 * dispatches via {@link ThreadLocalDispatcher}), then Stage 2 through
 * {@code SFlowAdapter} (which deserialises the BSON and extracts IP flows).
 *
 * <p><strong>Important:</strong> {@link SFlowUdpParser} uses
 * {@code LogPreservingThreadFactory} from {@code opennms-util}, which is on
 * the test classpath (added in Task 9 to unblock horizon parsers in test
 * scope). The parser is not wired into the production Spring context but is
 * fully instantiable and functional in this integration test.
 */
class SFlowParserBridgeIT {

    private static final String EXPORTER_ADDRESS = "192.0.2.50";
    private static final int EXPORTER_PORT = 6343;

    private ScheduledExecutorService scheduler;
    private SFlowUdpParser parser;
    private SFlowMessageProcessor processor;

    @BeforeEach
    void setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        ThreadLocalDispatcher tld = new ThreadLocalDispatcher();
        MetricRegistry metricRegistry = new MetricRegistry();

        parser = new SFlowUdpParser(
                "test-sflow",
                tld,
                new NoOpDnsResolver());
        parser.start(scheduler);

        processor = new SFlowMessageProcessor(
                parser,
                TestAdapterDefinitions.testAdapterDefinition("sflow-it"),
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
     * Feeds the sFlow v5 datagram from the fixture as one message and
     * asserts that at least one flow is returned. sFlow is stateless —
     * each datagram carries inline type descriptors — so no template priming
     * is required. {@code sflow3.dat} contains five Flow Samples with IPv4
     * headers, so a non-empty result proves the parser bridge decoded real
     * sFlow wire bytes end-to-end through both Stage 1 (BSON serialisation
     * via {@code SFlowUdpParser}) and Stage 2 (flow extraction via
     * {@code SFlowAdapter}).
     */
    @Test
    void sflowPacketProducesFlows() throws Exception {
        byte[] wireBytes = readFixture("fixtures/sflow3.dat");
        TelemetryProtos.TelemetryMessageLog log = rawLog(wireBytes);

        List<Flow> flows = processor.process(log);

        assertThat(flows)
                .as("sFlow v5 fixture (sflow3.dat) with flow samples should produce at least one flow")
                .isNotEmpty();
    }

    /**
     * Regression guard for the horizon {@code FlowRecord.visit()} null guard
     * shipped in horizon 1.0.8 (commit {@code c24a319708c}). The fixture
     * {@code fixtures/hsflowd-sample.dat} is a single UDP payload captured
     * from hsflowd 2.1.23 running with {@code sampling = 1} and
     * {@code pcap { dev = eth0 }}; it contains three samples, at least one
     * of which is an expanded flow sample whose {@code FlowRecord} carries
     * a data format outside horizon's {@code flowDataFormats} map. Those
     * records decode to {@code Opaque} instances with {@code value == null}.
     *
     * <p>Before horizon 1.0.8, {@code FlowRecord.visit()} dereferenced
     * {@code data.value} without a null guard (the sibling {@code writeBson}
     * was correctly guarded). When
     * {@link org.opennms.netmgt.telemetry.protocols.sflow.parser.SampleDatagramEnricher#enrich}
     * walked the datagram via {@code SampleDatagram.visit()}, the unguarded
     * dereference threw a {@link NullPointerException} that escaped the
     * parser's {@link java.util.concurrent.ExecutorService} task, left its
     * {@link java.util.concurrent.CompletableFuture} uncompleted, and caused
     * the calling thread to block on {@code join()} until the Kafka consumer
     * rebalanced — silent lag with no WARN/ERROR in the flow-enricher logs.
     *
     * <p>This test is wired exactly as production: DNS lookups enabled (the
     * default), {@code NoOpDnsResolver} making reverse lookups no-ops,
     * {@code enrich()} walking every datagram via {@code visit()}. If the
     * horizon null guard is ever reverted or the horizon dependency is
     * downgraded below 1.0.8, the stall returns and {@code @Timeout(10s)}
     * fails the test fast instead of letting it hang forever.
     *
     * <p>The fixture's first sample is a counter sample expansion, so it is
     * dropped by {@code SFlowAdapter} (counter samples are not flow records),
     * but the remaining samples include flow records with IP header data
     * that convert to {@link Flow} objects. A non-empty result proves the
     * full hsflowd → Minion → Kafka → flow-enricher → ClickHouse path works
     * against the actual wire format produced by the test exporter.
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void hsflowdWireBytesProduceFlows() throws Exception {
        byte[] wireBytes = readFixture("fixtures/hsflowd-sample.dat");
        TelemetryProtos.TelemetryMessageLog log = rawLog(wireBytes);

        List<Flow> flows = processor.process(log);

        assertThat(flows)
                .as("hsflowd-produced sFlow v5 datagram should produce at least one flow "
                        + "with the horizon 1.0.8 FlowRecord.visit() null guard")
                .isNotEmpty();
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
        try (InputStream in = SFlowParserBridgeIT.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Fixture not found on classpath: " + resourcePath);
            }
            return in.readAllBytes();
        }
    }
}
