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
import org.opennms.netmgt.enlinkd.model.CdpLink.CiscoNetworkProtocolType;
import org.opennms.netmgt.enlinkd.model.jakarta.converter.CiscoNetworkProtocolTypeConverter;
import org.opennms.netmgt.model.FilterManager;
import org.opennms.netmgt.model.OnmsNode;

@Entity
@Table(name = "cdpLink")
@Filter(name = FilterManager.AUTH_FILTER_NAME, condition = "exists (select distinct x.nodeid from node x join category_node cn on x.nodeid = cn.nodeid join category_group cg on cn.categoryId = cg.categoryId where x.nodeid = nodeid and cg.groupId in (:userGroups))")
public class CdpLink implements Serializable {

    private static final long serialVersionUID = 3428640531131834328L;

    private Integer m_id;
    private OnmsNode m_node;
    private Integer m_cdpCacheIfIndex;
    private Integer m_cdpCacheDeviceIndex;
    private String m_cdpInterfaceName;
    private CiscoNetworkProtocolType m_cdpCacheAddressType;
    private String m_cdpCacheAddress;
    private String m_cdpCacheVersion;
    private String m_cdpCacheDeviceId;
    private String m_cdpCacheDevicePort;
    private String m_cdpCacheDevicePlatform;
    private Date m_cdpLinkCreateTime = new Date();
    private Date m_cdpLinkLastPollTime;

    public CdpLink() {
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

    @Column(name = "cdpCacheIfIndex", nullable = false)
    public Integer getCdpCacheIfIndex() {
        return m_cdpCacheIfIndex;
    }

    public void setCdpCacheIfIndex(Integer cdpCacheIfIndex) {
        m_cdpCacheIfIndex = cdpCacheIfIndex;
    }

    @Column(name = "cdpCacheDeviceIndex", nullable = false)
    public Integer getCdpCacheDeviceIndex() {
        return m_cdpCacheDeviceIndex;
    }

    public void setCdpCacheDeviceIndex(Integer cdpCacheDeviceIndex) {
        m_cdpCacheDeviceIndex = cdpCacheDeviceIndex;
    }

    @Column(name = "cdpInterfaceName", length = 96)
    public String getCdpInterfaceName() {
        return m_cdpInterfaceName;
    }

    public void setCdpInterfaceName(String cdpInterfaceName) {
        m_cdpInterfaceName = cdpInterfaceName;
    }

    @Column(name = "cdpCacheAddressType", nullable = false)
    @Convert(converter = CiscoNetworkProtocolTypeConverter.class)
    public CiscoNetworkProtocolType getCdpCacheAddressType() {
        return m_cdpCacheAddressType;
    }

    public void setCdpCacheAddressType(CiscoNetworkProtocolType cdpCacheAddressType) {
        m_cdpCacheAddressType = cdpCacheAddressType;
    }

    @Column(name = "cdpCacheAddress", length = 64, nullable = false)
    public String getCdpCacheAddress() {
        return m_cdpCacheAddress;
    }

    public void setCdpCacheAddress(String cdpCacheAddress) {
        m_cdpCacheAddress = cdpCacheAddress;
    }

    @Column(name = "cdpCacheVersion", length = 256, nullable = false)
    public String getCdpCacheVersion() {
        return m_cdpCacheVersion;
    }

    public void setCdpCacheVersion(String cdpCacheVersion) {
        m_cdpCacheVersion = cdpCacheVersion;
    }

    @Column(name = "cdpCacheDeviceId", length = 64, nullable = false)
    public String getCdpCacheDeviceId() {
        return m_cdpCacheDeviceId;
    }

    public void setCdpCacheDeviceId(String cdpCacheDeviceId) {
        m_cdpCacheDeviceId = cdpCacheDeviceId;
    }

    @Column(name = "cdpCacheDevicePort", length = 96, nullable = false)
    public String getCdpCacheDevicePort() {
        return m_cdpCacheDevicePort;
    }

    public void setCdpCacheDevicePort(String cdpCacheDevicePort) {
        m_cdpCacheDevicePort = cdpCacheDevicePort;
    }

    @Column(name = "cdpCacheDevicePlatform", length = 96, nullable = false)
    public String getCdpCacheDevicePlatform() {
        return m_cdpCacheDevicePlatform;
    }

    public void setCdpCacheDevicePlatform(String cdpCacheDevicePlatform) {
        m_cdpCacheDevicePlatform = cdpCacheDevicePlatform;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "cdpLinkCreateTime", nullable = false)
    public Date getCdpLinkCreateTime() {
        return m_cdpLinkCreateTime;
    }

    public void setCdpLinkCreateTime(Date cdpLinkCreateTime) {
        m_cdpLinkCreateTime = cdpLinkCreateTime;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "cdpLinkLastPollTime", nullable = false)
    public Date getCdpLinkLastPollTime() {
        return m_cdpLinkLastPollTime;
    }

    public void setCdpLinkLastPollTime(Date cdpLinkLastPollTime) {
        m_cdpLinkLastPollTime = cdpLinkLastPollTime;
    }
}
