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
package org.deltav.core.daemon.common;

import org.opennms.netmgt.daemon.AbstractServiceDaemon;
import org.opennms.netmgt.daemon.SpringServiceDaemon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Adapts a {@link SpringServiceDaemon} to Spring's {@link SmartLifecycle} interface,
 * providing a single, consistent lifecycle adapter for all OpenNMS daemons.
 *
 * <p>{@code SpringServiceDaemon} extends {@code InitializingBean} and {@code DisposableBean},
 * so this adapter calls {@link SpringServiceDaemon#afterPropertiesSet()} followed by
 * {@link SpringServiceDaemon#start()} on startup, and {@link SpringServiceDaemon#destroy()}
 * on shutdown.</p>
 *
 * <p>For {@link AbstractServiceDaemon} subclasses, {@code afterPropertiesSet()} delegates
 * to {@code init()}, and {@code destroy()} delegates to {@code stop()}, so the behavior
 * is identical to the former {@code DaemonSmartLifecycle}.</p>
 *
 * <p>The phase is set to {@link Integer#MAX_VALUE} so daemons start last (after all
 * infrastructure beans) and stop first during shutdown.</p>
 */
public class SpringServiceDaemonSmartLifecycle implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(SpringServiceDaemonSmartLifecycle.class);

    private final SpringServiceDaemon daemon;
    private final String name;
    private volatile boolean running = false;

    public SpringServiceDaemonSmartLifecycle(SpringServiceDaemon daemon, String name) {
        this.daemon = daemon;
        this.name = name;
    }

    /**
     * Convenience constructor for {@link AbstractServiceDaemon} subclasses,
     * which already expose their name via {@link AbstractServiceDaemon#getName()}.
     */
    public SpringServiceDaemonSmartLifecycle(AbstractServiceDaemon daemon) {
        this(daemon, daemon.getName());
    }

    @Override
    public void start() {
        LOG.info("Starting daemon: {}", name);
        try {
            daemon.afterPropertiesSet();
            daemon.start();
        } catch (Exception e) {
            throw new RuntimeException("Failed to start daemon: " + name, e);
        }
        running = true;
        LOG.info("Daemon started: {}", name);
    }

    @Override
    public void stop() {
        LOG.info("Stopping daemon: {}", name);
        try {
            daemon.destroy();
        } catch (Exception e) {
            LOG.error("Error stopping daemon: {}", name, e);
        }
        running = false;
        LOG.info("Daemon stopped: {}", name);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
