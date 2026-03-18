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
package org.opennms.netmgt.bsm.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.opennms.netmgt.bsm.daemon.Bsmd;
import org.opennms.netmgt.bsm.service.AlarmProvider;
import org.opennms.netmgt.bsm.service.BusinessServiceManager;
import org.opennms.netmgt.bsm.service.BusinessServiceStateMachine;
import org.opennms.netmgt.events.api.EventSubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test for the BSMd Spring Boot application.
 *
 * <p>Starts a full Spring Boot context backed by Testcontainers PostgreSQL
 * (with schema.sql). Verifies that JPA DAOs are functional and that the
 * BSMd daemon, state machine, and business service manager beans are
 * properly wired.</p>
 */
@SpringBootTest(classes = BsmdApplication.class)
@Testcontainers
@Import(BsmdApplicationIT.TestConfig.class)
@Transactional
class BsmdApplicationIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("opennms")
            .withUsername("opennms")
            .withPassword("opennms")
            .withInitScript("schema.sql");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("opennms.kafka.bootstrap-servers", () -> "localhost:9092");
    }

    /**
     * Provides mock beans for services that BSMd requires but that are
     * not part of the JPA/DAO layer being tested.
     */
    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public EventSubscriptionService eventSubscriptionService() {
            return mock(EventSubscriptionService.class);
        }

        @Bean
        public AlarmProvider alarmProvider() {
            return mock(AlarmProvider.class);
        }
    }

    @Autowired
    private Bsmd bsmd;

    @Autowired
    private BusinessServiceStateMachine stateMachine;

    @Autowired
    private BusinessServiceManager manager;

    @Test
    void contextLoads() {
        assertThat(bsmd).isNotNull();
        assertThat(stateMachine).isNotNull();
        assertThat(manager).isNotNull();
    }
}
