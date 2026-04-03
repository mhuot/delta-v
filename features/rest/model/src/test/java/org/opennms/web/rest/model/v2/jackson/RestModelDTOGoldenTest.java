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
package org.opennms.web.rest.model.v2.jackson;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;

import org.apache.commons.io.IOUtils;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;
import org.json.JSONException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.opennms.core.utils.InetAddressUtils;
import org.opennms.web.rest.model.v2.AlarmCollectionDTO;
import org.opennms.web.rest.model.v2.AlarmDTO;
import org.opennms.web.rest.model.v2.AlarmSummaryDTO;
import org.opennms.web.rest.model.v2.EventCollectionDTO;
import org.opennms.web.rest.model.v2.EventDTO;
import org.opennms.web.rest.model.v2.EventParameterDTO;
import org.opennms.web.rest.model.v2.MonitoredServiceCollectionDTO;
import org.opennms.web.rest.model.v2.MonitoredServiceDTO;
import org.opennms.web.rest.model.v2.ServiceTypeDTO;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

/**
 * Golden file tests for Jackson JSON serialization of REST v2 model DTOs.
 *
 * These DTOs use JAXB annotations (with JaxbAnnotationIntrospector) for JSON
 * output. This test validates that any Jackson migration preserves the REST API
 * wire format.
 *
 * To regenerate golden files, run:
 *   -Djackson.golden.regenerate=true
 */
@RunWith(Parameterized.class)
public class RestModelDTOGoldenTest {

    /** Fixed date to ensure deterministic output: 2024-01-15T12:00:00Z */
    private static final Date FIXED_DATE = new Date(1705320000000L);

    private static final ObjectMapper MAPPER = createMapper();

    private final String testName;
    private final Object testObject;
    private final Class<?> testClass;
    private final String goldenFileName;

    public RestModelDTOGoldenTest(String testName, Object testObject, Class<?> testClass, String goldenFileName) {
        this.testName = testName;
        this.testObject = testObject;
        this.testClass = testClass;
        this.goldenFileName = goldenFileName;
    }

