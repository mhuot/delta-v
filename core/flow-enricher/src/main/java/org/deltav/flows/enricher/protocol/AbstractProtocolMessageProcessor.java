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

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.deltav.flows.enricher.parser.CapturingDispatcher;
import org.deltav.flows.enricher.parser.ThreadLocalDispatcher;
import org.deltav.flows.enricher.pipeline.CapturingPipeline;
import org.opennms.netmgt.flows.api.Flow;
import org.opennms.netmgt.telemetry.api.receiver.TelemetryMessage;
import org.opennms.netmgt.telemetry.common.ipc.TelemetryProtos;
import org.opennms.netmgt.telemetry.listeners.UdpParser;
import org.opennms.netmgt.telemetry.protocols.flows.AbstractFlowAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Base class for per-protocol flow processors. Implements the Phase 2
 * two-stage parser bridge:
 *
 * <ol>
 *   <li><strong>Stage 1 — parse raw UDP bytes into {@code FlowMessage} protobuf.</strong>
 *       Uses a horizon {@link UdpParser} supplied by the subclass via
 *       {@link #getParser()}. The parser is a long-lived bean (holds per-exporter
 *       template state in {@code UdpSessionManager}). Its output is captured
 *       into a per-call {@link CapturingDispatcher} installed onto the shared
 *       {@link ThreadLocalDispatcher} injected at construction.</li>
 *   <li><strong>Stage 2 — turn {@code FlowMessage} protobuf into {@link Flow} POJOs.</strong>
 *       Uses horizon's {@link AbstractFlowAdapter} + {@link CapturingPipeline},
 *       exactly as Phase 1.5 did. The Stage 1 output is wrapped in a synthetic
 *       {@link TelemetryProtos.TelemetryMessageLog} whose entries carry the
 *       {@code FlowMessage} bytes Stage 2 expects.</li>
 * </ol>
 *
 * <h2>Error boundaries</h2>
 *
 * <p>Stage 1 and Stage 2 have independent try/catch blocks so a parser bug
 * and an adapter bug get logged with distinct, attributable messages. Per-entry
 * parser failures inside the Stage 1 loop are logged at {@code DEBUG} and do
 * not abort the batch — one corrupt UDP packet should not discard its
 * neighbors. Whole-batch failures (anything that escapes the per-entry
 * try/catch) are logged at {@code WARN} and return an empty flow list.
 *
 * <h2>ThreadLocal hygiene (critical)</h2>
 *
 * <p>Every call to {@link ThreadLocalDispatcher#install install} in
 * {@link #runParser} is paired with a {@link ThreadLocalDispatcher#clear clear}
 * in a {@code finally} block that runs regardless of outcome. A leaked
 * thread-local silently cross-contaminates the next call on the same Spring
 * Cloud Stream consumer thread, which would attribute flows from one exporter
 * to another's result — a correctness bug that does not surface without
 * explicit coverage. The corresponding stress tests live in
 * {@code Netflow9MessageProcessorTest}.
 *
 * <h2>Thread-safety</h2>
 *
 * <p>Each call creates its own {@link CapturingDispatcher} and its own
 * {@link CapturingPipeline} + adapter instance. The only shared state is the
 * singleton {@link UdpParser} (thread-safe via {@code ConcurrentHashMap}
 * inside {@code UdpSessionManager}) and the singleton
 * {@link ThreadLocalDispatcher} (safe by design — per-thread delegation).
 */
public abstract class AbstractProtocolMessageProcessor implements ProtocolMessageProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractProtocolMessageProcessor.class);

    /**
     * Default local address passed to {@code UdpParser.parse()}. Horizon uses
     * the local address as part of its session key; in our server-side bridge
     * we always pass a constant because we are not a real UDP listener — the
     * Minion already bound the real socket.
     */
    private static final InetSocketAddress LOCALHOST_SINK = new InetSocketAddress("127.0.0.1", 0);

    private final ThreadLocalDispatcher threadLocalDispatcher;

    protected AbstractProtocolMessageProcessor(ThreadLocalDispatcher threadLocalDispatcher) {
        this.threadLocalDispatcher = Objects.requireNonNull(threadLocalDispatcher, "threadLocalDispatcher");
    }

    @Override
    public List<Flow> process(TelemetryProtos.TelemetryMessageLog rawLog) {
        if (rawLog == null || rawLog.getMessageCount() == 0) {
            return Collections.emptyList();
        }

        // Stage 1: parse raw UDP bytes -> FlowMessage protobuf bytes
        TelemetryProtos.TelemetryMessageLog parsedLog;
        try {
            parsedLog = runParser(rawLog);
        } catch (RuntimeException e) {
            LOG.warn("Parser {} failed on message log ({} entries, sourceAddr={}): {}",
                    getParser().getClass().getSimpleName(),
                    rawLog.getMessageCount(),
                    rawLog.getSourceAddress(),
                    e.getMessage(), e);
            return Collections.emptyList();
        }
        if (parsedLog.getMessageCount() == 0) {
            // Parser ate everything (e.g. all entries were templates, or all data
            // records referenced unknown templates). Not an error, just nothing to do.
            return Collections.emptyList();
        }

        // Stage 2: FlowMessage protobuf -> Flow POJO (unchanged from Phase 1.5)
        final CapturingPipeline pipeline = new CapturingPipeline();
        final AbstractFlowAdapter<?> adapter = createAdapter(pipeline);
        try {
            adapter.handleMessageLog(parsedLog);
        } catch (RuntimeException e) {
            LOG.warn("Horizon adapter {} failed on parsed log ({} entries): {}",
                    adapter.getClass().getSimpleName(),
                    parsedLog.getMessageCount(),
                    e.getMessage(), e);
            return Collections.emptyList();
        }
        return pipeline.getCapturedFlows();
    }

    /**
     * Run Stage 1. Installs a fresh {@link CapturingDispatcher} onto the
     * {@link ThreadLocalDispatcher}, iterates each entry in the input log
     * through {@link UdpParser#parse}, captures the parser's dispatched
     * messages, and wraps them in a synthetic {@link TelemetryProtos.TelemetryMessageLog}
     * whose entries carry {@code FlowMessage} protobuf bytes.
     *
     * <p>Per-entry parser exceptions are logged at {@code DEBUG} and
     * skipped — one bad UDP packet does not poison the rest of the batch.
     * Exceptions that escape this method (e.g. a catastrophic bug in the
     * capturing dispatcher itself) are caught by the outer try/catch in
     * {@link #process}.
     */
    private TelemetryProtos.TelemetryMessageLog runParser(TelemetryProtos.TelemetryMessageLog rawLog) {
        CapturingDispatcher captureForThisCall = new CapturingDispatcher();
        threadLocalDispatcher.install(captureForThisCall);
        try {
            InetSocketAddress remoteAddress = new InetSocketAddress(
                    rawLog.getSourceAddress(), rawLog.getSourcePort());
            UdpParser parser = getParser();
            for (TelemetryProtos.TelemetryMessage entry : rawLog.getMessageList()) {
                if (entry.getBytes().isEmpty()) {
                    continue;
                }
                ByteBuf buf = Unpooled.wrappedBuffer(entry.getBytes().toByteArray());
                try {
                    parser.parse(buf, remoteAddress, LOCALHOST_SINK).join();
                } catch (Exception e) {
                    LOG.debug("Parser dropped one entry ({} bytes, sourceAddr={}): {}",
                            entry.getBytes().size(), rawLog.getSourceAddress(), e.getMessage());
                }
            }
            return synthesizeParsedLog(rawLog, captureForThisCall.getCaptured());
        } finally {
            threadLocalDispatcher.clear();
        }
    }

    /**
     * Build a new {@link TelemetryProtos.TelemetryMessageLog} that copies the
     * location / systemId / source address / source port fields from the
     * input raw log, and replaces the message entries with one entry per
     * captured {@link TelemetryMessage} whose {@code bytes} field is the
     * captured message's buffer (which is already a serialized
     * {@code FlowMessage} protobuf produced by horizon's parser).
     */
    private static TelemetryProtos.TelemetryMessageLog synthesizeParsedLog(
            TelemetryProtos.TelemetryMessageLog rawLog,
            List<TelemetryMessage> captured) {
        TelemetryProtos.TelemetryMessageLog.Builder builder = TelemetryProtos.TelemetryMessageLog.newBuilder()
                .setLocation(rawLog.getLocation())
                .setSystemId(rawLog.getSystemId());
        if (rawLog.hasSourceAddress()) {
            builder.setSourceAddress(rawLog.getSourceAddress());
        }
        if (rawLog.hasSourcePort()) {
            builder.setSourcePort(rawLog.getSourcePort());
        }
        long now = System.currentTimeMillis();
        for (TelemetryMessage msg : captured) {
            ByteBuffer buffer = msg.getBuffer();
            // Duplicate so we don't mutate the parser's buffer cursor state.
            ByteBuffer copy = buffer.duplicate();
            byte[] bytes = new byte[copy.remaining()];
            copy.get(bytes);
            builder.addMessage(TelemetryProtos.TelemetryMessage.newBuilder()
                    .setTimestamp(now)
                    .setBytes(ByteString.copyFrom(bytes)));
        }
        return builder.build();
    }

    /**
     * Subclass hook — returns the singleton horizon {@link UdpParser} bound
     * to this protocol. The parser is typically injected via the subclass
     * constructor and stored as a final field.
     */
    protected abstract UdpParser getParser();

    /**
     * Subclass hook — constructs a fresh {@link AbstractFlowAdapter}
     * subclass for this call, bound to the supplied capturing pipeline. The
     * adapter must expect each entry in a {@code TelemetryMessageLog} to
     * carry a serialized {@code FlowMessage} protobuf (which is exactly
     * what {@link #synthesizeParsedLog} produces).
     */
    protected abstract AbstractFlowAdapter<?> createAdapter(CapturingPipeline pipeline);
}
