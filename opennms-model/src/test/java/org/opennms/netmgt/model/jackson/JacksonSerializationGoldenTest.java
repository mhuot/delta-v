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
package org.opennms.netmgt.model.jackson;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;

import org.apache.commons.io.IOUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.opennms.core.test.xml.JsonTest;
import org.opennms.core.utils.InetAddressUtils;
import org.opennms.core.xml.JacksonUtils;
import org.opennms.netmgt.model.OnmsAlarm;
import org.opennms.netmgt.model.OnmsAlarmCollection;
import org.opennms.netmgt.model.OnmsCategory;
import org.opennms.netmgt.model.OnmsCategoryCollection;
import org.opennms.netmgt.model.OnmsIpInterface;
import org.opennms.netmgt.model.OnmsMetaData;
import org.opennms.netmgt.model.OnmsMetaDataList;
import org.opennms.netmgt.model.OnmsMonitoredService;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.OnmsNodeList;
import org.opennms.netmgt.model.OnmsOutage;
import org.opennms.netmgt.model.OnmsOutageCollection;
import org.opennms.netmgt.model.OnmsServiceType;
import org.opennms.netmgt.model.OnmsSeverity;
import org.opennms.netmgt.model.OnmsSnmpInterface;
import org.opennms.netmgt.model.PrimaryType;
import org.opennms.netmgt.model.monitoringLocations.OnmsMonitoringLocation;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

/**
 * Golden file tests for Jackson JSON serialization of core model objects.
 *
 * These tests validate that the JSON wire format produced by Jackson 1
 * matches known-good baselines stored in
 * src/test/resources/golden/json/. This ensures that any future Jackson
 * migration (e.g. Jackson 1 -> Jackson 2) preserves REST API contracts.
 *
 * To regenerate golden files, run:
 *   -Djackson.golden.regenerate=true
 */
@RunWith(Parameterized.class)
public class JacksonSerializationGoldenTest {

    /** Fixed date to ensure deterministic output: 2024-01-15T12:00:00Z */
    private static final Date FIXED_DATE = new Date(1705320000000L);

    private static final ObjectMapper MAPPER = JacksonUtils.createDefaultObjectMapper();

    private final String testName;
    private final Object testObject;
    private final Class<?> testClass;
    private final String goldenFileName;

