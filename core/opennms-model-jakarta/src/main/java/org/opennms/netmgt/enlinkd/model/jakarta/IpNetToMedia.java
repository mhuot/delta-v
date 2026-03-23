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
import java.net.InetAddress;
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
import jakarta.persistence.Transient;

import org.hibernate.annotations.Filter;
import org.opennms.netmgt.enlinkd.model.IpNetToMedia.IpNetToMediaType;
import org.opennms.netmgt.model.FilterManager;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.jakarta.converter.InetAddressConverter;

@Entity
@Table(name = "ipNetToMedia")
@Filter(name = FilterManager.AUTH_FILTER_NAME, condition = "exists (select distinct x.nodeid from node x join category_node cn on x.nodeid = cn.nodeid join category_group cg on cn.categoryId = cg.categoryId where x.nodeid = nodeid and cg.groupId in (:userGroups))")
public class IpNetToMedia implements Serializable {

    private static final long serialVersionUID = 7750043250236397014L;

    private Integer m_id;
    private OnmsNode m_node;
    private Integer m_ifIndex;
    private String m_port;
    private InetAddress m_netAddress;
    private String m_physAddress;
    private IpNetToMediaType m_ipNetToMediaType;
    private OnmsNode m_sourceNode;
    private Integer m_sourceIfIndex;
    private Date m_createTime = new Date();
    private Date m_lastPollTime;

    public IpNetToMedia() {
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

    @Column(name = "netAddress", nullable = false)
    @Convert(converter = InetAddressConverter.class)
    public InetAddress getNetAddress() {
        return m_netAddress;
    }

    public void setNetAddress(InetAddress netAddress) {
        m_netAddress = netAddress;
    }

    @Column(name = "physAddress", length = 32, nullable = false)
    public String getPhysAddress() {
        return m_physAddress;
    }

    public void setPhysAddress(String physAddr) {
        m_physAddress = physAddr;
    }

    @Transient
    public IpNetToMediaType getIpNetToMediaType() {
        return m_ipNetToMediaType;
    }

    public void setIpNetToMediaType(IpNetToMediaType ipNetToMediaType) {
        m_ipNetToMediaType = ipNetToMediaType;
    }

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sourceNodeId", nullable = false)
    public OnmsNode getSourceNode() {
        return m_sourceNode;
    }

    public void setSourceNode(OnmsNode sourceNode) {
        m_sourceNode = sourceNode;
    }

    @Column(name = "sourceIfIndex", nullable = false)
    public Integer getSourceIfIndex() {
        return m_sourceIfIndex;
    }

    public void setSourceIfIndex(Integer sourceIfIndex) {
        m_sourceIfIndex = sourceIfIndex;
    }

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "nodeId")
    public OnmsNode getNode() {
        return m_node;
    }

    public void setNode(OnmsNode node) {
        m_node = node;
    }

    @Column(name = "ifIndex")
    public Integer getIfIndex() {
        return m_ifIndex;
    }

    public void setIfIndex(Integer ifIndex) {
        m_ifIndex = ifIndex;
    }

    public String getPort() {
        return m_port;
    }

    public void setPort(String port) {
        m_port = port;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "createTime", nullable = false)
    public Date getCreateTime() {
        return m_createTime;
    }

    public void setCreateTime(Date createTime) {
        m_createTime = createTime;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "lastPollTime", nullable = false)
    public Date getLastPollTime() {
        return m_lastPollTime;
    }

    public void setLastPollTime(Date lastPollTime) {
        m_lastPollTime = lastPollTime;
    }
}
