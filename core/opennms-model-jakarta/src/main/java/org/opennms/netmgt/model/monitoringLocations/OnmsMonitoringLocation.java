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
