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
package org.deltav.minion.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FlowProtocolTest {

    @Test
    void detectsNetflow5() {
        byte[] datagram = new byte[] { 0x00, 0x05, 0x00, 0x00 };
        assertThat(FlowProtocol.detect(datagram)).isEqualTo(FlowProtocol.NETFLOW_5);
    }

    @Test
    void detectsNetflow9() {
        byte[] datagram = new byte[] { 0x00, 0x09, 0x00, 0x00 };
        assertThat(FlowProtocol.detect(datagram)).isEqualTo(FlowProtocol.NETFLOW_9);
    }

    @Test
    void detectsIpfix() {
        byte[] datagram = new byte[] { 0x00, 0x0A, 0x00, 0x00 };
        assertThat(FlowProtocol.detect(datagram)).isEqualTo(FlowProtocol.IPFIX);
    }

    @Test
    void detectsSflowAsFourByteVersion() {
        byte[] datagram = new byte[] { 0x00, 0x00, 0x00, 0x05 };
        assertThat(FlowProtocol.detect(datagram)).isEqualTo(FlowProtocol.SFLOW);
    }

    @Test
    void returnsNullForUnknownProtocol() {
        byte[] datagram = new byte[] { 0x00, 0x01, 0x00, 0x00 };
        assertThat(FlowProtocol.detect(datagram)).isNull();
    }

    @Test
    void returnsNullForDatagramSmallerThanFourBytes() {
        assertThat(FlowProtocol.detect(new byte[] {})).isNull();
        assertThat(FlowProtocol.detect(new byte[] { 0x00 })).isNull();
        assertThat(FlowProtocol.detect(new byte[] { 0x00, 0x09 })).isNull();
        assertThat(FlowProtocol.detect(new byte[] { 0x00, 0x09, 0x00 })).isNull();
    }

    @Test
    void moduleIdHasExpectedFormat() {
        assertThat(FlowProtocol.NETFLOW_5.getSinkModuleId()).isEqualTo("Telemetry-Netflow-5");
        assertThat(FlowProtocol.NETFLOW_9.getSinkModuleId()).isEqualTo("Telemetry-Netflow-9");
        assertThat(FlowProtocol.IPFIX.getSinkModuleId()).isEqualTo("Telemetry-IPFIX");
        assertThat(FlowProtocol.SFLOW.getSinkModuleId()).isEqualTo("Telemetry-SFlow");
    }
}
