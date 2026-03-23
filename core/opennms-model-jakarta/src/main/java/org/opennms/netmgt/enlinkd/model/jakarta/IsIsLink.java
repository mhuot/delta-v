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
import org.opennms.netmgt.enlinkd.model.IsIsElement.IsisAdminState;
import org.opennms.netmgt.enlinkd.model.IsIsLink.IsisISAdjNeighSysType;
import org.opennms.netmgt.enlinkd.model.IsIsLink.IsisISAdjState;
import org.opennms.netmgt.enlinkd.model.jakarta.converter.IsisAdminStateConverter;
import org.opennms.netmgt.enlinkd.model.jakarta.converter.IsisISAdjNeighSysTypeConverter;
import org.opennms.netmgt.enlinkd.model.jakarta.converter.IsisISAdjStateConverter;
import org.opennms.netmgt.model.FilterManager;
import org.opennms.netmgt.model.OnmsNode;

@Entity
@Table(name = "isisLink")
@Filter(name = FilterManager.AUTH_FILTER_NAME, condition = "exists (select distinct x.nodeid from node x join category_node cn on x.nodeid = cn.nodeid join category_group cg on cn.categoryId = cg.categoryId where x.nodeid = nodeid and cg.groupId in (:userGroups))")
public class IsIsLink implements Serializable {

    private static final long serialVersionUID = 3813247749765614567L;

    private Integer m_id;
    private OnmsNode m_node;
    private Integer m_isisCircIndex;
    private Integer m_isisISAdjIndex;
    private Integer m_isisCircIfIndex;
    private IsisAdminState m_isisCircAdminState;
    private IsisISAdjState m_isisISAdjState;
    private String m_isisISAdjNeighSNPAAddress;
    private IsisISAdjNeighSysType m_isisISAdjNeighSysType;
    private String m_isisISAdjNeighSysID;
    private Integer m_isisISAdjNbrExtendedCircID;
    private Date m_isisLinkCreateTime = new Date();
    private Date m_isisLinkLastPollTime;

    public IsIsLink() {
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

    @Column(name = "isisCircIndex", nullable = false)
    public Integer getIsisCircIndex() {
        return m_isisCircIndex;
    }

    public void setIsisCircIndex(Integer isisCircIndex) {
        m_isisCircIndex = isisCircIndex;
    }

    @Column(name = "isisISAdjIndex", nullable = false)
    public Integer getIsisISAdjIndex() {
        return m_isisISAdjIndex;
    }

    public void setIsisISAdjIndex(Integer isisISAdjIndex) {
        m_isisISAdjIndex = isisISAdjIndex;
    }

    @Column(name = "isisCircIfIndex")
    public Integer getIsisCircIfIndex() {
        return m_isisCircIfIndex;
    }

    public void setIsisCircIfIndex(Integer isisCircIfIndex) {
        m_isisCircIfIndex = isisCircIfIndex;
    }

    @Column(name = "isisCircAdminState")
    @Convert(converter = IsisAdminStateConverter.class)
    public IsisAdminState getIsisCircAdminState() {
        return m_isisCircAdminState;
    }

    public void setIsisCircAdminState(IsisAdminState isisCircAdminState) {
        m_isisCircAdminState = isisCircAdminState;
    }

    @Column(name = "isisISAdjState", nullable = false)
    @Convert(converter = IsisISAdjStateConverter.class)
    public IsisISAdjState getIsisISAdjState() {
        return m_isisISAdjState;
    }

    public void setIsisISAdjState(IsisISAdjState isisISAdjState) {
        m_isisISAdjState = isisISAdjState;
    }

    @Column(name = "isisISAdjNeighSNPAAddress", length = 80, nullable = false)
    public String getIsisISAdjNeighSNPAAddress() {
        return m_isisISAdjNeighSNPAAddress;
    }

    public void setIsisISAdjNeighSNPAAddress(String isisISAdjNeighSNPAAddress) {
        m_isisISAdjNeighSNPAAddress = isisISAdjNeighSNPAAddress;
    }

    @Column(name = "isisISAdjNeighSysType", nullable = false)
    @Convert(converter = IsisISAdjNeighSysTypeConverter.class)
    public IsisISAdjNeighSysType getIsisISAdjNeighSysType() {
        return m_isisISAdjNeighSysType;
    }

    public void setIsisISAdjNeighSysType(IsisISAdjNeighSysType isisISAdjNeighSysType) {
        m_isisISAdjNeighSysType = isisISAdjNeighSysType;
    }

    @Column(name = "isisISAdjNeighSysID", length = 32, nullable = false)
    public String getIsisISAdjNeighSysID() {
        return m_isisISAdjNeighSysID;
    }

    public void setIsisISAdjNeighSysID(String isisISAdjNeighSysID) {
        m_isisISAdjNeighSysID = isisISAdjNeighSysID;
    }

    @Column(name = "isisISAdjNbrExtendedCircID", nullable = false)
    public Integer getIsisISAdjNbrExtendedCircID() {
        return m_isisISAdjNbrExtendedCircID;
    }

    public void setIsisISAdjNbrExtendedCircID(Integer isisISAdjNbrExtendedCircID) {
        m_isisISAdjNbrExtendedCircID = isisISAdjNbrExtendedCircID;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "isisLinkCreateTime", nullable = false)
    public Date getIsisLinkCreateTime() {
        return m_isisLinkCreateTime;
    }

    public void setIsisLinkCreateTime(Date isisLinkCreateTime) {
        m_isisLinkCreateTime = isisLinkCreateTime;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "isisLinkLastPollTime", nullable = false)
    public Date getIsisLinkLastPollTime() {
        return m_isisLinkLastPollTime;
    }

    public void setIsisLinkLastPollTime(Date isisLinkLastPollTime) {
        m_isisLinkLastPollTime = isisLinkLastPollTime;
    }
}
