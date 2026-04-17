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
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NodeContextDebouncerTest {

    private NodeContextPublisher publisher;
    private SimpleMeterRegistry meters;
    private NodeContextDebouncer debouncer;

    @BeforeEach
    void setUp() {
        publisher = mock(NodeContextPublisher.class);
        meters = new SimpleMeterRegistry();
        // Fast debounce (50 ms) so tests run quickly
        debouncer = new NodeContextDebouncer(publisher, 50, 2, meters);
    }

    @AfterEach
    void tearDown() {
        debouncer.flushAndClose();
    }

    @Test
    void enqueueUpdate_firesAfterWindow() {
        debouncer.enqueueUpdate(7);

        verify(publisher, timeout(500)).publishNode(7);
    }

    @Test
    void burstEnqueues_coalescedToOnePublish() {
        for (int i = 0; i < 10; i++) {
            debouncer.enqueueUpdate(42);
        }

        verify(publisher, timeout(500)).publishNode(42);
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        verify(publisher, org.mockito.Mockito.times(1)).publishNode(42);

        assertThat(meters.counter("deltav_node_context_debounce_coalesced_total").count())
                .isGreaterThanOrEqualTo(9.0);
    }

    @Test
    void differentNodeIds_independentPublishes() {
        debouncer.enqueueUpdate(1);
        debouncer.enqueueUpdate(2);
        debouncer.enqueueUpdate(3);

        verify(publisher, timeout(500)).publishNode(1);
        verify(publisher, timeout(500)).publishNode(2);
        verify(publisher, timeout(500)).publishNode(3);
    }

    @Test
    void evict_cancelsPendingFuture() throws InterruptedException {
        debouncer.enqueueUpdate(5);
        debouncer.evict(5);

        Thread.sleep(200);  // well past debounce window

        verifyNoInteractions(publisher);
    }

    @Test
    void flushAndClose_firesAllPendingSynchronously() {
        debouncer.enqueueUpdate(10);
        debouncer.enqueueUpdate(11);

        debouncer.flushAndClose();

        verify(publisher).publishNode(10);
        verify(publisher).publishNode(11);
    }

    @Test
    void pendingGauge_reflectsPendingCount() {
        debouncer.enqueueUpdate(1);
        debouncer.enqueueUpdate(2);
        double gauge = meters.find("deltav_node_context_debounce_pending_gauge").gauge().value();
        assertThat(gauge).isBetween(1.0, 2.0);

        await().atMost(Duration.ofSeconds(1)).until(() ->
                meters.find("deltav_node_context_debounce_pending_gauge").gauge().value() == 0.0);
    }

    @Test
    void enqueueAfterClose_noPublish() {
        debouncer.flushAndClose();

        debouncer.enqueueUpdate(99);

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        verifyNoInteractions(publisher);
    }
}
