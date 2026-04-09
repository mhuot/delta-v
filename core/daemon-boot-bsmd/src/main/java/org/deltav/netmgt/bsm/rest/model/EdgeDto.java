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
