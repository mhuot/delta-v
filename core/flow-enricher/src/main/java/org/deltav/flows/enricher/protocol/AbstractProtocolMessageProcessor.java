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

import java.util.Collections;
import java.util.List;

import org.deltav.flows.enricher.pipeline.CapturingPipeline;
import org.opennms.netmgt.flows.api.Flow;
import org.opennms.netmgt.telemetry.common.ipc.TelemetryProtos;
import org.opennms.netmgt.telemetry.protocols.flows.AbstractFlowAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for per-protocol processors that delegate flow parsing to a
 * horizon {@link AbstractFlowAdapter} subclass. Subclasses override
 * {@link #createAdapter(CapturingPipeline)} to construct the protocol-specific
 * adapter (Netflow5, Netflow9, IPFIX, sFlow).
 *
 * <h2>Pipeline-per-invocation pattern</h2>
 *
 * <p>Horizon's {@code AbstractFlowAdapter} takes a
 * {@code org.opennms.netmgt.flows.processing.Pipeline} reference in its
 * constructor and stores it in a final field. The adapter's
 * {@code handleMessageLog()} method parses each entry in the incoming
 * {@code TelemetryMessageLog}, accumulates the resulting flows into a list,
 * and then calls {@code pipeline.process(flows, source, options)} exactly
 * once per message log to publish the flows downstream.
 *
 * <p>Because the pipeline reference is captured in the adapter's constructor
 * and never reassigned, it is not possible to reuse a single adapter instance
 * with different {@link CapturingPipeline} instances across calls. To keep
 * each call independent and avoid cross-invocation contamination, this class
 * constructs a <strong>fresh adapter</strong> and a <strong>fresh
 * {@code CapturingPipeline}</strong> on every call to {@link #process}. The
 * pipeline captures the parsed flows via its
 * {@link CapturingPipeline#getCapturedFlows()} accessor, which this class
 * returns to the caller.
 *
 * <h2>Exception handling</h2>
 *
 * <p>If {@code adapter.handleMessageLog()} throws any unchecked exception
 * (e.g. a malformed {@code FlowMessage} protobuf in one of the entries, or
 * a BSON document the sFlow adapter cannot decode), the exception is logged
 * at {@code WARN} level and this method returns an empty list. The failure
 * is not propagated to the caller because the Spring Cloud Stream binding
 * must not abort the function on a single bad message &mdash; doing so would
 * block the consumer on the offending record indefinitely. Horizon's own
 * adapter code already catches the checked {@code FlowException} family
 * internally, so in practice the only exceptions that can escape
 * {@code handleMessageLog} are unchecked parser errors.
 *
 * <h2>Thread-safety</h2>
 *
 * <p>Because each {@link #process} call allocates its own adapter and
 * pipeline, this class is <strong>effectively stateless and thread-safe
 * for concurrent callers</strong>. Note, however, that the horizon adapters
 * themselves are not designed to be shared across threads &mdash; our
 * per-call construction is what keeps them safe here. A single
 * {@link AbstractProtocolMessageProcessor} instance can be wired into the
 * Spring Cloud Stream function dispatch map and shared across all messages.
 */
public abstract class AbstractProtocolMessageProcessor implements ProtocolMessageProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractProtocolMessageProcessor.class);

    @Override
    public List<Flow> process(TelemetryProtos.TelemetryMessageLog messageLog) {
        if (messageLog == null || messageLog.getMessageCount() == 0) {
            return Collections.emptyList();
        }

        final CapturingPipeline pipeline = new CapturingPipeline();
        final AbstractFlowAdapter<?> adapter = createAdapter(pipeline);
        try {
            // TelemetryProtos.TelemetryMessageLog implements the
            // org.opennms.netmgt.telemetry.api.adapter.TelemetryMessageLog
            // interface that handleMessageLog expects, so no wrapping is
            // needed.
            adapter.handleMessageLog(messageLog);
        } catch (RuntimeException e) {
            LOG.warn("Horizon adapter {} threw while processing telemetry message log with {} entries: {}",
                    adapter.getClass().getSimpleName(),
                    messageLog.getMessageCount(),
                    e.getMessage(),
                    e);
            return Collections.emptyList();
        } finally {
            try {
                adapter.destroy();
            } catch (RuntimeException destroyEx) {
                LOG.debug("Ignoring exception from adapter.destroy()", destroyEx);
            }
        }
        return pipeline.getCapturedFlows();
    }

    /**
     * Constructs the protocol-specific horizon adapter with the provided
     * capturing pipeline. Called once per {@link #process} invocation.
     *
     * @param pipeline the capturing pipeline the adapter must publish to
     * @return a freshly-constructed adapter bound to {@code pipeline}
     */
    protected abstract AbstractFlowAdapter<?> createAdapter(CapturingPipeline pipeline);
}
