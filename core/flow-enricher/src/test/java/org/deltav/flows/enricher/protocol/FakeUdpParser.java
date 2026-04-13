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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;

import org.deltav.flows.enricher.parser.ThreadLocalDispatcher;
import org.opennms.netmgt.telemetry.api.receiver.TelemetryMessage;
import org.opennms.netmgt.telemetry.listeners.UdpParser;

import io.netty.buffer.ByteBuf;

/**
 * Test stub for {@link UdpParser}. Allows unit tests to script what bytes
 * the parser should emit for each invocation of {@code parse()}, and
 * optionally force it to throw on the next invocation. Dispatches via the
 * installed {@link ThreadLocalDispatcher} so the stub exercises the
 * real capture path.
 */
final class FakeUdpParser implements UdpParser {

    private final ThreadLocalDispatcher dispatcher;
    private final List<byte[]> scriptedEmissions = new ArrayList<>();
    private RuntimeException nextException;
    private int parseCallCount;

    FakeUdpParser(ThreadLocalDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    /** Queue bytes to dispatch as one captured TelemetryMessage on the next parse() call. */
    FakeUdpParser emitNext(byte[] bytes) {
        scriptedEmissions.add(bytes);
        return this;
    }

    /** Make the next parse() call throw the given exception after incrementing the call count. */
    FakeUdpParser throwOnNextParse(RuntimeException e) {
        this.nextException = e;
        return this;
    }

    int getParseCallCount() {
        return parseCallCount;
    }

    @Override
    public CompletableFuture<?> parse(ByteBuf buffer, InetSocketAddress remoteAddress, InetSocketAddress localAddress) {
        parseCallCount++;
        if (nextException != null) {
            RuntimeException e = nextException;
            nextException = null;
            throw e;
        }
        if (!scriptedEmissions.isEmpty()) {
            byte[] bytes = scriptedEmissions.remove(0);
            TelemetryMessage msg = new TelemetryMessage(remoteAddress, ByteBuffer.wrap(bytes));
            dispatcher.send(msg);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public String getName() {
        return "fake-udp-parser";
    }

    @Override
    public String getDescription() {
        return "Test stub for unit tests";
    }

    @Override
    public Object dumpInternalState() {
        return "fake";
    }

    @Override
    public void start(ScheduledExecutorService executorService) {
        // no-op
    }

    @Override
    public void stop() {
        // no-op
    }
}
