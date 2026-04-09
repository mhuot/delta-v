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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opennms.netmgt.xml.eventconf.Event;
import org.opennms.netmgt.xml.eventconf.LogDestType;

class DaemonEventConfDaoXmlMapperTest {

    private static XmlMapper xmlMapper;

    @BeforeAll
    static void setUp() {
        xmlMapper = new XmlMapper();
        xmlMapper.registerModule(new JaxbAnnotationModule());
        xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        xmlMapper.setDefaultUseWrapper(false);
    }

    @Test
    void deserializesBasicEventFields() throws Exception {
        String xml = """
                <event xmlns="http://xmlns.opennms.org/xsd/eventconf">
                    <uei>uei.opennms.org/test/sampleEvent</uei>
                    <event-label>Sample Test Event</event-label>
                    <descr>A test event description</descr>
                    <logmsg dest="logndisplay">Test log message</logmsg>
                    <severity>Warning</severity>
                </event>
                """;

        Event event = xmlMapper.readValue(xml, Event.class);

        assertThat(event.getUei()).isEqualTo("uei.opennms.org/test/sampleEvent");
        assertThat(event.getEventLabel()).isEqualTo("Sample Test Event");
        assertThat(event.getDescr()).isEqualTo("A test event description");
        assertThat(event.getSeverity()).isEqualTo("Warning");
        assertThat(event.getLogmsg()).isNotNull();
        assertThat(event.getLogmsg().getContent()).isEqualTo("Test log message");
        assertThat(event.getLogmsg().getDest()).isEqualTo(LogDestType.LOGNDISPLAY);
    }

    @Test
    void deserializesEventWithoutNamespace() throws Exception {
        String xml = """
                <event>
                    <uei>uei.opennms.org/test/noNamespace</uei>
                    <event-label>No Namespace Event</event-label>
                    <descr>Event without XML namespace</descr>
                    <logmsg dest="logonly">Namespace-free log message</logmsg>
                    <severity>Minor</severity>
                </event>
                """;

        Event event = xmlMapper.readValue(xml, Event.class);

        assertThat(event.getUei()).isEqualTo("uei.opennms.org/test/noNamespace");
        assertThat(event.getEventLabel()).isEqualTo("No Namespace Event");
        assertThat(event.getSeverity()).isEqualTo("Minor");
        assertThat(event.getLogmsg().getDest()).isEqualTo(LogDestType.LOGONLY);
    }

    @Test
    void deserializesAlarmData() throws Exception {
        String xml = """
                <event xmlns="http://xmlns.opennms.org/xsd/eventconf">
                    <uei>uei.opennms.org/test/alarmEvent</uei>
                    <event-label>Alarm Test Event</event-label>
                    <descr>Event with alarm data</descr>
                    <logmsg dest="logndisplay">Alarm log message</logmsg>
                    <severity>Major</severity>
                    <alarm-data reduction-key="%uei%:%dpname%:%nodeid%" alarm-type="1" auto-clean="false"/>
                </event>
                """;

        Event event = xmlMapper.readValue(xml, Event.class);

        assertThat(event.getUei()).isEqualTo("uei.opennms.org/test/alarmEvent");
        assertThat(event.getSeverity()).isEqualTo("Major");
        assertThat(event.getAlarmData()).isNotNull();
        assertThat(event.getAlarmData().getReductionKey()).isEqualTo("%uei%:%dpname%:%nodeid%");
        assertThat(event.getAlarmData().getAlarmType()).isEqualTo(1);
        assertThat(event.getAlarmData().getAutoClean()).isFalse();
    }

    @Test
    void deserializesAlarmDataWithClearKey() throws Exception {
        String xml = """
                <event xmlns="http://xmlns.opennms.org/xsd/eventconf">
                    <uei>uei.opennms.org/test/resolveEvent</uei>
                    <event-label>Resolution Event</event-label>
                    <descr>Resolution event with clear key</descr>
                    <logmsg dest="logndisplay">Resolved</logmsg>
                    <severity>Normal</severity>
                    <alarm-data reduction-key="%uei%:%dpname%:%nodeid%"
                                clear-key="uei.opennms.org/test/alarmEvent:%dpname%:%nodeid%"
                                alarm-type="2"/>
                </event>
                """;

        Event event = xmlMapper.readValue(xml, Event.class);

        assertThat(event.getAlarmData()).isNotNull();
        assertThat(event.getAlarmData().getAlarmType()).isEqualTo(2);
        assertThat(event.getAlarmData().getClearKey())
                .isEqualTo("uei.opennms.org/test/alarmEvent:%dpname%:%nodeid%");
    }

    @Test
    void deserializesEventWithMask() throws Exception {
        String xml = """
                <event xmlns="http://xmlns.opennms.org/xsd/eventconf">
                    <mask>
                        <maskelement>
                            <mename>id</mename>
                            <mevalue>.1.3.6.1.4.1.9</mevalue>
                        </maskelement>
                        <maskelement>
                            <mename>generic</mename>
                            <mevalue>6</mevalue>
                        </maskelement>
                        <maskelement>
                            <mename>specific</mename>
                            <mevalue>1</mevalue>
                        </maskelement>
                    </mask>
                    <uei>uei.opennms.org/test/maskedEvent</uei>
                    <event-label>Masked Event</event-label>
                    <descr>Event with mask elements</descr>
                    <logmsg dest="logndisplay">Masked event</logmsg>
                    <severity>Warning</severity>
                </event>
                """;

        Event event = xmlMapper.readValue(xml, Event.class);

        assertThat(event.getUei()).isEqualTo("uei.opennms.org/test/maskedEvent");
        assertThat(event.getMask()).isNotNull();
        assertThat(event.getMask().getMaskelements()).isNotEmpty();
    }

    @Test
    void ignoresUnknownElements() throws Exception {
        String xml = """
                <event xmlns="http://xmlns.opennms.org/xsd/eventconf">
                    <uei>uei.opennms.org/test/unknownFields</uei>
                    <event-label>Unknown Fields Event</event-label>
                    <descr>Event with unknown elements</descr>
                    <logmsg dest="logndisplay">Test</logmsg>
                    <severity>Indeterminate</severity>
                    <totally-unknown-element>should be ignored</totally-unknown-element>
                </event>
                """;

        Event event = xmlMapper.readValue(xml, Event.class);

        assertThat(event.getUei()).isEqualTo("uei.opennms.org/test/unknownFields");
        assertThat(event.getSeverity()).isEqualTo("Indeterminate");
    }
}
