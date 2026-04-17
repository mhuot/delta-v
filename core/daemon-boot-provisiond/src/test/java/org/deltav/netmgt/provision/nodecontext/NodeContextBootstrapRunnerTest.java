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
package org.deltav.netmgt.provision.nodecontext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.dao.api.SessionUtils;
import org.opennms.netmgt.model.OnmsNode;

class NodeContextBootstrapRunnerTest {

    private final NodeContextPublisher publisher = mock(NodeContextPublisher.class);
    private final NodeDao nodeDao = mock(NodeDao.class);
    private final SessionUtils sessionUtils = mock(SessionUtils.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    @Test
    void start_publishesAllNodesWithBootstrapReason() {
        when(sessionUtils.<Object>withReadOnlyTransaction(any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        OnmsNode a = node(1); OnmsNode b = node(2); OnmsNode c = node(3);
        when(nodeDao.findAll()).thenReturn(List.of(a, b, c));

        NodeContextBootstrapRunner runner = new NodeContextBootstrapRunner(
                publisher, nodeDao, sessionUtils, meters);
        runner.start();

        verify(publisher).publishNode(1, "bootstrap");
        verify(publisher).publishNode(2, "bootstrap");
        verify(publisher).publishNode(3, "bootstrap");
        assertThat(runner.isRunning()).isTrue();
    }

    @Test
    void start_nodeWithNullId_skipped() {
        when(sessionUtils.<Object>withReadOnlyTransaction(any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        OnmsNode withId = node(42);
        OnmsNode withoutId = new OnmsNode();
        when(nodeDao.findAll()).thenReturn(List.of(withId, withoutId));

        new NodeContextBootstrapRunner(publisher, nodeDao, sessionUtils, meters).start();

        verify(publisher).publishNode(42, "bootstrap");
        org.mockito.Mockito.verify(publisher, org.mockito.Mockito.never())
                .publishNode(0, "bootstrap");
    }

    @Test
    void start_enumerationThrows_failureCounterIncremented() {
        when(sessionUtils.<Object>withReadOnlyTransaction(any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        when(nodeDao.findAll()).thenThrow(new RuntimeException("db down"));

        NodeContextBootstrapRunner runner = new NodeContextBootstrapRunner(
                publisher, nodeDao, sessionUtils, meters);
        runner.start();

        assertThat(meters.counter("deltav_node_context_records_failed_total",
                "location", "", "reason", "bootstrap_error").count()).isEqualTo(1.0);
    }

    @Test
    void stop_isNoOp() {
        NodeContextBootstrapRunner runner = new NodeContextBootstrapRunner(
                publisher, nodeDao, sessionUtils, meters);
        runner.stop();  // should not throw
        assertThat(runner.isRunning()).isFalse();
    }

    private static OnmsNode node(int id) {
        OnmsNode n = new OnmsNode();
        n.setId(id);
        return n;
    }
}
