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
package org.opennms.netmgt.model.monitoringLocations;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Jakarta Persistence version of OnmsMonitoringLocation.
 *
 * <p>Minimal entity mapping for the monitoringLocations table.
 * Only the columns required by foreign-key relationships from other
 * Jakarta entities (e.g., OnmsNode.location) are mapped here.</p>
 */
@Entity
@Table(name = "monitoringLocations")
public class OnmsMonitoringLocation implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String DEFAULT_MONITORING_LOCATION_ID = "Default";

    private String m_locationName;
    private String m_monitoringArea;
    private String m_geolocation;
    private Float m_longitude;
    private Float m_latitude;
    private Long m_priority;

    public OnmsMonitoringLocation() {}

    public OnmsMonitoringLocation(String locationName, String monitoringArea) {
        m_locationName = locationName;
        m_monitoringArea = monitoringArea;
    }

    @Id
    @Column(name = "id", nullable = false)
    public String getLocationName() {
        return m_locationName;
    }

    public void setLocationName(String locationName) {
        m_locationName = locationName;
    }

    @Column(name = "monitoringArea", nullable = false)
    public String getMonitoringArea() {
        return m_monitoringArea;
    }

    public void setMonitoringArea(String monitoringArea) {
        m_monitoringArea = monitoringArea;
    }

    @Column(name = "geolocation")
    public String getGeolocation() {
        return m_geolocation;
    }

    public void setGeolocation(String geolocation) {
        m_geolocation = geolocation;
    }

    @Column(name = "longitude")
    public Float getLongitude() {
        return m_longitude;
    }

    public void setLongitude(Float longitude) {
        m_longitude = longitude;
    }

    @Column(name = "latitude")
    public Float getLatitude() {
        return m_latitude;
    }

    public void setLatitude(Float latitude) {
        m_latitude = latitude;
    }

    @Column(name = "priority")
    public Long getPriority() {
        return m_priority;
    }

    public void setPriority(Long priority) {
        m_priority = priority;
    }

    @Override
    public String toString() {
        return "OnmsMonitoringLocation[" + m_locationName + "]";
    }
}
