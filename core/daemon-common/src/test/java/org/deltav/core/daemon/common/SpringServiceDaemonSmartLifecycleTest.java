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
package org.deltav.core.daemon.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.opennms.netmgt.daemon.AbstractServiceDaemon;
import org.opennms.netmgt.daemon.SpringServiceDaemon;

class SpringServiceDaemonSmartLifecycleTest {

    @Nested
    class WithSpringServiceDaemon {

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
        void startCallsAfterPropertiesSetThenStart() {
            var lifecycle = new SpringServiceDaemonSmartLifecycle(mockDaemon, "TestDaemon");
            assertThat(lifecycle.isRunning()).isFalse();
            lifecycle.start();
            assertThat(initCalled).isTrue();
            assertThat(startCalled).isTrue();
            assertThat(lifecycle.isRunning()).isTrue();
        }

        @Test
        void stopCallsDestroy() {
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

    @Nested
    class WithAbstractServiceDaemon {

        private static class TestDaemon extends AbstractServiceDaemon {
            boolean initialized = false;
            boolean started = false;
            boolean stopped = false;

            TestDaemon() {
                super("test-daemon");
            }

            @Override
            protected void onInit() {
                initialized = true;
            }

            @Override
            protected void onStart() {
                started = true;
            }

            @Override
            protected void onStop() {
                stopped = true;
            }
        }

        @Test
        void convenienceConstructorExtractsName() {
            var daemon = new TestDaemon();
            var lifecycle = new SpringServiceDaemonSmartLifecycle(daemon);
            assertThat(lifecycle.isAutoStartup()).isTrue();
        }

        @Test
        void startCallsInitThenStart() {
            var daemon = new TestDaemon();
            var lifecycle = new SpringServiceDaemonSmartLifecycle(daemon);

            lifecycle.start();

            assertThat(daemon.initialized).isTrue();
            assertThat(daemon.started).isTrue();
            assertThat(lifecycle.isRunning()).isTrue();
        }

        @Test
        void stopCallsStop() {
            var daemon = new TestDaemon();
            var lifecycle = new SpringServiceDaemonSmartLifecycle(daemon);

            lifecycle.start();
            lifecycle.stop();

            assertThat(daemon.stopped).isTrue();
            assertThat(lifecycle.isRunning()).isFalse();
        }

        @Test
        void isAutoStartupReturnsTrue() {
            var daemon = new TestDaemon();
            var lifecycle = new SpringServiceDaemonSmartLifecycle(daemon);

            assertThat(lifecycle.isAutoStartup()).isTrue();
        }
    }
}
