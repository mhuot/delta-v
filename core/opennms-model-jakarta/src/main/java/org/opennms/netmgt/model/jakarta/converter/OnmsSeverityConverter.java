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

import org.opennms.netmgt.model.OnmsSeverity;

/**
 * JPA {@link AttributeConverter} for {@link OnmsSeverity} ↔ INTEGER.
 *
 * <p>Replaces the Hibernate 3.6 {@code OnmsSeverityUserType} for Spring Boot 4 / Hibernate 7.
 * Persists the severity's integer ID. A {@code null} Java value is persisted as {@code 1}
 * (INDETERMINATE) to match legacy behaviour; a {@code null} DB column is returned as {@code null}.</p>
 *
 * <p>Usage: {@code @Convert(converter = OnmsSeverityConverter.class)}</p>
 */
@Converter
public class OnmsSeverityConverter implements AttributeConverter<OnmsSeverity, Integer> {

    private static final int DEFAULT_SEVERITY_ID = OnmsSeverity.INDETERMINATE.getId();

    @Override
    public Integer convertToDatabaseColumn(final OnmsSeverity severity) {
        return severity == null ? DEFAULT_SEVERITY_ID : severity.getId();
    }

    @Override
    public OnmsSeverity convertToEntityAttribute(final Integer dbValue) {
        return dbValue == null ? null : OnmsSeverity.get(dbValue);
    }
}
