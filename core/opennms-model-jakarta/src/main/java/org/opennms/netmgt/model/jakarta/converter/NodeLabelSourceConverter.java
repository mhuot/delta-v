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

import org.opennms.netmgt.model.OnmsNode.NodeLabelSource;

/**
 * JPA {@link AttributeConverter} for {@link NodeLabelSource} ↔ CHAR(1).
 *
 * <p>Replaces the Hibernate 3.6 {@code NodeLabelSourceUserType} for Spring Boot 4 / Hibernate 7.
 * Uses {@link NodeLabelSource#value()} to write and matches by character to read.</p>
 *
 * <p>Valid DB values: {@code 'U'} (USER), {@code 'N'} (NETBIOS), {@code 'H'} (HOSTNAME),
 * {@code 'S'} (SYSNAME), {@code 'A'} (ADDRESS), {@code ' '} (UNKNOWN).</p>
 *
 * <p>Usage: {@code @Convert(converter = NodeLabelSourceConverter.class)}</p>
 */
@Converter(autoApply = true)
public class NodeLabelSourceConverter implements AttributeConverter<NodeLabelSource, String> {

    @Override
    public String convertToDatabaseColumn(final NodeLabelSource nodeLabelSource) {
        return nodeLabelSource == null ? null : String.valueOf(nodeLabelSource.value());
    }

    @Override
    public NodeLabelSource convertToEntityAttribute(final String dbValue) {
        if (dbValue == null) {
            return null;
        }
        char c = dbValue.charAt(0);
        for (NodeLabelSource source : NodeLabelSource.values()) {
            if (source.value() == c) {
                return source;
            }
        }
        throw new IllegalArgumentException("Invalid NodeLabelSource value: '" + dbValue + "'");
    }
}
