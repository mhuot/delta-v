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
