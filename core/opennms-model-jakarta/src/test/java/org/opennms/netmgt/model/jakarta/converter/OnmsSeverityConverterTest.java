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

import org.junit.jupiter.api.Test;
import org.opennms.netmgt.model.OnmsSeverity;

class OnmsSeverityConverterTest {

    private final OnmsSeverityConverter converter = new OnmsSeverityConverter();

    @Test
    void convertToDatabaseColumn_null_returnsOne() {
        // Legacy UserType stores null as 1 (INDETERMINATE)
        assertThat(converter.convertToDatabaseColumn(null)).isEqualTo(1);
    }

    @Test
    void convertToDatabaseColumn_indeterminate() {
        assertThat(converter.convertToDatabaseColumn(OnmsSeverity.INDETERMINATE)).isEqualTo(1);
    }

    @Test
    void convertToDatabaseColumn_critical() {
        assertThat(converter.convertToDatabaseColumn(OnmsSeverity.CRITICAL)).isEqualTo(7);
    }

    @Test
    void convertToEntityAttribute_null() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_one() {
        assertThat(converter.convertToEntityAttribute(1)).isEqualTo(OnmsSeverity.INDETERMINATE);
    }

    @Test
    void convertToEntityAttribute_seven() {
        assertThat(converter.convertToEntityAttribute(7)).isEqualTo(OnmsSeverity.CRITICAL);
    }

    @Test
    void allValues_roundTrip() {
        for (OnmsSeverity severity : OnmsSeverity.values()) {
            Integer dbValue = converter.convertToDatabaseColumn(severity);
            assertThat(dbValue).isEqualTo(severity.getId());
            OnmsSeverity result = converter.convertToEntityAttribute(dbValue);
            assertThat(result).isEqualTo(severity);
        }
    }

    @Test
    void allValues_correctIds() {
        assertThat(converter.convertToDatabaseColumn(OnmsSeverity.INDETERMINATE)).isEqualTo(1);
        assertThat(converter.convertToDatabaseColumn(OnmsSeverity.CLEARED)).isEqualTo(2);
        assertThat(converter.convertToDatabaseColumn(OnmsSeverity.NORMAL)).isEqualTo(3);
        assertThat(converter.convertToDatabaseColumn(OnmsSeverity.WARNING)).isEqualTo(4);
        assertThat(converter.convertToDatabaseColumn(OnmsSeverity.MINOR)).isEqualTo(5);
        assertThat(converter.convertToDatabaseColumn(OnmsSeverity.MAJOR)).isEqualTo(6);
        assertThat(converter.convertToDatabaseColumn(OnmsSeverity.CRITICAL)).isEqualTo(7);
    }
}
