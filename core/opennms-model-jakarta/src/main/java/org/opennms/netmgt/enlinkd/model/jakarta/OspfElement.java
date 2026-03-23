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
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import org.hibernate.annotations.Filter;
import org.opennms.netmgt.enlinkd.model.OspfElement.Status;
import org.opennms.netmgt.enlinkd.model.OspfElement.TruthValue;
import org.opennms.netmgt.enlinkd.model.jakarta.converter.StatusConverter;
import org.opennms.netmgt.enlinkd.model.jakarta.converter.TruthValueConverter;
import org.opennms.netmgt.model.FilterManager;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.jakarta.converter.InetAddressConverter;

@Entity
@Table(name = "ospfElement")
@Filter(name = FilterManager.AUTH_FILTER_NAME, condition = "exists (select distinct x.nodeid from node x join category_node cn on x.nodeid = cn.nodeid join category_group cg on cn.categoryId = cg.categoryId where x.nodeid = nodeid and cg.groupId in (:userGroups))")
public final class OspfElement implements Serializable {

    private static final long serialVersionUID = 7820026592390162672L;

    private Integer m_id;
    private InetAddress m_ospfRouterId;
    private Status m_ospfAdminStat;
    private Integer m_ospfVersionNumber;
    private TruthValue m_ospfBdrRtrStatus;
    private TruthValue m_ospfASBdrRtrStatus;
    private InetAddress m_ospfRouterIdNetmask;
    private Integer m_ospfRouterIdIfindex;
    private Date m_ospfNodeCreateTime = new Date();
    private Date m_ospfNodeLastPollTime;
    private OnmsNode m_node;

    public OspfElement() {
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

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nodeId")
    public OnmsNode getNode() {
        return m_node;
    }

    public void setNode(OnmsNode node) {
        m_node = node;
    }

    @Convert(converter = InetAddressConverter.class)
    @Column(name = "ospfRouterId", nullable = false)
    public InetAddress getOspfRouterId() {
        return m_ospfRouterId;
    }

    public void setOspfRouterId(InetAddress ospfRouterId) {
        m_ospfRouterId = ospfRouterId;
    }

    @Column(name = "ospfAdminStat", nullable = false)
    @Convert(converter = StatusConverter.class)
    public Status getOspfAdminStat() {
        return m_ospfAdminStat;
    }

    public void setOspfAdminStat(Status ospfAdminStat) {
        m_ospfAdminStat = ospfAdminStat;
    }

    @Column(name = "ospfVersionNumber", nullable = false)
    public Integer getOspfVersionNumber() {
        return m_ospfVersionNumber;
    }

    public void setOspfVersionNumber(Integer ospfVersionNumber) {
        m_ospfVersionNumber = ospfVersionNumber;
    }

    @Column(name = "ospfBdrRtrStatus", nullable = false)
    @Convert(converter = TruthValueConverter.class)
    public TruthValue getOspfBdrRtrStatus() {
        return m_ospfBdrRtrStatus;
    }

    public void setOspfBdrRtrStatus(TruthValue ospfBdrRtrStatus) {
        m_ospfBdrRtrStatus = ospfBdrRtrStatus;
    }

    @Column(name = "ospfASBdrRtrStatus", nullable = false)
    @Convert(converter = TruthValueConverter.class)
    public TruthValue getOspfASBdrRtrStatus() {
        return m_ospfASBdrRtrStatus;
    }

    public void setOspfASBdrRtrStatus(TruthValue ospfASBdrRtrStatus) {
        m_ospfASBdrRtrStatus = ospfASBdrRtrStatus;
    }

    @Convert(converter = InetAddressConverter.class)
    @Column(name = "ospfRouterIdNetmask", nullable = false)
    public InetAddress getOspfRouterIdNetmask() {
        return m_ospfRouterIdNetmask;
    }

    public void setOspfRouterIdNetmask(InetAddress ospfRouterIdNetmask) {
        m_ospfRouterIdNetmask = ospfRouterIdNetmask;
    }

    @Column(name = "ospfRouterIdIfindex", nullable = false)
    public Integer getOspfRouterIdIfindex() {
        return m_ospfRouterIdIfindex;
    }

    public void setOspfRouterIdIfindex(Integer ospfRouterIdIfindex) {
        m_ospfRouterIdIfindex = ospfRouterIdIfindex;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "ospfNodeCreateTime", nullable = false)
    public Date getOspfNodeCreateTime() {
        return m_ospfNodeCreateTime;
    }

    public void setOspfNodeCreateTime(Date ospfNodeCreateTime) {
        m_ospfNodeCreateTime = ospfNodeCreateTime;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "ospfNodeLastPollTime", nullable = false)
    public Date getOspfNodeLastPollTime() {
        return m_ospfNodeLastPollTime;
    }

    public void setOspfNodeLastPollTime(Date ospfNodeLastPollTime) {
        m_ospfNodeLastPollTime = ospfNodeLastPollTime;
    }
}
