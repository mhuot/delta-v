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
@Converter(autoApply = true)
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
