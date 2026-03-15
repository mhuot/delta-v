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
@Converter
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
