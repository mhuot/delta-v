/*
 * Copyright (C) 1999-2024 The OpenNMS Group, Inc.
 * Copyright (C) 2026 BeaconStrategists, Inc. (Modifications)
 *
 * This file is part of OpenNMS(R) / Delta-V.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
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
package org.opennms.netmgt.model.jakarta.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;

import org.junit.jupiter.api.Test;
import org.opennms.core.utils.InetAddressUtils;

class InetAddressConverterTest {

    private final InetAddressConverter converter = new InetAddressConverter();

    @Test
    void convertToDatabaseColumn_ipv4() throws Exception {
        InetAddress address = InetAddress.getByName("192.168.1.1");
        String result = converter.convertToDatabaseColumn(address);
        assertThat(result).isEqualTo("192.168.1.1");
    }

    @Test
    void convertToDatabaseColumn_ipv6() throws Exception {
        InetAddress address = InetAddress.getByName("::1");
        String result = converter.convertToDatabaseColumn(address);
        assertThat(result).isNotNull();
        assertThat(result).isNotEmpty();
    }

    @Test
    void convertToDatabaseColumn_null() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_ipv4() {
        InetAddress result = converter.convertToEntityAttribute("192.168.1.1");
        assertThat(result).isNotNull();
        assertThat(result.getHostAddress()).isEqualTo("192.168.1.1");
    }

    @Test
    void convertToEntityAttribute_null() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void roundTrip_ipv4() throws Exception {
        InetAddress original = InetAddress.getByName("10.0.0.1");
        String dbValue = converter.convertToDatabaseColumn(original);
        InetAddress result = converter.convertToEntityAttribute(dbValue);
        assertThat(result).isEqualTo(original);
    }

    @Test
    void roundTrip_ipv6() throws Exception {
        InetAddress original = InetAddressUtils.addr("::1");
        String dbValue = converter.convertToDatabaseColumn(original);
        InetAddress result = converter.convertToEntityAttribute(dbValue);
        assertThat(result).isEqualTo(original);
    }

    @Test
    void convertToDatabaseColumn_usesInetAddressUtilsStr() throws Exception {
        // InetAddressUtils.str() produces a normalized form — verify round-trip consistency
        InetAddress address = InetAddress.getByName("192.168.0.1");
        String dbValue = converter.convertToDatabaseColumn(address);
        assertThat(dbValue).isEqualTo(InetAddressUtils.str(address));
    }
}
