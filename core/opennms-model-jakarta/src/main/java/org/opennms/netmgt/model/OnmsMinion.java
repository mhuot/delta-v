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

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * <p>The OnmsMinion represents a Minion node which has reported to OpenNMS.</p>
 */
@Entity
@DiscriminatorValue(OnmsMonitoringSystem.TYPE_MINION)
public class OnmsMinion extends OnmsMonitoringSystem {

    private static final long serialVersionUID = 7512728871301272703L;

    private String m_status;

    private String m_version;

    public OnmsMinion() {
    }

    public OnmsMinion(final String id, final String location, final String status, final Date lastUpdated) {
        super(id, location);
        setStatus(status);
        setLastUpdated(lastUpdated);
    }

    @Column(name="status")
    public String getStatus() {
        return m_status;
    }

    public void setStatus(final String status) {
        m_status = status;
    }

    @Column(name="version")
    public String getVersion() {
        return m_version;
    }

    public void setVersion(final String version) {
        m_version = version;
    }

    @Override
    public String toString() {
        return "OnmsMinion [id=" + getId() + ", location=" + getLocation() + ", status=" + m_status + ", version=" + getVersion() + ", lastUpdated=" + getLastUpdated() + ", properties=" + getProperties() + "]";
    }
}
