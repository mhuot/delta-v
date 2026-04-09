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
package org.opennms.netmgt.enlinkd.model.converter;

import jakarta.persistence.AttributeConverter;
import java.util.function.Function;

/**
 * Abstract base for JPA {@link AttributeConverter} implementations that map
 * Enlinkd enum types to their integer database column representation.
 *
 * <p>Each Enlinkd enum follows the same contract: {@code getValue()} returns the
 * database integer and a static factory method reconstructs the enum from that
 * integer.  Concrete subclasses pass method references for both directions.</p>
 *
 * @param <E> the enum type
 */
public abstract class IntegerEnumConverter<E> implements AttributeConverter<E, Integer> {

    private final Function<E, Integer> toDb;
    private final Function<Integer, E> fromDb;

    protected IntegerEnumConverter(Function<E, Integer> toDb, Function<Integer, E> fromDb) {
        this.toDb = toDb;
        this.fromDb = fromDb;
    }

    @Override
    public Integer convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : toDb.apply(attribute);
    }

    @Override
    public E convertToEntityAttribute(Integer dbValue) {
        return dbValue == null ? null : fromDb.apply(dbValue);
    }
}
