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
package org.opennms.netmgt.bsm.rest.model;

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
