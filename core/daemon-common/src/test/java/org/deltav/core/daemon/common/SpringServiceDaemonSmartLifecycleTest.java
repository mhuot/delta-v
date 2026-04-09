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
