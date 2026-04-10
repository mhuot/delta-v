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
package org.deltav.flows.enricher;

import org.deltav.flows.enricher.enrichment.FlowLocalityCalculator;
import org.deltav.flows.enricher.enrichment.InterfaceMarkingCache;
import org.deltav.flows.enricher.enrichment.JdbcNodeInfoLookup;
import org.deltav.flows.proto.FlowDocumentProtos;
import org.opennms.netmgt.telemetry.common.ipc.TelemetryProtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Phase 1 (skeleton) flow enrichment pipeline. The processing path is:
 *
 * <ol>
 *   <li>Deserialize the Kafka payload into a {@link TelemetryProtos.TelemetryMessageLog}
 *       via {@link SinkMessageDeserializer}.</li>
 *   <li>Look up the exporter NodeInfo by the message source address (the
 *       Minion-reported IP that received the original UDP packet).</li>
 *   <li>Build a {@link FlowDocumentProtos.FlowDocument} envelope with timestamp,
 *       host, location, and exporter_node populated.</li>
 *   <li>Serialize and emit to the {@code deltav-flows} output binding.</li>
 * </ol>
 *
 * <p><strong>Phase 1 limitations (intentional):</strong>
 * <ul>
 *   <li>Per-flow protocol parsing (Netflow5/9, IPFIX, sFlow records inside
 *       the {@code TelemetryMessage} payload bytes) is NOT performed in this
 *       skeleton. The horizon protocol adapters will be wired in a follow-up
 *       PR once this Spring Cloud Stream pipeline is proven end-to-end.</li>
 *   <li>Application classification, src/dst node lookup, and locality
 *       calculation depend on per-flow parsed records and are similarly
 *       deferred. The {@link InterfaceMarkingCache},
 *       {@link FlowLocalityCalculator}, and {@link JdbcNodeInfoLookup#lookupByIpAddress}
 *       beans are wired through the constructor so the follow-up PR can use
 *       them without further bean changes.</li>
 *   <li>The unused fields are deliberately left in the constructor signature
 *       so this skeleton compiles to the same bean wiring the full pipeline
 *       will use; removing them now would just churn the configuration class
 *       in the next PR.</li>
 * </ul>
 *
 * <p>The output of this skeleton is a near-empty FlowDocument: callers
 * receive a real protobuf message they can deserialize, with the topic
 * contract proven, but the meaningful fields populated only by the follow-up
 * adapter integration.
 */
public class FlowEnrichmentFunction {

    private static final Logger LOG = LoggerFactory.getLogger(FlowEnrichmentFunction.class);

    private final SinkMessageDeserializer deserializer;
    private final JdbcNodeInfoLookup nodeInfoLookup;
    @SuppressWarnings("unused") // wired now; consumed in the follow-up adapter PR
    private final FlowLocalityCalculator localityCalculator;
    @SuppressWarnings("unused") // wired now; consumed in the follow-up adapter PR
    private final InterfaceMarkingCache interfaceMarkingCache;

    public FlowEnrichmentFunction(
            SinkMessageDeserializer deserializer,
            JdbcNodeInfoLookup nodeInfoLookup,
            FlowLocalityCalculator localityCalculator,
            InterfaceMarkingCache interfaceMarkingCache) {
        this.deserializer = deserializer;
        this.nodeInfoLookup = nodeInfoLookup;
        this.localityCalculator = localityCalculator;
        this.interfaceMarkingCache = interfaceMarkingCache;
    }

    /**
     * Spring Cloud Stream function entry point. Returns the protobuf bytes of
     * an enriched FlowDocument, or {@code null} to drop the input message
     * without producing an output record.
     */
    public byte[] processMessage(byte[] kafkaBytes) {
        TelemetryProtos.TelemetryMessageLog messageLog = deserializer.deserialize(kafkaBytes);
        if (messageLog == null || messageLog.getMessageCount() == 0) {
            return null;
        }

        try {
            FlowDocumentProtos.FlowDocument.Builder builder =
                    FlowDocumentProtos.FlowDocument.newBuilder()
                            .setTimestamp(System.currentTimeMillis())
                            .setHost(messageLog.getSourceAddress())
                            .setLocation(messageLog.getLocation());

            JdbcNodeInfoLookup.NodeInfo exporter =
                    nodeInfoLookup.lookupByIpAddress(messageLog.getSourceAddress());
            if (exporter != null) {
                FlowDocumentProtos.NodeInfo.Builder exporterBuilder =
                        FlowDocumentProtos.NodeInfo.newBuilder().setNodeId(exporter.nodeId());
                if (exporter.foreignSource() != null) {
                    exporterBuilder.setForeignSource(exporter.foreignSource());
                }
                if (exporter.foreignId() != null) {
                    exporterBuilder.setForeignId(exporter.foreignId());
                }
                builder.setExporterNode(exporterBuilder.build());
            }

            return builder.build().toByteArray();
        } catch (Exception e) {
            LOG.warn("Failed to enrich flow message from {}: {}",
                    messageLog.getSourceAddress(), e.getMessage());
            return null;
        }
    }
}
