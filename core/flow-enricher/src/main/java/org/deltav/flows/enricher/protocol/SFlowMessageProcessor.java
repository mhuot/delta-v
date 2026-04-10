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
import org.opennms.netmgt.telemetry.protocols.sflow.adapter.SFlowAdapter;

import com.codahale.metrics.MetricRegistry;

/**
 * Protocol processor for sFlow messages. Wraps horizon's
 * {@link SFlowAdapter}, which expects each
 * {@code TelemetryMessageLogEntry} to carry a serialized BSON document
 * (the sFlow parser layer's normalized representation). Each BSON document
 * contains a top-level {@code data.samples} array that may produce zero or
 * more {@link org.opennms.netmgt.flows.api.Flow} records.
 */
public class SFlowMessageProcessor extends AbstractProtocolMessageProcessor {

    private final AdapterDefinition adapterDefinition;
    private final MetricRegistry metricRegistry;

    public SFlowMessageProcessor(AdapterDefinition adapterDefinition, MetricRegistry metricRegistry) {
        this.adapterDefinition = Objects.requireNonNull(adapterDefinition, "adapterDefinition");
        this.metricRegistry = Objects.requireNonNull(metricRegistry, "metricRegistry");
    }

    @Override
    protected AbstractFlowAdapter<?> createAdapter(CapturingPipeline pipeline) {
        return new SFlowAdapter(adapterDefinition, metricRegistry, pipeline);
    }
}
