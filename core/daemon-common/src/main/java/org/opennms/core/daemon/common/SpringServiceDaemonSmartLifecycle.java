/*
 * Licensed to The OpenNMS Group, Inc (TOG) under one or more
 * contributor license agreements.  See the LICENSE.md file
 * distributed with this work for additional information
 * regarding copyright ownership.
 *
 * TOG licenses this file to You under the GNU Affero General
 * Public License Version 3 (the "License") or (at your option)
 * any later version.  You may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at:
 *
 *      https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */
package org.opennms.core.daemon.common;

import org.opennms.netmgt.daemon.SpringServiceDaemon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Adapts a {@link SpringServiceDaemon} to Spring's {@link SmartLifecycle} interface.
 *
 * <p>{@code SpringServiceDaemon} extends {@code InitializingBean} and {@code DisposableBean},
 * so this adapter calls {@link SpringServiceDaemon#afterPropertiesSet()} followed by
 * {@link SpringServiceDaemon#start()} on startup, and {@link SpringServiceDaemon#destroy()}
 * on shutdown.</p>
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
