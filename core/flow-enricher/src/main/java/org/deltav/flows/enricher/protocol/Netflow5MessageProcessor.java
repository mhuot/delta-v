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

import java.util.Objects;

import org.deltav.flows.enricher.parser.ThreadLocalDispatcher;
import org.deltav.flows.enricher.pipeline.CapturingPipeline;
import org.opennms.netmgt.telemetry.config.api.AdapterDefinition;
import org.opennms.netmgt.telemetry.listeners.UdpParser;
import org.opennms.netmgt.telemetry.protocols.flows.AbstractFlowAdapter;
import org.opennms.netmgt.telemetry.protocols.netflow.adapter.netflow5.Netflow5Adapter;

import com.codahale.metrics.MetricRegistry;

/**
 * Protocol processor for Netflow v5. Stage 1 parses raw Netflow v5 UDP wire
 * bytes via a horizon {@link UdpParser}; Stage 2 turns the parser-emitted
 * {@code FlowMessage} protobufs into {@link org.opennms.netmgt.flows.api.Flow}
 * POJOs via horizon's {@link Netflow5Adapter}.
 */
public class Netflow5MessageProcessor extends AbstractProtocolMessageProcessor {

    private final UdpParser parser;
    private final AdapterDefinition adapterDefinition;
    private final MetricRegistry metricRegistry;

    public Netflow5MessageProcessor(
            UdpParser parser,
            AdapterDefinition adapterDefinition,
            MetricRegistry metricRegistry,
            ThreadLocalDispatcher threadLocalDispatcher) {
        super(threadLocalDispatcher);
        this.parser = Objects.requireNonNull(parser, "parser");
        this.adapterDefinition = Objects.requireNonNull(adapterDefinition, "adapterDefinition");
        this.metricRegistry = Objects.requireNonNull(metricRegistry, "metricRegistry");
    }

    @Override
    protected UdpParser getParser() {
        return parser;
    }

    @Override
    protected AbstractFlowAdapter<?> createAdapter(CapturingPipeline pipeline) {
        return new Netflow5Adapter(adapterDefinition, metricRegistry, pipeline);
    }
}
