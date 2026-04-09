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
package org.opennms.netmgt.bsm.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.opennms.netmgt.bsm.boot.BsmdApplication;
import org.opennms.netmgt.bsm.rest.model.BusinessServiceDto;
import org.opennms.netmgt.bsm.rest.model.ReduceFunctionDto;
import org.opennms.netmgt.bsm.service.AlarmProvider;
import org.opennms.netmgt.events.api.EventSubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for the BSMd v3 REST API.
 *
 * <p>Starts a full Spring Boot context with Testcontainers PostgreSQL and
 * exercises the {@link BsmdRestController} and {@link MonitoredServiceRestController}
 * endpoints via {@link MockMvc}.</p>
 *
 * <p>Tests are ordered so that {@code listEmptyInitially} runs before any
 * test that creates data, since all tests share the same database.</p>
 */
@SpringBootTest(classes = BsmdApplication.class)
@Testcontainers
@Import(BsmdRestControllerIT.TestConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BsmdRestControllerIT {

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
    private WebApplicationContext webApplicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    @Order(1)
    void listEmptyInitially() throws Exception {
        mockMvc.perform(get("/api/v3/business-services"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @Order(2)
    void createAndGetBusinessService() throws Exception {
        var reduceFn = new ReduceFunctionDto();
        reduceFn.setType("highestSeverity");

        var dto = new BusinessServiceDto();
        dto.setName("test-bs");
        dto.setAttributes(Map.of("env", "test"));
        dto.setReduceFunction(reduceFn);

        // Create
        MvcResult createResult = mockMvc.perform(post("/api/v3/business-services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        BusinessServiceDto created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                BusinessServiceDto.class);
        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo("test-bs");

        // Get by ID
        mockMvc.perform(get("/api/v3/business-services/" + created.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("test-bs"))
                .andExpect(jsonPath("$.reduceFunction.type").value("highestSeverity"));

        // Status (expect indeterminate -- no edges, no alarms)
        mockMvc.perform(get("/api/v3/business-services/" + created.getId() + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationalStatus").value("indeterminate"));

        // Delete
        mockMvc.perform(delete("/api/v3/business-services/" + created.getId()))
                .andExpect(status().isNoContent());

        // Verify deleted
        mockMvc.perform(get("/api/v3/business-services/" + created.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(3)
    void updateBusinessService() throws Exception {
        var reduceFn = new ReduceFunctionDto();
        reduceFn.setType("highestSeverity");

        var dto = new BusinessServiceDto();
        dto.setName("update-test");
        dto.setReduceFunction(reduceFn);

        // Create
        MvcResult createResult = mockMvc.perform(post("/api/v3/business-services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        BusinessServiceDto created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                BusinessServiceDto.class);

        // Update name
        created.setName("update-test-renamed");
        mockMvc.perform(put("/api/v3/business-services/" + created.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(created)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("update-test-renamed"));

        // Cleanup
        mockMvc.perform(delete("/api/v3/business-services/" + created.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(4)
    void listMonitoredServices() throws Exception {
        mockMvc.perform(get("/api/v3/monitored-services"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @Order(5)
    void notFoundReturns404() throws Exception {
        mockMvc.perform(get("/api/v3/business-services/999999"))
                .andExpect(status().isNotFound());
    }
}
