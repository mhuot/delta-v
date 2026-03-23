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
