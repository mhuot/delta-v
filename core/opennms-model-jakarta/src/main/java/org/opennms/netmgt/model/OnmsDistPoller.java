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

import java.io.Serializable;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * Represents an OpenNMS Distributed Poller.
 */
@Entity
@DiscriminatorValue(OnmsMonitoringSystem.TYPE_OPENNMS)
public class OnmsDistPoller extends OnmsMonitoringSystem implements Serializable {

    private static final long serialVersionUID = -1094353783612066524L;

    /**
     * default constructor
     */
    public OnmsDistPoller() {}

    /**
     * minimal constructor
     *
     * @param id a {@link java.lang.String} object.
     */
    public OnmsDistPoller(String id) {
        // org.opennms.netmgt.dao.api.MonitoringLocationDao.DEFAULT_MONITORING_LOCATION_ID
        super(id, "Default");
    }
}
