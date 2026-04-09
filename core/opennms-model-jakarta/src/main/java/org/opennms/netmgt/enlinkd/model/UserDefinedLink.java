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
package org.opennms.netmgt.enlinkd.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_defined_links")
public class UserDefinedLink {

    private Integer nodeIdA;
    private String componentLabelA;
    private Integer nodeIdZ;
    private String componentLabelZ;
    private String linkId;
    private String linkLabel;
    private String owner;
    private Integer dbId;

    @Column(name = "node_id_a", nullable = false)
    public Integer getNodeIdA() {
        return nodeIdA;
    }

    public void setNodeIdA(Integer nodeIdA) {
        this.nodeIdA = nodeIdA;
    }

    @Column(name = "component_label_a")
    public String getComponentLabelA() {
        return componentLabelA;
    }

    public void setComponentLabelA(String componentLabelA) {
        this.componentLabelA = componentLabelA;
    }

    @Column(name = "node_id_z", nullable = false)
    public Integer getNodeIdZ() {
        return nodeIdZ;
    }

    public void setNodeIdZ(Integer nodeIdZ) {
        this.nodeIdZ = nodeIdZ;
    }

    @Column(name = "component_label_z")
    public String getComponentLabelZ() {
        return componentLabelZ;
    }

    public void setComponentLabelZ(String componentLabelZ) {
        this.componentLabelZ = componentLabelZ;
    }

    @Column(name = "link_id", nullable = false)
    public String getLinkId() {
        return linkId;
    }

    public void setLinkId(String linkId) {
        this.linkId = linkId;
    }

    @Column(name = "link_label")
    public String getLinkLabel() {
        return linkLabel;
    }

    public void setLinkLabel(String linkLabel) {
        this.linkLabel = linkLabel;
    }

    @Column(name = "owner", nullable = false)
    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    @Id
    @Column(name = "id", nullable = false)
    @SequenceGenerator(name = "opennmsSequence", sequenceName = "opennmsNxtId", allocationSize = 1)
    @GeneratedValue(generator = "opennmsSequence")
    public Integer getDbId() {
        return dbId;
    }

    public void setDbId(Integer dbId) {
        this.dbId = dbId;
    }
}
