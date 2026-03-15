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
package org.opennms.netmgt.alarmd.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Date;

import org.junit.jupiter.api.Test;
import org.opennms.netmgt.dao.api.AlarmEntityNotifier;
import org.opennms.netmgt.dao.api.AlarmDao;
import org.opennms.netmgt.dao.api.DistPollerDao;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.events.api.EventSubscriptionService;
import org.opennms.netmgt.model.OnmsAlarm;
import org.opennms.netmgt.model.OnmsDistPoller;
import org.opennms.netmgt.model.OnmsSeverity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test for the Alarmd Spring Boot application.
 *
 * <p>Starts a full Spring Boot context backed by Testcontainers PostgreSQL
 * (with schema.sql). Verifies that JPA DAOs are functional and can
 * persist/retrieve alarms.</p>
 */
@SpringBootTest(classes = AlarmdApplication.class)
@Testcontainers
@Import(AlarmdApplicationIT.TestConfig.class)
@Transactional
class AlarmdApplicationIT {

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
     * Provides mock beans for services that Alarmd requires but that are
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
        public AlarmEntityNotifier alarmEntityNotifier() {
            return mock(AlarmEntityNotifier.class);
        }

        @Bean
        public org.opennms.netmgt.eventd.EventUtil eventUtil() {
            return mock(org.opennms.netmgt.eventd.EventUtil.class);
        }

        @Bean
        public TransactionOperations transactionOperations(PlatformTransactionManager txManager) {
            return new TransactionTemplate(txManager);
        }

        @Bean
        public org.opennms.netmgt.dao.api.SessionUtils sessionUtils() {
            return mock(org.opennms.netmgt.dao.api.SessionUtils.class);
        }

        @Bean(name = "eventProxy")
        public org.opennms.netmgt.events.api.EventProxy eventProxy() {
            return mock(org.opennms.netmgt.events.api.EventProxy.class);
        }

    }

    @Autowired
    private AlarmDao alarmDao;

    @Autowired
    private DistPollerDao distPollerDao;

    @Autowired
    private NodeDao nodeDao;

    @Test
    void contextLoads() {
        assertThat(alarmDao).isNotNull();
        assertThat(distPollerDao).isNotNull();
        assertThat(nodeDao).isNotNull();
    }

    @Test
    void canPersistAndRetrieveAlarm() {
        OnmsDistPoller distPoller = distPollerDao.whoami();
        assertThat(distPoller).isNotNull();

        OnmsAlarm alarm = new OnmsAlarm();
        alarm.setUei("uei.opennms.org/test/alarm");
        alarm.setDistPoller(distPoller);
        alarm.setCounter(1);
        alarm.setSeverity(OnmsSeverity.MAJOR);
        alarm.setReductionKey("uei.opennms.org/test/alarm::1");
        alarm.setFirstEventTime(new Date());
        alarm.setLastEventTime(new Date());
        alarm.setLogMsg("Test alarm");

        alarmDao.save(alarm);
        alarmDao.flush();

        assertThat(alarm.getId()).isNotNull();

        OnmsAlarm retrieved = alarmDao.get(alarm.getId());
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getUei()).isEqualTo("uei.opennms.org/test/alarm");
        assertThat(retrieved.getSeverity()).isEqualTo(OnmsSeverity.MAJOR);
        assertThat(retrieved.getCounter()).isEqualTo(1);
    }

    @Test
    void findByReductionKey() {
        OnmsDistPoller distPoller = distPollerDao.whoami();

        OnmsAlarm alarm = new OnmsAlarm();
        alarm.setUei("uei.opennms.org/test/findByKey");
        alarm.setDistPoller(distPoller);
        alarm.setCounter(1);
        alarm.setSeverity(OnmsSeverity.WARNING);
        alarm.setReductionKey("uei.opennms.org/test/findByKey::unique");
        alarm.setFirstEventTime(new Date());
        alarm.setLastEventTime(new Date());
        alarm.setLogMsg("Test find by key");

        alarmDao.save(alarm);
        alarmDao.flush();

        OnmsAlarm found = alarmDao.findByReductionKey("uei.opennms.org/test/findByKey::unique");
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(alarm.getId());
    }
}
