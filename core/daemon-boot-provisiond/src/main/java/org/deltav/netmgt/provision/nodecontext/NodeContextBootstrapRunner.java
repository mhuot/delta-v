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
import io.micrometer.core.instrument.Timer;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.dao.api.SessionUtils;
import org.opennms.netmgt.model.OnmsNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Publishes a NodeContext for every existing node on provisiond startup.
 * Blocks {@code start()} until enumeration completes so the topic reflects
 * the DB before provisiond reports ready.
 *
 * <p>Phase numerically greater than {@code provisiondLifecycle} so DAO and
 * transaction beans are fully initialized before the bootstrap runs.</p>
 */
public class NodeContextBootstrapRunner implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(NodeContextBootstrapRunner.class);
    private static final int PHASE = Integer.MAX_VALUE - 100;
    private static final int PROGRESS_LOG_INTERVAL = 1000;

    private final NodeContextPublisher publisher;
    private final NodeDao nodeDao;
    private final SessionUtils sessionUtils;
    private final MeterRegistry meters;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public NodeContextBootstrapRunner(NodeContextPublisher publisher,
                                      NodeDao nodeDao,
                                      SessionUtils sessionUtils,
                                      MeterRegistry meters) {
        this.publisher = publisher;
        this.nodeDao = nodeDao;
        this.sessionUtils = sessionUtils;
        this.meters = meters;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        LOG.info("Node-context bootstrap starting");
        Timer.Sample sample = Timer.start(meters);
        int published = 0;
        try {
            List<OnmsNode> nodes = sessionUtils.withReadOnlyTransaction(() -> nodeDao.findAll());
            for (OnmsNode node : nodes) {
                if (node.getId() == null) {
                    continue;
                }
                try {
                    publisher.publishNode(node.getId(), "bootstrap");
                    published++;
                    if (published % PROGRESS_LOG_INTERVAL == 0) {
                        LOG.info("Node-context bootstrap progress: {} records published", published);
                    }
                } catch (Throwable t) {
                    LOG.warn("Bootstrap publish failed for nodeId={}; continuing", node.getId(), t);
                }
            }
            LOG.info("Node-context bootstrap complete: {} records published", published);
        } catch (Throwable t) {
            LOG.error("Node-context bootstrap enumeration failed after {} publishes", published, t);
            meters.counter("deltav_node_context_records_failed_total",
                    "location", "", "reason", "bootstrap_error").increment();
        } finally {
            sample.stop(Timer.builder("deltav_node_context_bootstrap_duration_seconds")
                    .register(meters));
        }
    }

    @Override
    public void stop() {
        running.set(false);
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return PHASE;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }
}
