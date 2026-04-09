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
package org.deltav.netmgt.bsm.rest.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EdgeDto {
    private Long id;
    private String type;
    private MapFunctionDto mapFunction;
    private int weight = 1;
    private Long childId;
    private Integer ipServiceId;
    private String reductionKey;
    private Integer applicationId;
    private String friendlyName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public MapFunctionDto getMapFunction() { return mapFunction; }
    public void setMapFunction(MapFunctionDto mapFunction) { this.mapFunction = mapFunction; }
    public int getWeight() { return weight; }
    public void setWeight(int weight) { this.weight = weight; }
    public Long getChildId() { return childId; }
    public void setChildId(Long childId) { this.childId = childId; }
    public Integer getIpServiceId() { return ipServiceId; }
    public void setIpServiceId(Integer ipServiceId) { this.ipServiceId = ipServiceId; }
    public String getReductionKey() { return reductionKey; }
    public void setReductionKey(String reductionKey) { this.reductionKey = reductionKey; }
    public Integer getApplicationId() { return applicationId; }
    public void setApplicationId(Integer applicationId) { this.applicationId = applicationId; }
    public String getFriendlyName() { return friendlyName; }
    public void setFriendlyName(String friendlyName) { this.friendlyName = friendlyName; }
}