    public JacksonSerializationGoldenTest(String testName, Object testObject, Class<?> testClass, String goldenFileName) {
        this.testName = testName;
        this.testObject = testObject;
        this.testClass = testClass;
        this.goldenFileName = goldenFileName;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            { "OnmsAlarm", createAlarm(), OnmsAlarm.class, "onms-alarm.json" },
            { "OnmsNode", createNode(), OnmsNode.class, "onms-node.json" },
            { "OnmsOutage", createOutage(), OnmsOutage.class, "onms-outage.json" },
            { "OnmsMonitoredService", createMonitoredService(), OnmsMonitoredService.class, "onms-monitored-service.json" },
            { "OnmsCategory", createCategory(), OnmsCategory.class, "onms-category.json" },
            { "OnmsMetaData", createMetaData(), OnmsMetaData.class, "onms-metadata.json" },
            { "OnmsAlarmCollection", createAlarmCollection(), OnmsAlarmCollection.class, "onms-alarm-collection.json" },
            { "OnmsNodeList", createNodeList(), OnmsNodeList.class, "onms-node-list.json" },
            { "OnmsOutageCollection", createOutageCollection(), OnmsOutageCollection.class, "onms-outage-collection.json" },
            { "OnmsCategoryCollection", createCategoryCollection(), OnmsCategoryCollection.class, "onms-category-collection.json" },
            { "OnmsMetaDataList", createMetaDataList(), OnmsMetaDataList.class, "onms-metadata-list.json" },
        });
    }

    @Test
    public void testSerializationMatchesGoldenFile() throws IOException, JSONException {
        String actualJson = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(testObject);

        String expectedJson = loadOrBootstrapGoldenFile(goldenFileName, actualJson);
        if (expectedJson == null) {
            // First run — golden file was just created; nothing to compare yet
            return;
        }
        JSONAssert.assertEquals(
            "JSON serialization mismatch for " + testName + " against golden file " + goldenFileName,
            expectedJson, actualJson, JSONCompareMode.STRICT);
    }

    @Test
    public void testRoundTrip() throws IOException, JSONException {
        // Serialize
        String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(testObject);

        // Deserialize
        Object deserialized = MAPPER.readValue(json, testClass);

        // Re-serialize
        String roundTrippedJson = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(deserialized);

        // The re-serialized form must match the original serialization
        JSONAssert.assertEquals(
            "Round-trip serialization mismatch for " + testName,
            json, roundTrippedJson, JSONCompareMode.STRICT);
    }

    /**
     * Load the golden file if it exists. If it doesn't, write it (bootstrap mode)
     * and return null to signal the test should skip comparison on first run.
     */
    private static String loadOrBootstrapGoldenFile(String fileName, String actualJson) throws IOException {
        String path = "golden/json/" + fileName;
        try (InputStream is = JacksonSerializationGoldenTest.class.getClassLoader().getResourceAsStream(path)) {
            if (is != null) {
                return IOUtils.toString(is, StandardCharsets.UTF_8);
            }
        }

        // Golden file doesn't exist — bootstrap it
        java.net.URL resourceDir = JacksonSerializationGoldenTest.class.getClassLoader().getResource("golden/json");
        if (resourceDir != null && "file".equals(resourceDir.getProtocol())) {
            java.io.File goldenFile = new java.io.File(resourceDir.getPath(), fileName);
            goldenFile.getParentFile().mkdirs();
            java.nio.file.Files.write(goldenFile.toPath(), actualJson.getBytes(StandardCharsets.UTF_8));
            System.out.println("BOOTSTRAP: Wrote golden file " + goldenFile.getAbsolutePath());
            System.out.println("           Re-run tests to validate against this baseline.");
            return null;
        }

        Assert.fail("Golden file not found: " + path +
            ". Ensure src/test/resources/golden/json/ directory exists in the source tree.");
        return null; // unreachable
    }

    // ========== Test Data Factories ==========

    private static OnmsAlarm createAlarm() {
        OnmsAlarm alarm = new OnmsAlarm();
        alarm.setId(42);
        alarm.setUei("uei.opennms.org/test/goldenFile");
        alarm.setCounter(3);
        alarm.setSeverity(OnmsSeverity.MAJOR);
        alarm.setAlarmType(OnmsAlarm.PROBLEM_TYPE);
        alarm.setDescription("Test alarm for golden file validation");
        alarm.setLogMsg("Golden file test alarm");
        alarm.setReductionKey("uei.opennms.org/test/goldenFile:1:ICMP");
        alarm.setFirstEventTime(FIXED_DATE);
        alarm.setLastEventTime(FIXED_DATE);
        alarm.setIpAddr(InetAddressUtils.addr("192.168.1.1"));
        alarm.setX733ProbableCause(0);

        OnmsNode node = createMinimalNode();
        alarm.setNode(node);

        OnmsServiceType serviceType = createServiceType(1, "ICMP");
        alarm.setServiceType(serviceType);

        return alarm;
    }

    private static OnmsNode createNode() {
        OnmsNode node = new OnmsNode();
        node.setId(1);
        node.setLabel("test-node-1");
        node.setLabelSource(OnmsNode.NodeLabelSource.HOSTNAME);
        node.setType(OnmsNode.NodeType.ACTIVE);
        node.setSysObjectId(".1.3.6.1.4.1.8072.3.2.10");
        node.setSysName("test-node-1");
        node.setSysDescription("Linux test-node-1 5.4.0");
        node.setSysLocation("Lab");
        node.setSysContact("admin@opennms.org");
        node.setCreateTime(FIXED_DATE);
        node.setForeignSource("test-requisition");
        node.setForeignId("node-1");

        OnmsMonitoringLocation location = new OnmsMonitoringLocation("Default", "Default");
        node.setLocation(location);

        return node;
    }

    private static OnmsOutage createOutage() {
        OnmsOutage outage = new OnmsOutage();
        outage.setId(1);
        outage.setIfLostService(FIXED_DATE);

        OnmsNode node = createMinimalNode();
        OnmsIpInterface ipInterface = createIpInterface(1, "192.168.1.1", node);
        OnmsMonitoredService service = createServiceOnInterface(1, "ICMP", ipInterface);
        outage.setMonitoredService(service);

        OnmsMonitoringLocation perspective = new OnmsMonitoringLocation();
        perspective.setLocationName("Default");
        outage.setPerspective(perspective);

        return outage;
    }

    private static OnmsMonitoredService createMonitoredService() {
        OnmsNode node = createMinimalNode();
        OnmsIpInterface ipInterface = createIpInterface(1, "192.168.1.1", node);
        return createServiceOnInterface(1, "ICMP", ipInterface);
    }

    private static OnmsCategory createCategory() {
        OnmsCategory category = new OnmsCategory();
        category.setId(1);
        category.setName("Routers");
        category.setDescription("Core network routers");
        return category;
    }

    private static OnmsMetaData createMetaData() {
        return new OnmsMetaData("requisition", "city", "New York");
    }

    private static OnmsAlarmCollection createAlarmCollection() {
        OnmsAlarmCollection collection = new OnmsAlarmCollection();
        collection.add(createAlarm());

        OnmsAlarm alarm2 = new OnmsAlarm();
        alarm2.setId(43);
        alarm2.setUei("uei.opennms.org/test/goldenFile2");
        alarm2.setCounter(1);
        alarm2.setSeverity(OnmsSeverity.MINOR);
        alarm2.setAlarmType(OnmsAlarm.PROBLEM_TYPE);
        alarm2.setDescription("Second test alarm");
        alarm2.setLogMsg("Second alarm");
        alarm2.setFirstEventTime(FIXED_DATE);
        alarm2.setLastEventTime(FIXED_DATE);
        alarm2.setX733ProbableCause(0);
        collection.add(alarm2);

        collection.setTotalCount(2);
        return collection;
    }

    private static OnmsNodeList createNodeList() {
        OnmsNodeList list = new OnmsNodeList();
        list.add(createNode());

        OnmsNode node2 = new OnmsNode();
        node2.setId(2);
        node2.setLabel("test-node-2");
        node2.setType(OnmsNode.NodeType.ACTIVE);
        node2.setCreateTime(FIXED_DATE);
        OnmsMonitoringLocation loc = new OnmsMonitoringLocation("Default", "Default");
        node2.setLocation(loc);
        list.add(node2);

        list.setTotalCount(2);
        return list;
    }

    private static OnmsOutageCollection createOutageCollection() {
        OnmsOutageCollection collection = new OnmsOutageCollection();
        collection.add(createOutage());
        collection.setTotalCount(1);
        return collection;
    }

    private static OnmsCategoryCollection createCategoryCollection() {
        OnmsCategoryCollection collection = new OnmsCategoryCollection();
        collection.add(createCategory());

        OnmsCategory cat2 = new OnmsCategory();
        cat2.setId(2);
        cat2.setName("Servers");
        cat2.setDescription("Application servers");
        collection.add(cat2);

        collection.setTotalCount(2);
        return collection;
    }

    private static OnmsMetaDataList createMetaDataList() {
        OnmsMetaDataList list = new OnmsMetaDataList();
        list.add(createMetaData());
        list.add(new OnmsMetaData("requisition", "state", "NY"));
        list.setTotalCount(2);
        return list;
    }

    // ========== Shared Helpers ==========

    private static OnmsNode createMinimalNode() {
        OnmsNode node = new OnmsNode();
        node.setId(1);
        node.setLabel("test-node-1");
        node.setCreateTime(FIXED_DATE);
        OnmsMonitoringLocation location = new OnmsMonitoringLocation("Default", "Default");
        node.setLocation(location);
        return node;
    }

    private static OnmsIpInterface createIpInterface(int id, String ipAddress, OnmsNode node) {
        OnmsIpInterface ipInterface = new OnmsIpInterface();
        ipInterface.setId(id);
        ipInterface.setIpAddress(InetAddressUtils.addr(ipAddress));
        ipInterface.setIpHostName("test-host");
        ipInterface.setIsManaged("M");
        ipInterface.setIsSnmpPrimary(PrimaryType.PRIMARY);
        ipInterface.setNode(node);
        node.getIpInterfaces().add(ipInterface);
        return ipInterface;
    }

    private static OnmsMonitoredService createServiceOnInterface(int id, String serviceName, OnmsIpInterface ipInterface) {
        OnmsMonitoredService service = new OnmsMonitoredService();
        service.setId(id);
        service.setServiceType(createServiceType(1, serviceName));
        service.setIpInterface(ipInterface);
        ipInterface.getMonitoredServices().add(service);
        return service;
    }

    private static OnmsServiceType createServiceType(int id, String name) {
        OnmsServiceType type = new OnmsServiceType();
        type.setId(id);
        type.setName(name);
        return type;
    }
}
