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

import io.micrometer.core.instrument.MeterRegistry;

import java.util.List;

import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.events.api.EventConstants;
import org.opennms.netmgt.events.api.annotations.EventHandler;
import org.opennms.netmgt.events.api.annotations.EventListener;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.xml.event.Event;
import org.opennms.netmgt.xml.event.Parm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Subscribes to the 13 node-lifecycle UEIs and drives the node-context producer:
 * <ul>
 *   <li>Most UEIs enqueue to the debouncer (burst-coalescing).</li>
 *   <li>{@code nodeDeleted} bypasses the debouncer and publishes an explicit tombstone;
 *       also evicts any pending debouncer entry so a queued update cannot overwrite
 *       the tombstone.</li>
 *   <li>{@code nodeLocationChanged} emits a tombstone at the old-location key and a
 *       fresh publish at the new-location key, both synchronous; evicts any pending
 *       entry.</li>
 *   <li>{@code IMPORT_SUCCESSFUL_UEI} is a catch-all: enqueues every node belonging to
 *       the imported foreignSource so metadata writes that bypassed the other UEIs
 *       still get picked up.</li>
 * </ul>
 */
@EventListener(name = "NodeContextChangeFeed", logPrefix = "node-context")
public class NodeContextChangeFeedListener {

    private static final Logger LOG = LoggerFactory.getLogger(NodeContextChangeFeedListener.class);

    private final NodeContextDebouncer debouncer;
    private final NodeContextPublisher publisher;
    private final NodeDao nodeDao;
    private final MeterRegistry meters;

    public NodeContextChangeFeedListener(NodeContextDebouncer debouncer,
                                          NodeContextPublisher publisher,
                                          NodeDao nodeDao,
                                          MeterRegistry meters) {
        this.debouncer = debouncer;
        this.publisher = publisher;
        this.nodeDao = nodeDao;
        this.meters = meters;
    }

    @EventHandler(uei = EventConstants.NODE_ADDED_EVENT_UEI)
    public void onNodeAdded(Event e) {
        enqueue(e);
    }

    @EventHandler(uei = EventConstants.NODE_UPDATED_EVENT_UEI)
    public void onNodeUpdated(Event e) {
        enqueue(e);
    }

    @EventHandler(uei = EventConstants.NODE_LABEL_CHANGED_EVENT_UEI)
    public void onNodeLabelChanged(Event e) {
        enqueue(e);
    }

    @EventHandler(uei = EventConstants.NODE_INFO_CHANGED_EVENT_UEI)
    public void onNodeInfoChanged(Event e) {
        enqueue(e);
    }

    @EventHandler(uei = EventConstants.NODE_CATEGORY_MEMBERSHIP_CHANGED_EVENT_UEI)
    public void onNodeCategoryMembershipChanged(Event e) {
        enqueue(e);
    }

    @EventHandler(uei = EventConstants.ASSET_INFO_CHANGED_EVENT_UEI)
    public void onAssetInfoChanged(Event e) {
        enqueue(e);
    }

    @EventHandler(uei = EventConstants.NODE_GAINED_INTERFACE_EVENT_UEI)
    public void onNodeGainedInterface(Event e) {
        enqueue(e);
    }

    @EventHandler(uei = EventConstants.INTERFACE_DELETED_EVENT_UEI)
    public void onInterfaceDeleted(Event e) {
        enqueue(e);
    }

    @EventHandler(uei = EventConstants.NODE_GAINED_SERVICE_EVENT_UEI)
    public void onNodeGainedService(Event e) {
        enqueue(e);
    }

    @EventHandler(uei = EventConstants.SERVICE_DELETED_EVENT_UEI)
    public void onServiceDeleted(Event e) {
        enqueue(e);
    }

    @EventHandler(uei = EventConstants.NODE_DELETED_EVENT_UEI)
    public void onNodeDeleted(Event e) {
        Integer nodeId = nodeIdOrSkip(e);
        if (nodeId == null) {
            return;
        }
        String location = parm(e, "location");
        if (location == null || location.isEmpty()) {
            LOG.warn("nodeDeleted event for nodeId={} missing 'location' parm; skipping tombstone", nodeId);
            meters.counter("deltav_node_context_records_failed_total",
                    "location", "", "reason", "missing_location").increment();
            return;
        }
        publisher.publishTombstone(nodeId, location);
        debouncer.evict(nodeId);
    }

    @EventHandler(uei = EventConstants.NODE_LOCATION_CHANGED_EVENT_UEI)
    public void onNodeLocationChanged(Event e) {
        Integer nodeId = nodeIdOrSkip(e);
        if (nodeId == null) {
            return;
        }
        String oldLocation = parm(e, "oldLocation");
        String newLocation = parm(e, "newLocation");
        if (oldLocation == null || newLocation == null) {
            LOG.warn("nodeLocationChanged event for nodeId={} missing location parms; skipping", nodeId);
            meters.counter("deltav_node_context_records_failed_total",
                    "location", "", "reason", "missing_location").increment();
            return;
        }
        publisher.publishRelocation(nodeId, oldLocation, newLocation);
        debouncer.evict(nodeId);
    }

    @EventHandler(uei = EventConstants.IMPORT_SUCCESSFUL_UEI)
    public void onImportSuccessful(Event e) {
        String foreignSource = parm(e, "foreignSource");
        if (foreignSource == null) {
            LOG.debug("IMPORT_SUCCESSFUL without foreignSource parm; skipping fan-out");
            return;
        }
        try {
            List<OnmsNode> nodes = nodeDao.findByForeignSource(foreignSource);
            for (OnmsNode n : nodes) {
                if (n.getId() != null) {
                    debouncer.enqueueUpdate(n.getId());
                }
            }
        } catch (RuntimeException ex) {
            LOG.warn("IMPORT_SUCCESSFUL fan-out failed for foreignSource={}", foreignSource, ex);
            meters.counter("deltav_node_context_records_failed_total",
                    "location", "", "reason", "db_read_error").increment();
        }
    }

    private void enqueue(Event e) {
        Integer nodeId = nodeIdOrSkip(e);
        if (nodeId == null) {
            return;
        }
        debouncer.enqueueUpdate(nodeId);
    }

    private Integer nodeIdOrSkip(Event e) {
        if (!e.hasNodeid()) {
            LOG.warn("Event {} has null nodeid; skipping", e.getUei());
            meters.counter("deltav_node_context_records_failed_total",
                    "location", "", "reason", "malformed_event").increment();
            return null;
        }
        long longId = e.getNodeid();
        if (longId < Integer.MIN_VALUE || longId > Integer.MAX_VALUE) {
            LOG.warn("Event {} nodeid {} out of int range; skipping", e.getUei(), longId);
            meters.counter("deltav_node_context_records_failed_total",
                    "location", "", "reason", "malformed_event").increment();
            return null;
        }
        return (int) longId;
    }

    private static String parm(Event e, String name) {
        Parm p = e.getParm(name);
        if (p == null || p.getValue() == null) {
            return null;
        }
        return p.getValue().getContent();
    }
}