    private static ObjectMapper createMapper() {
        final ObjectMapper mapper = new ObjectMapper();
        final AnnotationIntrospector introspectorPair = AnnotationIntrospector.pair(
                new JacksonAnnotationIntrospector(),
                new JaxbAnnotationIntrospector(mapper.getTypeFactory()));
        mapper.setAnnotationIntrospector(introspectorPair);
        return mapper;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            { "AlarmDTO", createAlarmDTO(), AlarmDTO.class, "alarm-dto.json" },
            { "AlarmCollectionDTO", createAlarmCollectionDTO(), AlarmCollectionDTO.class, "alarm-collection-dto.json" },
            { "EventDTO", createEventDTO(), EventDTO.class, "event-dto.json" },
            { "EventCollectionDTO", createEventCollectionDTO(), EventCollectionDTO.class, "event-collection-dto.json" },
            { "MonitoredServiceDTO", createMonitoredServiceDTO(), MonitoredServiceDTO.class, "monitored-service-dto.json" },
            { "MonitoredServiceCollectionDTO", createMonitoredServiceCollectionDTO(), MonitoredServiceCollectionDTO.class, "monitored-service-collection-dto.json" },
            { "AlarmSummaryDTO", createAlarmSummaryDTO(), AlarmSummaryDTO.class, "alarm-summary-dto.json" },
        });
    }

    @Test
    public void testSerializationMatchesGoldenFile() throws IOException, JSONException {
        String actualJson = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(testObject);

        if (Boolean.getBoolean("jackson.golden.regenerate")) {
            System.out.println("=== Golden file for " + testName + " (" + goldenFileName + ") ===");
            System.out.println(actualJson);
            System.out.println("=== End golden file ===");
            return;
        }

        String expectedJson = loadGoldenFile(goldenFileName);
        JSONAssert.assertEquals(
            "JSON serialization mismatch for " + testName + " against golden file " + goldenFileName,
            expectedJson, actualJson, JSONCompareMode.STRICT);
    }

    @Test
    public void testRoundTrip() throws IOException, JSONException {
        String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(testObject);
        Object deserialized = MAPPER.readValue(json, testClass);
        String roundTrippedJson = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(deserialized);
        JSONAssert.assertEquals(
            "Round-trip serialization mismatch for " + testName,
            json, roundTrippedJson, JSONCompareMode.STRICT);
    }

    private static String loadGoldenFile(String fileName) throws IOException {
        String path = "golden/json/" + fileName;
        try (InputStream is = RestModelDTOGoldenTest.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                Assert.fail("Golden file not found: " + path +
                    ". Run with -Djackson.golden.regenerate=true to generate it.");
            }
            return IOUtils.toString(is, StandardCharsets.UTF_8);
        }
    }

    // ========== Test Data Factories ==========

    private static AlarmDTO createAlarmDTO() {
        AlarmDTO alarm = new AlarmDTO();
        alarm.setId(42);
        alarm.setUei("uei.opennms.org/test/goldenFile");
        alarm.setNodeId(1);
        alarm.setNodeLabel("test-node-1");
        alarm.setIpAddress(InetAddressUtils.addr("192.168.1.1"));
        alarm.setType(1);
        alarm.setCount(3);
        alarm.setSeverity("MAJOR");
        alarm.setDescription("Test alarm for golden file validation");
        alarm.setLogMessage("Golden file test alarm");
        alarm.setReductionKey("uei.opennms.org/test/goldenFile:1:ICMP");
        alarm.setFirstEventTime(FIXED_DATE);
        alarm.setLastEventTime(FIXED_DATE);
        alarm.setLocation("Default");

        ServiceTypeDTO serviceType = new ServiceTypeDTO();
        serviceType.setId(1);
        serviceType.setName("ICMP");
        alarm.setServiceType(serviceType);

        return alarm;
    }

    private static AlarmCollectionDTO createAlarmCollectionDTO() {
        AlarmCollectionDTO collection = new AlarmCollectionDTO();
        collection.add(createAlarmDTO());
        collection.setTotalCount(1);
        return collection;
    }

    private static EventDTO createEventDTO() {
        EventDTO event = new EventDTO();
        event.setId(100L);
        event.setUei("uei.opennms.org/test/goldenFile");
        event.setNodeId(1);
        event.setNodeLabel("test-node-1");
        event.setIpAddress(InetAddressUtils.addr("192.168.1.1"));
        event.setSeverity("WARNING");
        event.setLog("Y");
        event.setDisplay("Y");
        event.setDescription("Test event for golden file");
        event.setLogMessage("Golden file test event");
        event.setSource("test");
        event.setCreateTime(FIXED_DATE);
        event.setTime(FIXED_DATE);

        EventParameterDTO param = new EventParameterDTO();
        param.setName("interface");
        param.setValue("192.168.1.1");
        param.setType("string");
        event.setParameters(Arrays.asList(param));

        return event;
    }

    private static EventCollectionDTO createEventCollectionDTO() {
        EventCollectionDTO collection = new EventCollectionDTO();
        collection.add(createEventDTO());
        collection.setTotalCount(1);
        return collection;
    }

    private static MonitoredServiceDTO createMonitoredServiceDTO() {
        MonitoredServiceDTO service = new MonitoredServiceDTO();
        service.setId(1);
        service.setStatus("A");
        service.setStatusLong("Active");
        service.setDown(false);

        ServiceTypeDTO serviceType = new ServiceTypeDTO();
        serviceType.setId(1);
        serviceType.setName("ICMP");
        service.setServiceType(serviceType);

        service.setIpInterfaceId(10);
        service.setLastGood(FIXED_DATE);
        return service;
    }

    private static MonitoredServiceCollectionDTO createMonitoredServiceCollectionDTO() {
        MonitoredServiceCollectionDTO collection = new MonitoredServiceCollectionDTO();
        collection.add(createMonitoredServiceDTO());
        collection.setTotalCount(1);
        return collection;
    }

    private static AlarmSummaryDTO createAlarmSummaryDTO() {
        AlarmSummaryDTO summary = new AlarmSummaryDTO();
        summary.setId(42);
        summary.setType(1);
        summary.setSeverity("MAJOR");
        summary.setNodeLabel("test-node-1");
        summary.setLabel("Test alarm summary");
        summary.setDescription("Test alarm summary for golden file validation");
        summary.setLogMessage("Golden file summary");
        summary.setReductionKey("uei.opennms.org/test/goldenFile:1:ICMP");
        return summary;
    }
}
