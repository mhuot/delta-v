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
import org.opennms.netmgt.model.PrimaryType;

class PrimaryTypeConverterTest {

    private final PrimaryTypeConverter converter = new PrimaryTypeConverter();

    @Test
    void convertToDatabaseColumn_primary() {
        assertThat(converter.convertToDatabaseColumn(PrimaryType.PRIMARY)).isEqualTo("P");
    }

    @Test
    void convertToDatabaseColumn_secondary() {
        assertThat(converter.convertToDatabaseColumn(PrimaryType.SECONDARY)).isEqualTo("S");
    }

    @Test
    void convertToDatabaseColumn_notEligible() {
        assertThat(converter.convertToDatabaseColumn(PrimaryType.NOT_ELIGIBLE)).isEqualTo("N");
    }

    @Test
    void convertToDatabaseColumn_null() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_P() {
        PrimaryType result = converter.convertToEntityAttribute("P");
        assertThat(result).isEqualTo(PrimaryType.PRIMARY);
    }

    @Test
    void convertToEntityAttribute_S() {
        PrimaryType result = converter.convertToEntityAttribute("S");
        assertThat(result).isEqualTo(PrimaryType.SECONDARY);
    }

    @Test
    void convertToEntityAttribute_N() {
        PrimaryType result = converter.convertToEntityAttribute("N");
        assertThat(result).isEqualTo(PrimaryType.NOT_ELIGIBLE);
    }

    @Test
    void convertToEntityAttribute_null() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void roundTrip_allTypes() {
        for (PrimaryType type : PrimaryType.getAllTypes()) {
            String dbValue = converter.convertToDatabaseColumn(type);
            assertThat(dbValue).hasSize(1);
            PrimaryType result = converter.convertToEntityAttribute(dbValue);
            assertThat(result).isEqualTo(type);
        }
    }
}
