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
package org.opennms.netmgt.model.outage;

import java.util.Date;

public class CurrentOutageDetails {
    private Integer m_outageId;
    private Integer m_monitoredServiceId;
    private String m_serviceName;
    private Date m_ifLostService;
    private Integer m_nodeId;
    private String m_foreignSource;
    private String m_foreignId;
    private String m_location;

    public CurrentOutageDetails() {}

    public CurrentOutageDetails(final Integer outageId, final Integer monitoredServiceId,
            final String serviceName, final Date ifLostService, final Integer nodeId,
            final String foreignSource, final String foreignId, final String location) {
        m_outageId = outageId;
        m_monitoredServiceId = monitoredServiceId;
        m_serviceName = serviceName;
        m_ifLostService = ifLostService;
        m_nodeId = nodeId;
        m_foreignSource = foreignSource;
        m_foreignId = foreignId;
        m_location = location;
    }

    public Integer getOutageId() { return m_outageId; }
    public Integer getMonitoredServiceId() { return m_monitoredServiceId; }
    public String getServiceName() { return m_serviceName; }
    public Date getIfLostService() { return m_ifLostService; }
    public Integer getNodeId() { return m_nodeId; }
    public String getForeignSource() { return m_foreignSource; }
    public String getForeignId() { return m_foreignId; }
    public String getLocation() { return m_location; }

    @Override
    public String toString() {
        return "CurrentOutageDetails [outageId=" + m_outageId
                + ", monitoredServiceId=" + m_monitoredServiceId
                + ", serviceName=" + m_serviceName
                + ", ifLostService=" + m_ifLostService
                + ", nodeId=" + m_nodeId
                + ", foreignSource=" + m_foreignSource
                + ", foreignId=" + m_foreignId
                + ", location=" + m_location + "]";
    }
}
