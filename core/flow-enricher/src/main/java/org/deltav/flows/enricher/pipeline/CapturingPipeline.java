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
package org.deltav.flows.enricher.pipeline;

import java.util.Collections;
import java.util.List;

import org.opennms.netmgt.flows.api.Flow;
import org.opennms.netmgt.flows.api.FlowSource;
import org.opennms.netmgt.flows.processing.Pipeline;
import org.opennms.netmgt.flows.processing.ProcessingOptions;

/**
 * A {@link Pipeline} implementation that stashes the flows and source passed
 * to its {@link #process} method instead of forwarding them to any downstream
 * persister or sink.
 *
 * <p>This is the integration seam between horizon's protocol adapters
 * ({@code Netflow5Adapter}, {@code Netflow9Adapter}, {@code IpfixAdapter},
 * {@code SflowAdapter}) and delta-v's flow enrichment. Horizon adapters
 * extend {@code AbstractFlowAdapter}, whose {@code handleMessageLog()} parses
 * a {@code TelemetryMessageLog} into a list of {@link Flow} instances and
 * then calls {@code pipeline.process(flows, source, options)} to persist
 * them. By injecting a {@code CapturingPipeline} into the adapter instead of
 * horizon's {@code PipelineImpl}, we intercept the parsed flows without
 * triggering any of horizon's persistence, Elasticsearch indexing, or
 * Kafka forwarding logic.
 *
 * <p><strong>This class is NOT thread-safe.</strong> Each adapter invocation
 * must use a fresh instance — a single instance cannot be safely shared
 * across concurrent message-log handling. The enricher's flow function
 * constructs a new {@code CapturingPipeline} per message.
 */
public class CapturingPipeline implements Pipeline {

    private List<Flow> capturedFlows = Collections.emptyList();
    private FlowSource capturedSource;

    @Override
    public void process(List<Flow> flows, FlowSource source, ProcessingOptions options) {
        this.capturedFlows = flows;
        this.capturedSource = source;
    }

    public List<Flow> getCapturedFlows() {
        return capturedFlows;
    }

    public FlowSource getCapturedSource() {
        return capturedSource;
    }
}
