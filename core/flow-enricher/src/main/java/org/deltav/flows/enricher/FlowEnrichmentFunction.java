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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.deltav.flows.enricher.enrichment.FlowLocalityCalculator;
import org.deltav.flows.enricher.enrichment.InterfaceMarkingCache;
import org.deltav.flows.enricher.enrichment.JdbcNodeInfoLookup;
import org.deltav.flows.enricher.protocol.ProtocolMessageProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Phase 1.5 (Commit 3) flow enrichment pipeline. Dispatches incoming Sink
 * messages to protocol-specific processors by {@code moduleId}, then emits
 * a list of enriched FlowDocument byte arrays.
 *
 * <p><strong>Commit 3 scope:</strong> this commit establishes the new
 * {@code Function<byte[], List<byte[]>>} splitter signature with a placeholder
 * dispatch. The dispatch map is empty in Commit 3 because the
 * {@link ProtocolMessageProcessor} implementations are created in
 * Commit 4 (Task 8). Commit 5 (Task 12) wires the full per-flow enrichment
 * pipeline. Every code path therefore returns an empty list for now, which
 * Spring Cloud Stream interprets as "drop this input message without
 * producing any output records".
 *
 * <p><strong>Known gap &mdash; moduleId dispatch:</strong> the Sink protobuf
 * envelope does not carry a {@code moduleId} field (see
 * {@link DeserializedSinkMessage}), so the single-argument
 * {@link SinkMessageDeserializer#deserialize(byte[])} path always reports
 * {@code moduleId == null}. A later commit will change the Spring Cloud
 * Stream function signature to accept {@code Message<byte[]>} so the topic
 * name can be read from headers and passed to
 * {@link SinkMessageDeserializer#deserialize(String, byte[])}. Until that
 * happens the dispatch lookup will always miss; this is fine for Commit 3
 * because the dispatch map is empty anyway.
 */
public class FlowEnrichmentFunction {

    private static final Logger LOG = LoggerFactory.getLogger(FlowEnrichmentFunction.class);

    private final SinkMessageDeserializer deserializer;
    @SuppressWarnings("unused") // wired now; consumed in Commit 5 (full enrichment pipeline)
    private final JdbcNodeInfoLookup nodeInfoLookup;
    @SuppressWarnings("unused") // wired now; consumed in Commit 5
    private final FlowLocalityCalculator localityCalculator;
    @SuppressWarnings("unused") // wired now; consumed in Commit 5
    private final InterfaceMarkingCache interfaceMarkingCache;
    private final Map<String, ProtocolMessageProcessor> processorsByModuleId;

    public FlowEnrichmentFunction(
            SinkMessageDeserializer deserializer,
            JdbcNodeInfoLookup nodeInfoLookup,
            FlowLocalityCalculator localityCalculator,
            InterfaceMarkingCache interfaceMarkingCache,
            Map<String, ProtocolMessageProcessor> processorsByModuleId) {
        this.deserializer = deserializer;
        this.nodeInfoLookup = nodeInfoLookup;
        this.localityCalculator = localityCalculator;
        this.interfaceMarkingCache = interfaceMarkingCache;
        this.processorsByModuleId = Map.copyOf(processorsByModuleId);
    }

    /**
     * Spring Cloud Stream function entry point. Returns a list of serialized
     * enriched FlowDocument messages &mdash; one per parsed flow record, zero
     * if the input is unparseable or contains no flows.
     *
     * <p>Returning an empty list drops the input message without producing
     * any output records.
     */
    public List<byte[]> processMessage(byte[] kafkaBytes) {
        if (kafkaBytes == null || kafkaBytes.length == 0) {
            return Collections.emptyList();
        }

        DeserializedSinkMessage deserialized = deserializer.deserialize(kafkaBytes);
        if (deserialized == null
                || deserialized.messageLog() == null
                || deserialized.messageLog().getMessageCount() == 0) {
            return Collections.emptyList();
        }

        String moduleId = deserialized.moduleId();
        if (moduleId == null) {
            // Phase 1.5 pre-Commit-5 state: the Spring Cloud Stream binding
            // does not yet supply the Kafka topic name, so the single-arg
            // deserializer returns a null moduleId and the dispatch lookup
            // has nothing to match against. Drop the message for now; a
            // later commit will wire topic headers through the function
            // signature.
            LOG.debug("SinkMessage has no moduleId (topic header not wired yet); dropping");
            return Collections.emptyList();
        }
        ProtocolMessageProcessor processor = processorsByModuleId.get(moduleId);
        if (processor == null) {
            LOG.debug("No processor for moduleId '{}', dropping message", moduleId);
            return Collections.emptyList();
        }

        // Commit 5 (Task 12) replaces this placeholder with the full per-flow
        // enrichment pipeline: adapter.process() -> enrich each flow -> map to
        // FlowDocument -> serialize -> add to result list.
        return Collections.emptyList();
    }
}
