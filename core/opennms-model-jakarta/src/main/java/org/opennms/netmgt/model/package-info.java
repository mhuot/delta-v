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
/**
 * Jakarta Persistence entity model for OpenNMS.
 *
 * <p>This package-info defines the Hibernate 7 compatible {@code @FilterDef}
 * for the authorization filter used by entity classes in this package.
 * It replaces the legacy package-info.java from opennms-model which uses
 * Hibernate 3.x {@code @ParamDef(type="string")} syntax incompatible
 * with Hibernate 7's {@code @ParamDef(type=String.class)}.</p>
 */
@FilterDef(
    name = FilterManager.AUTH_FILTER_NAME,
    parameters = @ParamDef(name = "userGroups", type = String.class)
)
package org.opennms.netmgt.model;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
