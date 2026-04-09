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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.opennms.netmgt.model.OnmsNode.NodeLabelSource;

class NodeLabelSourceConverterTest {

    private final NodeLabelSourceConverter converter = new NodeLabelSourceConverter();

    @Test
    void convertToDatabaseColumn_user() {
        assertThat(converter.convertToDatabaseColumn(NodeLabelSource.USER)).isEqualTo("U");
    }

    @Test
    void convertToDatabaseColumn_netbios() {
        assertThat(converter.convertToDatabaseColumn(NodeLabelSource.NETBIOS)).isEqualTo("N");
    }

    @Test
    void convertToDatabaseColumn_hostname() {
        assertThat(converter.convertToDatabaseColumn(NodeLabelSource.HOSTNAME)).isEqualTo("H");
    }

    @Test
    void convertToDatabaseColumn_sysname() {
        assertThat(converter.convertToDatabaseColumn(NodeLabelSource.SYSNAME)).isEqualTo("S");
    }

    @Test
    void convertToDatabaseColumn_address() {
        assertThat(converter.convertToDatabaseColumn(NodeLabelSource.ADDRESS)).isEqualTo("A");
    }

    @Test
    void convertToDatabaseColumn_unknown() {
        assertThat(converter.convertToDatabaseColumn(NodeLabelSource.UNKNOWN)).isEqualTo(" ");
    }

    @Test
    void convertToDatabaseColumn_null() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_U() {
        assertThat(converter.convertToEntityAttribute("U")).isEqualTo(NodeLabelSource.USER);
    }

    @Test
    void convertToEntityAttribute_N() {
        assertThat(converter.convertToEntityAttribute("N")).isEqualTo(NodeLabelSource.NETBIOS);
    }

    @Test
    void convertToEntityAttribute_H() {
        assertThat(converter.convertToEntityAttribute("H")).isEqualTo(NodeLabelSource.HOSTNAME);
    }

    @Test
    void convertToEntityAttribute_S() {
        assertThat(converter.convertToEntityAttribute("S")).isEqualTo(NodeLabelSource.SYSNAME);
    }

    @Test
    void convertToEntityAttribute_A() {
        assertThat(converter.convertToEntityAttribute("A")).isEqualTo(NodeLabelSource.ADDRESS);
    }

    @Test
    void convertToEntityAttribute_space() {
        assertThat(converter.convertToEntityAttribute(" ")).isEqualTo(NodeLabelSource.UNKNOWN);
    }

    @Test
    void convertToEntityAttribute_null() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_invalid_throws() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("X"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allValues_roundTrip() {
        for (NodeLabelSource source : NodeLabelSource.values()) {
            String dbValue = converter.convertToDatabaseColumn(source);
            assertThat(dbValue).isNotNull().hasSize(1);
            NodeLabelSource result = converter.convertToEntityAttribute(dbValue);
            assertThat(result).isEqualTo(source);
        }
    }

    @Test
    void dbValues_matchCharacters() {
        for (NodeLabelSource source : NodeLabelSource.values()) {
            assertThat(converter.convertToDatabaseColumn(source))
                    .isEqualTo(String.valueOf(source.value()));
        }
    }
}
