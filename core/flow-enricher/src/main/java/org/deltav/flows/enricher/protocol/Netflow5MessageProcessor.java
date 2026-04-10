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

import org.deltav.flows.enricher.pipeline.CapturingPipeline;
import org.opennms.netmgt.telemetry.config.api.AdapterDefinition;
import org.opennms.netmgt.telemetry.protocols.flows.AbstractFlowAdapter;
import org.opennms.netmgt.telemetry.protocols.netflow.adapter.netflow5.Netflow5Adapter;

import com.codahale.metrics.MetricRegistry;

/**
 * Protocol processor for Netflow v5 messages. Wraps horizon's
 * {@link Netflow5Adapter}, which expects each
 * {@code TelemetryMessageLogEntry} to carry a serialized
 * {@code org.opennms.netmgt.telemetry.protocols.netflow.transport.FlowMessage}
 * protobuf (the parser layer's normalized representation).
 */
public class Netflow5MessageProcessor extends AbstractProtocolMessageProcessor {

    private final AdapterDefinition adapterDefinition;
    private final MetricRegistry metricRegistry;

    public Netflow5MessageProcessor(AdapterDefinition adapterDefinition, MetricRegistry metricRegistry) {
        this.adapterDefinition = Objects.requireNonNull(adapterDefinition, "adapterDefinition");
        this.metricRegistry = Objects.requireNonNull(metricRegistry, "metricRegistry");
    }

    @Override
    protected AbstractFlowAdapter<?> createAdapter(CapturingPipeline pipeline) {
        return new Netflow5Adapter(adapterDefinition, metricRegistry, pipeline);
    }
}
