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
import org.opennms.netmgt.model.OnmsNode.NodeType;

class NodeTypeConverterTest {

    private final NodeTypeConverter converter = new NodeTypeConverter();

    @Test
    void convertToDatabaseColumn_active() {
        assertThat(converter.convertToDatabaseColumn(NodeType.ACTIVE)).isEqualTo("A");
    }

    @Test
    void convertToDatabaseColumn_deleted() {
        assertThat(converter.convertToDatabaseColumn(NodeType.DELETED)).isEqualTo("D");
    }

    @Test
    void convertToDatabaseColumn_unknown() {
        assertThat(converter.convertToDatabaseColumn(NodeType.UNKNOWN)).isEqualTo(" ");
    }

    @Test
    void convertToDatabaseColumn_null() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_A() {
        assertThat(converter.convertToEntityAttribute("A")).isEqualTo(NodeType.ACTIVE);
    }

    @Test
    void convertToEntityAttribute_D() {
        assertThat(converter.convertToEntityAttribute("D")).isEqualTo(NodeType.DELETED);
    }

    @Test
    void convertToEntityAttribute_space() {
        assertThat(converter.convertToEntityAttribute(" ")).isEqualTo(NodeType.UNKNOWN);
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
        for (NodeType type : NodeType.values()) {
            String dbValue = converter.convertToDatabaseColumn(type);
            assertThat(dbValue).isNotNull().hasSize(1);
            NodeType result = converter.convertToEntityAttribute(dbValue);
            assertThat(result).isEqualTo(type);
        }
    }

    @Test
    void dbValues_matchCharacters() {
        assertThat(converter.convertToDatabaseColumn(NodeType.ACTIVE)).isEqualTo(String.valueOf(NodeType.ACTIVE.value()));
        assertThat(converter.convertToDatabaseColumn(NodeType.DELETED)).isEqualTo(String.valueOf(NodeType.DELETED.value()));
        assertThat(converter.convertToDatabaseColumn(NodeType.UNKNOWN)).isEqualTo(String.valueOf(NodeType.UNKNOWN.value()));
    }
}
