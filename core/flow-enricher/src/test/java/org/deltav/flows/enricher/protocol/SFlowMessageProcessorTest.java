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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.bson.BsonBinaryWriter;
import org.bson.BsonDocument;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.EncoderContext;
import org.bson.io.BasicOutputBuffer;
import org.deltav.flows.enricher.parser.ThreadLocalDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opennms.netmgt.flows.api.Flow;
import org.opennms.netmgt.telemetry.common.ipc.TelemetryProtos;
import org.opennms.netmgt.telemetry.config.api.AdapterDefinition;

import com.codahale.metrics.MetricRegistry;
import com.google.protobuf.ByteString;

/**
 * Tests {@link SFlowMessageProcessor}. Under Phase 2 the processor runs a
 * two-stage bridge: Stage 1 feeds raw bytes to a horizon {@link
 * org.opennms.netmgt.telemetry.listeners.UdpParser}, which emits bytes that
 * become the Stage 2 input; Stage 2 passes those bytes to horizon's
 * {@code SFlowAdapter}. Tests use {@link FakeUdpParser} to script the
 * parser's output.
 *
 * <p>sFlow uses a BSON document (not a protobuf) as the parser layer's
 * normalized representation. The test fixture
 * {@code test-packets/sflow-sample.json} is a JSON text representation of a
 * BSON document lifted verbatim from horizon's own sFlow adapter test suite;
 * at test time we parse it into a {@link BsonDocument} and re-serialize it
 * to canonical BSON bytes. The {@link FakeUdpParser} emits those BSON bytes,
 * which the {@code SFlowAdapter} in Stage 2 then decodes.
 *
 * <p><strong>Best-effort per Phase 2 spec.</strong> Live E2E verification of
 * the full sFlow parser bridge is deferred to a followup.
 */
class SFlowMessageProcessorTest {

    private ThreadLocalDispatcher tld;
    private FakeUdpParser fakeParser;
    private SFlowMessageProcessor processor;

    @BeforeEach
    void setUp() {
        tld = new ThreadLocalDispatcher();
        fakeParser = new FakeUdpParser(tld);
        AdapterDefinition adapterDefinition = TestAdapterDefinitions.testAdapterDefinition("sflow-test");
        MetricRegistry metricRegistry = new MetricRegistry();
        processor = new SFlowMessageProcessor(fakeParser, adapterDefinition, metricRegistry, tld);
    }

    @Test
    void processReturnsEmptyListForNullMessageLog() {
        assertThat(processor.process(null)).isEmpty();
    }

    @Test
    void processYieldsFlowsWhenParserEmitsSflowBsonBytes() throws Exception {
        byte[] bsonBytes = loadFixtureAsBsonBytes("/test-packets/sflow-sample.json");
        fakeParser.emitNext(bsonBytes);

        TelemetryProtos.TelemetryMessageLog raw = TelemetryProtos.TelemetryMessageLog.newBuilder()
                .setLocation("test-location")
                .setSystemId("test-system")
                .setSourceAddress("127.0.0.1")
                .setSourcePort(6343)
                .addMessage(TelemetryProtos.TelemetryMessage.newBuilder()
                        .setBytes(ByteString.copyFrom(new byte[]{1, 2, 3, 4}))
                        .setTimestamp(1521618510235L))
                .build();

        List<Flow> flows = processor.process(raw);

        // Matches horizon's own SFlowConverterTest expectation: the fixture
        // contains seven samples, five of which carry IPv4/IPv6 information
        // and are therefore emitted as flows.
        assertThat(flows).hasSize(5);
        assertThat(flows).allSatisfy(flow -> assertThat(flow).isNotNull());
    }

    private static byte[] loadFixtureAsBsonBytes(String resourcePath) throws Exception {
        try (InputStream in = SFlowMessageProcessorTest.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Test fixture not found on classpath: " + resourcePath);
            }
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buf.write(chunk, 0, read);
            }
            String json = buf.toString(StandardCharsets.UTF_8);
            BsonDocument doc = BsonDocument.parse(json);
            return encodeBsonDocument(doc);
        }
    }

    private static byte[] encodeBsonDocument(BsonDocument document) {
        BasicOutputBuffer outputBuffer = new BasicOutputBuffer();
        try (BsonBinaryWriter writer = new BsonBinaryWriter(outputBuffer)) {
            new BsonDocumentCodec().encode(writer, document, EncoderContext.builder().build());
        }
        return outputBuffer.toByteArray();
    }
}
