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

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import org.opennms.netmgt.model.OnmsNode.NodeType;

/**
 * JPA {@link AttributeConverter} for {@link NodeType} ↔ CHAR(1).
 *
 * <p>Replaces the Hibernate 3.6 {@code NodeTypeUserType} for Spring Boot 4 / Hibernate 7.
 * Uses {@link NodeType#value()} to write and matches by character to read.</p>
 *
 * <p>Valid DB values: {@code 'A'} (ACTIVE), {@code 'D'} (DELETED), {@code ' '} (UNKNOWN).</p>
 *
 * <p>Usage: {@code @Convert(converter = NodeTypeConverter.class)}</p>
 */
@Converter(autoApply = true)
public class NodeTypeConverter implements AttributeConverter<NodeType, String> {

    @Override
    public String convertToDatabaseColumn(final NodeType nodeType) {
        return nodeType == null ? null : String.valueOf(nodeType.value());
    }

    @Override
    public NodeType convertToEntityAttribute(final String dbValue) {
        if (dbValue == null) {
            return null;
        }
        char c = dbValue.charAt(0);
        for (NodeType nodeType : NodeType.values()) {
            if (nodeType.value() == c) {
                return nodeType;
            }
        }
        throw new IllegalArgumentException("Invalid NodeType value: '" + dbValue + "'");
    }
}
