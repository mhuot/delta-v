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

/**
 * Classloading stub for the legacy OnmsCriteria type.
 *
 * The {@link org.opennms.netmgt.dao.api.OnmsDao} interface declares deprecated
 * default methods whose signatures reference this class. Those defaults throw
 * {@link UnsupportedOperationException} and are never called in Delta-V — but
 * the JVM must still resolve the parameter type when loading the interface.
 *
 * This empty stub satisfies that resolution without pulling in the full
 * opennms-model JAR (which carries javax.persistence entities that conflict
 * with the jakarta.persistence entities in this module).
 */
public class OnmsCriteria {
}
