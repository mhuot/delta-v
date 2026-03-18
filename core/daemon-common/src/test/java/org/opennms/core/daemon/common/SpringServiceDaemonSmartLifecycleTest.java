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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.opennms.netmgt.daemon.SpringServiceDaemon;

class SpringServiceDaemonSmartLifecycleTest {

    private boolean initCalled = false;
    private boolean startCalled = false;
    private boolean destroyCalled = false;

    private final SpringServiceDaemon mockDaemon = new SpringServiceDaemon() {
        @Override
        public void afterPropertiesSet() {
            initCalled = true;
        }

        @Override
        public void start() {
            startCalled = true;
        }

        @Override
        public void destroy() {
            destroyCalled = true;
        }
    };

    @Test
    void startCallsAfterPropertiesSetThenStart() throws Exception {
        var lifecycle = new SpringServiceDaemonSmartLifecycle(mockDaemon, "TestDaemon");
        assertThat(lifecycle.isRunning()).isFalse();
        lifecycle.start();
        assertThat(initCalled).isTrue();
        assertThat(startCalled).isTrue();
        assertThat(lifecycle.isRunning()).isTrue();
    }

    @Test
    void stopCallsDestroy() throws Exception {
        var lifecycle = new SpringServiceDaemonSmartLifecycle(mockDaemon, "TestDaemon");
        lifecycle.start();
        lifecycle.stop();
        assertThat(destroyCalled).isTrue();
        assertThat(lifecycle.isRunning()).isFalse();
    }

    @Test
    void phaseIsMaxValue() {
        var lifecycle = new SpringServiceDaemonSmartLifecycle(mockDaemon, "TestDaemon");
        assertThat(lifecycle.getPhase()).isEqualTo(Integer.MAX_VALUE);
    }
}
