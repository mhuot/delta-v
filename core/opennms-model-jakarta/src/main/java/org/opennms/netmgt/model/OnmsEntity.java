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
 * Abstract base for OpenNMS entity classes.
 */
public abstract class OnmsEntity {

    /**
     * Accept a visitor for traversing the entity hierarchy.
     * Used by provisiond's import pipeline during node provisioning.
     */
    public abstract void visit(EntityVisitor visitor);

    /**
     * Returns {@code true} when a new value differs from the existing one.
     * Convenience for update-detection in entity merge logic.
     */
    protected static boolean hasNewValue(final Object newVal, final Object existingVal) {
        return newVal != null && !newVal.equals(existingVal);
    }
}
