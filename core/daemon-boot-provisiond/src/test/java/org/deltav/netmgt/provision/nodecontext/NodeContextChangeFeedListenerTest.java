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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.events.api.EventConstants;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.xml.event.Event;
import org.opennms.netmgt.xml.event.Parm;
import org.opennms.netmgt.xml.event.Value;

class NodeContextChangeFeedListenerTest {

    private NodeContextDebouncer debouncer;
    private NodeContextPublisher publisher;
    private NodeDao nodeDao;
    private NodeContextChangeFeedListener listener;

    @BeforeEach
    void setUp() {
        debouncer = mock(NodeContextDebouncer.class);
        publisher = mock(NodeContextPublisher.class);
        nodeDao = mock(NodeDao.class);
        listener = new NodeContextChangeFeedListener(debouncer, publisher, nodeDao,
                new SimpleMeterRegistry());
    }

    @Test
    void onNodeAdded_enqueuesDebouncer() {
        listener.onNodeAdded(eventForNode(EventConstants.NODE_ADDED_EVENT_UEI, 7));
        verify(debouncer).enqueueUpdate(7);
    }

    @Test
    void onNodeUpdated_enqueuesDebouncer() {
        listener.onNodeUpdated(eventForNode(EventConstants.NODE_UPDATED_EVENT_UEI, 8));
        verify(debouncer).enqueueUpdate(8);
    }

    @Test
    void onNodeDeleted_publishesTombstoneAndEvicts() {
        Event e = eventForNode(EventConstants.NODE_DELETED_EVENT_UEI, 9);
        addParm(e, "nodelabel", "n9");
        addParm(e, "location", "Default");

        listener.onNodeDeleted(e);

        verify(publisher).publishTombstone(9, "Default");
        verify(debouncer).evict(9);
    }

    @Test
    void onNodeDeleted_missingLocation_incrementsFailureCounter() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        NodeContextChangeFeedListener l = new NodeContextChangeFeedListener(
                debouncer, publisher, nodeDao, meters);

        l.onNodeDeleted(eventForNode(EventConstants.NODE_DELETED_EVENT_UEI, 9));

        verifyNoInteractions(publisher);
        assertThat(meters.counter("deltav_node_context_records_failed_total",
                "location", "", "reason", "missing_location").count()).isEqualTo(1.0);
    }

    @Test
    void onNodeLocationChanged_publishesRelocationAndEvicts() {
        Event e = eventForNode(EventConstants.NODE_LOCATION_CHANGED_EVENT_UEI, 10);
        addParm(e, "oldLocation", "Site-A");
        addParm(e, "newLocation", "Site-B");

        listener.onNodeLocationChanged(e);

        verify(publisher).publishRelocation(10, "Site-A", "Site-B");
        verify(debouncer).evict(10);
    }

    @Test
    void onImportSuccessful_enqueuesEveryNodeInForeignSource() {
        Event e = new Event();
        e.setUei(EventConstants.IMPORT_SUCCESSFUL_UEI);
        addParm(e, "foreignSource", "fs-1");
        when(nodeDao.findByForeignSource("fs-1")).thenReturn(List.of(
                nodeWithId(100), nodeWithId(101), nodeWithId(102)));

        listener.onImportSuccessful(e);

        verify(debouncer).enqueueUpdate(100);
        verify(debouncer).enqueueUpdate(101);
        verify(debouncer).enqueueUpdate(102);
    }

    @Test
    void onNodeUpdated_nullNodeid_skippedWithMalformedEventCounter() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        NodeContextChangeFeedListener l = new NodeContextChangeFeedListener(
                debouncer, publisher, nodeDao, meters);
        Event e = new Event();
        e.setUei(EventConstants.NODE_UPDATED_EVENT_UEI);
        e.setNodeid(null);

        l.onNodeUpdated(e);

        verifyNoInteractions(debouncer);
        assertThat(meters.counter("deltav_node_context_records_failed_total",
                "location", "", "reason", "malformed_event").count()).isEqualTo(1.0);
    }

    // --- helpers ---

    private static Event eventForNode(String uei, long nodeid) {
        Event e = new Event();
        e.setUei(uei);
        e.setNodeid(nodeid);
        return e;
    }

    private static void addParm(Event e, String name, String value) {
        Value v = new Value();
        v.setContent(value);
        Parm p = new Parm();
        p.setParmName(name);
        p.setValue(v);
        e.addParm(p);
    }

    private static OnmsNode nodeWithId(int id) {
        OnmsNode n = new OnmsNode();
        n.setId(id);
        return n;
    }
}
