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
@Converter
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
