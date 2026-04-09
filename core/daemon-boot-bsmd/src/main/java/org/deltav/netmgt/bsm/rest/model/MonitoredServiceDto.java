/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
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
package org.deltav.netmgt.bsm.rest.model;

public class MonitoredServiceDto {
    private int id;
    private String nodeLabel;
    private String ipAddress;
    private String serviceName;

    public MonitoredServiceDto() {}

    public MonitoredServiceDto(int id, String nodeLabel, String ipAddress, String serviceName) {
        this.id = id;
        this.nodeLabel = nodeLabel;
        this.ipAddress = ipAddress;
        this.serviceName = serviceName;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getNodeLabel() { return nodeLabel; }
    public void setNodeLabel(String nodeLabel) { this.nodeLabel = nodeLabel; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
}
