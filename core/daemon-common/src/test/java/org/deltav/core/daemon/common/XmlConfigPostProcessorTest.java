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

import java.io.File;
import java.nio.file.Files;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class XmlConfigPostProcessorTest {

    @TempDir
    File tempDir;

    @Test
    void extractsPageSequenceParameter() throws Exception {
        String xml = """
                <poller-configuration xmlns="http://xmlns.opennms.org/xsd/config/poller">
                  <package name="example1">
                    <filter>IPADDR != '0.0.0.0'</filter>
                    <service name="Deltav-Health" interval="30000" status="on">
                      <parameter key="timeout" value="3000"/>
                      <parameter key="page-sequence">
                        <page-sequence>
                          <page host="${nodelabel}" path="/actuator/health" port="8080"
                                response-range="200-299"/>
                        </page-sequence>
                      </parameter>
                    </service>
                  </package>
                </poller-configuration>
                """;

        File configFile = new File(tempDir, "poller-configuration.xml");
        Files.writeString(configFile.toPath(), xml);

        Map<String, String> result = XmlConfigPostProcessor.extractNestedXmlParameters(configFile);

        assertThat(result).hasSize(1);
        assertThat(result).containsKey("example1:Deltav-Health:page-sequence");

        String pageSequenceXml = result.get("example1:Deltav-Health:page-sequence");
        assertThat(pageSequenceXml).contains("<page-sequence>");
        assertThat(pageSequenceXml).contains("host=\"${nodelabel}\"");
        assertThat(pageSequenceXml).contains("path=\"/actuator/health\"");
        assertThat(pageSequenceXml).contains("port=\"8080\"");
    }

    @Test
    void ignoresParametersWithValueAttribute() throws Exception {
        String xml = """
                <poller-configuration xmlns="http://xmlns.opennms.org/xsd/config/poller">
                  <package name="example1">
                    <filter>IPADDR != '0.0.0.0'</filter>
                    <service name="ICMP" interval="300000" status="on">
                      <parameter key="retry" value="2"/>
                      <parameter key="timeout" value="3000"/>
                    </service>
                  </package>
                </poller-configuration>
                """;

        File configFile = new File(tempDir, "poller-configuration.xml");
        Files.writeString(configFile.toPath(), xml);

        Map<String, String> result = XmlConfigPostProcessor.extractNestedXmlParameters(configFile);

        assertThat(result).isEmpty();
    }

    @Test
    void extractsMultiplePsmServices() throws Exception {
        String xml = """
                <poller-configuration xmlns="http://xmlns.opennms.org/xsd/config/poller">
                  <package name="example1">
                    <filter>IPADDR != '0.0.0.0'</filter>
                    <service name="Deltav-Health" interval="30000" status="on">
                      <parameter key="page-sequence">
                        <page-sequence>
                          <page host="${nodelabel}" path="/actuator/health" port="8080"
                                response-range="200-299"/>
                        </page-sequence>
                      </parameter>
                    </service>
                    <service name="Google-Search" interval="30000" status="on">
                      <parameter key="page-sequence">
                        <page-sequence>
                          <page host="${nodelabel}" path="/" port="443" scheme="https"
                                response-range="200-399"/>
                        </page-sequence>
                      </parameter>
                    </service>
                  </package>
                </poller-configuration>
                """;

        File configFile = new File(tempDir, "poller-configuration.xml");
        Files.writeString(configFile.toPath(), xml);

        Map<String, String> result = XmlConfigPostProcessor.extractNestedXmlParameters(configFile);

        assertThat(result).hasSize(2);
        assertThat(result).containsKey("example1:Deltav-Health:page-sequence");
        assertThat(result).containsKey("example1:Google-Search:page-sequence");

        assertThat(result.get("example1:Google-Search:page-sequence"))
                .contains("scheme=\"https\"");
    }

    @Test
    void ignoresNestedParametersInsidePageSequence() throws Exception {
        // <parameter> elements inside <page> (e.g., form post params) should NOT be extracted
        String xml = """
                <poller-configuration xmlns="http://xmlns.opennms.org/xsd/config/poller">
                  <package name="example1">
                    <filter>IPADDR != '0.0.0.0'</filter>
                    <service name="HypericHQ" interval="300000" status="on">
                      <parameter key="page-sequence">
                        <page-sequence>
                          <page path="/login" method="POST" response-range="200-399">
                            <parameter key="j_username" value="admin"/>
                            <parameter key="j_password" value="admin"/>
                          </page>
                        </page-sequence>
                      </parameter>
                    </service>
                  </package>
                </poller-configuration>
                """;

        File configFile = new File(tempDir, "poller-configuration.xml");
        Files.writeString(configFile.toPath(), xml);

        Map<String, String> result = XmlConfigPostProcessor.extractNestedXmlParameters(configFile);

        // Only the top-level page-sequence parameter should be extracted, not the nested ones
        assertThat(result).hasSize(1);
        assertThat(result).containsKey("example1:HypericHQ:page-sequence");

        // The nested <parameter> elements should be INSIDE the serialized XML
        String pageSequenceXml = result.get("example1:HypericHQ:page-sequence");
        assertThat(pageSequenceXml).contains("j_username");
        assertThat(pageSequenceXml).contains("j_password");
    }

    @Test
    void returnsEmptyMapForNonexistentFile() {
        Map<String, String> result = XmlConfigPostProcessor.extractNestedXmlParameters(
                new File("/nonexistent/poller-configuration.xml"));
        assertThat(result).isEmpty();
    }
}
