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
package org.opennms.netmgt.model;

import javax.xml.bind.annotation.adapters.XmlAdapter;

public class PrimaryTypeAdapter extends XmlAdapter<String, PrimaryType> {

    @Override
    public String marshal(final PrimaryType type) throws Exception {
        return type == null? null : type.getCode();
    }

    @Override
    public PrimaryType unmarshal(final String typeCode) throws Exception {
        return typeCode == null? null : PrimaryType.get(typeCode);
    }

}
