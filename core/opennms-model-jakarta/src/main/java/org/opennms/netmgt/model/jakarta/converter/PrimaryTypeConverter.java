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

import org.opennms.netmgt.model.PrimaryType;

/**
 * JPA {@link AttributeConverter} for {@link PrimaryType} ↔ CHAR(1).
 *
 * <p>Replaces the Hibernate 3.6 {@code PrimaryTypeUserType} for Spring Boot 4 / Hibernate 7.
 * Uses {@link PrimaryType#getCharCode()} to write and {@link PrimaryType#get(Object)} to read.</p>
 *
 * <p>Valid DB values: {@code 'P'} (PRIMARY), {@code 'S'} (SECONDARY), {@code 'N'} (NOT_ELIGIBLE).</p>
 *
 * <p>Usage: {@code @Convert(converter = PrimaryTypeConverter.class)}</p>
 */
@Converter
public class PrimaryTypeConverter implements AttributeConverter<PrimaryType, String> {

    @Override
    public String convertToDatabaseColumn(final PrimaryType primaryType) {
        return primaryType == null ? null : String.valueOf(primaryType.getCharCode());
    }

    @Override
    public PrimaryType convertToEntityAttribute(final String dbValue) {
        return dbValue == null ? null : PrimaryType.get(dbValue);
    }
}
