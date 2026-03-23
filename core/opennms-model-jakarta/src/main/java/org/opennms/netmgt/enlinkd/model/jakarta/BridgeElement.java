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
package org.opennms.netmgt.enlinkd.model.jakarta;

import java.io.Serializable;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import org.hibernate.annotations.Filter;
import org.opennms.netmgt.enlinkd.model.BridgeElement.BridgeDot1dBaseType;
import org.opennms.netmgt.enlinkd.model.BridgeElement.BridgeDot1dStpProtocolSpecification;
import org.opennms.netmgt.enlinkd.model.jakarta.converter.BridgeDot1dBaseTypeConverter;
import org.opennms.netmgt.enlinkd.model.jakarta.converter.BridgeDot1dStpProtocolSpecificationConverter;
import org.opennms.netmgt.model.FilterManager;
import org.opennms.netmgt.model.OnmsNode;

@Entity
@Table(name = "bridgeElement")
@Filter(name = FilterManager.AUTH_FILTER_NAME, condition = "exists (select distinct x.nodeid from node x join category_node cn on x.nodeid = cn.nodeid join category_group cg on cn.categoryId = cg.categoryId where x.nodeid = nodeid and cg.groupId in (:userGroups))")
public class BridgeElement implements Serializable {

    private static final long serialVersionUID = -3137257592300016141L;

    private Integer m_id;
    private OnmsNode m_node;
    private String m_baseBridgeAddress;
    private Integer m_baseNumPorts;
    private BridgeDot1dBaseType m_baseType;
    private BridgeDot1dStpProtocolSpecification m_stpProtocolSpecification;
    private Integer m_stpPriority;
    private String m_stpDesignatedRoot;
    private Integer m_stpRootCost;
    private Integer m_stpRootPort;
    private Integer m_vlan;
    private String m_vlanname;
    private Date m_bridgeNodeCreateTime = new Date();
    private Date m_bridgeNodeLastPollTime;

    public BridgeElement() {
    }

    @Id
    @Column(nullable = false)
    @SequenceGenerator(name = "opennmsSequence", sequenceName = "opennmsNxtId", allocationSize = 1)
    @GeneratedValue(generator = "opennmsSequence")
    public Integer getId() {
        return m_id;
    }

    public void setId(Integer id) {
        m_id = id;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nodeId")
    public OnmsNode getNode() {
        return m_node;
    }

    public void setNode(OnmsNode node) {
        m_node = node;
    }

    @Column(name = "baseBridgeAddress", length = 12, nullable = false)
    public String getBaseBridgeAddress() {
        return m_baseBridgeAddress;
    }

    public void setBaseBridgeAddress(String baseBridgeAddress) {
        m_baseBridgeAddress = baseBridgeAddress;
    }

    @Column(name = "baseNumPorts", nullable = false)
    public Integer getBaseNumPorts() {
        return m_baseNumPorts;
    }

    public void setBaseNumPorts(Integer baseNumPorts) {
        m_baseNumPorts = baseNumPorts;
    }

    @Column(name = "baseType", nullable = false)
    @Convert(converter = BridgeDot1dBaseTypeConverter.class)
    public BridgeDot1dBaseType getBaseType() {
        return m_baseType;
    }

    public void setBaseType(BridgeDot1dBaseType baseType) {
        m_baseType = baseType;
    }

    @Column(name = "vlan")
    public Integer getVlan() {
        return m_vlan;
    }

    public void setVlan(Integer vlan) {
        m_vlan = vlan;
    }

    @Column(name = "vlanname", length = 64)
    public String getVlanname() {
        return m_vlanname;
    }

    public void setVlanname(String vlanname) {
        m_vlanname = vlanname;
    }

    @Column(name = "stpProtocolSpecification")
    @Convert(converter = BridgeDot1dStpProtocolSpecificationConverter.class)
    public BridgeDot1dStpProtocolSpecification getStpProtocolSpecification() {
        return m_stpProtocolSpecification;
    }

    public void setStpProtocolSpecification(BridgeDot1dStpProtocolSpecification stpProtocolSpecification) {
        m_stpProtocolSpecification = stpProtocolSpecification;
    }

    @Column(name = "stpPriority")
    public Integer getStpPriority() {
        return m_stpPriority;
    }

    public void setStpPriority(Integer stpPriority) {
        m_stpPriority = stpPriority;
    }

    @Column(name = "stpDesignatedRoot", length = 16)
    public String getStpDesignatedRoot() {
        return m_stpDesignatedRoot;
    }

    public void setStpDesignatedRoot(String stpDesignatedRoot) {
        m_stpDesignatedRoot = stpDesignatedRoot;
    }

    @Column(name = "stpRootCost")
    public Integer getStpRootCost() {
        return m_stpRootCost;
    }

    public void setStpRootCost(Integer stpRootCost) {
        m_stpRootCost = stpRootCost;
    }

    @Column(name = "stpRootPort")
    public Integer getStpRootPort() {
        return m_stpRootPort;
    }

    public void setStpRootPort(Integer stpRootPort) {
        m_stpRootPort = stpRootPort;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "bridgeNodeCreateTime", nullable = false)
    public Date getBridgeNodeCreateTime() {
        return m_bridgeNodeCreateTime;
    }

    public void setBridgeNodeCreateTime(Date bridgeNodeCreateTime) {
        m_bridgeNodeCreateTime = bridgeNodeCreateTime;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "bridgeNodeLastPollTime", nullable = false)
    public Date getBridgeNodeLastPollTime() {
        return m_bridgeNodeLastPollTime;
    }

    public void setBridgeNodeLastPollTime(Date bridgeNodeLastPollTime) {
        m_bridgeNodeLastPollTime = bridgeNodeLastPollTime;
    }
}
