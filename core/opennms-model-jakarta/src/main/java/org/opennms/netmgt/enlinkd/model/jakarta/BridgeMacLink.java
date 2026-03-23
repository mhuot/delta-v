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
import org.opennms.netmgt.enlinkd.model.BridgeMacLink.BridgeMacLinkType;
import org.opennms.netmgt.enlinkd.model.jakarta.converter.BridgeMacLinkTypeConverter;
import org.opennms.netmgt.model.FilterManager;
import org.opennms.netmgt.model.OnmsNode;

@Entity
@Table(name = "bridgeMacLink")
@Filter(name = FilterManager.AUTH_FILTER_NAME, condition = "exists (select distinct x.nodeid from node x join category_node cn on x.nodeid = cn.nodeid join category_group cg on cn.categoryId = cg.categoryId where x.nodeid = nodeid and cg.groupId in (:userGroups))")
public class BridgeMacLink implements Serializable {

    private static final long serialVersionUID = 8100699538135896806L;

    private Integer m_id;
    private OnmsNode m_node;
    private Integer m_bridgePort;
    private Integer m_bridgePortIfIndex;
    private String m_bridgePortIfName;
    private String m_macAddress;
    private Integer m_vlan;
    private BridgeMacLinkType m_linkType;
    private Date m_bridgeMacLinkCreateTime = new Date();
    private Date m_bridgeMacLinkLastPollTime;

    public BridgeMacLink() {
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

    @Column(name = "bridgePort", nullable = false)
    public Integer getBridgePort() {
        return m_bridgePort;
    }

    public void setBridgePort(Integer bridgePort) {
        m_bridgePort = bridgePort;
    }

    @Column(name = "bridgePortIfIndex")
    public Integer getBridgePortIfIndex() {
        return m_bridgePortIfIndex;
    }

    public void setBridgePortIfIndex(Integer bridgePortIfIndex) {
        m_bridgePortIfIndex = bridgePortIfIndex;
    }

    @Column(name = "bridgePortIfName", length = 32)
    public String getBridgePortIfName() {
        return m_bridgePortIfName;
    }

    public void setBridgePortIfName(String bridgePortIfName) {
        m_bridgePortIfName = bridgePortIfName;
    }

    @Column(name = "vlan")
    public Integer getVlan() {
        return m_vlan;
    }

    public void setVlan(Integer vlan) {
        m_vlan = vlan;
    }

    @Column(name = "macAddress", length = 12, nullable = false)
    public String getMacAddress() {
        return m_macAddress;
    }

    public void setMacAddress(String macAddress) {
        m_macAddress = macAddress;
    }

    @Column(name = "linkType", nullable = false)
    @Convert(converter = BridgeMacLinkTypeConverter.class)
    public BridgeMacLinkType getLinkType() {
        return m_linkType;
    }

    public void setLinkType(BridgeMacLinkType linkType) {
        m_linkType = linkType;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "bridgeMacLinkCreateTime", nullable = false)
    public Date getBridgeMacLinkCreateTime() {
        return m_bridgeMacLinkCreateTime;
    }

    public void setBridgeMacLinkCreateTime(Date bridgeMacLinkCreateTime) {
        m_bridgeMacLinkCreateTime = bridgeMacLinkCreateTime;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "bridgeMacLinkLastPollTime", nullable = false)
    public Date getBridgeMacLinkLastPollTime() {
        return m_bridgeMacLinkLastPollTime;
    }

    public void setBridgeMacLinkLastPollTime(Date bridgeMacLinkLastPollTime) {
        m_bridgeMacLinkLastPollTime = bridgeMacLinkLastPollTime;
    }
}
