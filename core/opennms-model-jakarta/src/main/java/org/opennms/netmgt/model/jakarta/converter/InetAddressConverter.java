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

import java.net.InetAddress;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import org.opennms.core.utils.InetAddressUtils;

/**
 * JPA {@link AttributeConverter} for {@link InetAddress} ↔ VARCHAR.
 *
 * <p>Replaces the Hibernate 3.6 {@code InetAddressUserType} for Spring Boot 4 / Hibernate 7.
 * Uses {@link InetAddressUtils#str(InetAddress)} to produce a normalized string representation
 * and {@link InetAddressUtils#addr(String)} to parse it back.</p>
 *
 * <p>Usage: {@code @Convert(converter = InetAddressConverter.class)}</p>
 */
@Converter(autoApply = true)
public class InetAddressConverter implements AttributeConverter<InetAddress, String> {

    @Override
    public String convertToDatabaseColumn(final InetAddress address) {
        return address == null ? null : InetAddressUtils.str(address);
    }

    @Override
    public InetAddress convertToEntityAttribute(final String dbValue) {
        return dbValue == null ? null : InetAddressUtils.addr(dbValue);
    }
}
