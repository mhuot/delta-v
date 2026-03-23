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
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import org.hibernate.annotations.Filter;
import org.opennms.netmgt.enlinkd.model.IsIsElement.IsisAdminState;
import org.opennms.netmgt.enlinkd.model.jakarta.converter.IsisAdminStateConverter;
import org.opennms.netmgt.model.FilterManager;
import org.opennms.netmgt.model.OnmsNode;

@Entity
@Table(name = "isisElement")
@Filter(name = FilterManager.AUTH_FILTER_NAME, condition = "exists (select distinct x.nodeid from node x join category_node cn on x.nodeid = cn.nodeid join category_group cg on cn.categoryId = cg.categoryId where x.nodeid = nodeid and cg.groupId in (:userGroups))")
public final class IsIsElement implements Serializable {

    private static final long serialVersionUID = -3134355798509685991L;

    private Integer m_id;
    private String m_isisSysID;
    private IsisAdminState m_isisSysAdminState;
    private Date m_isisNodeCreateTime = new Date();
    private Date m_isisNodeLastPollTime;
    private OnmsNode m_node;

    public IsIsElement() {
    }

    @Id
    @Column(nullable = false)
    @SequenceGenerator(name = "opennmsSequence", sequenceName = "opennmsNxtId", allocationSize = 1)
    @GeneratedValue(generator = "opennmsSequence")
    public Integer getId() {
        return m_id;
    }

    public void setId(final Integer id) {
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

    @Column(name = "isisSysAdminState", nullable = false)
    @Convert(converter = IsisAdminStateConverter.class)
    public IsisAdminState getIsisSysAdminState() {
        return m_isisSysAdminState;
    }

    public void setIsisSysAdminState(IsisAdminState isisSysAdminState) {
        m_isisSysAdminState = isisSysAdminState;
    }

    @Column(name = "isisSysID", length = 32, nullable = false)
    public String getIsisSysID() {
        return m_isisSysID;
    }

    public void setIsisSysID(String isisSysID) {
        m_isisSysID = isisSysID;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "isisNodeCreateTime", nullable = false)
    public Date getIsisNodeCreateTime() {
        return m_isisNodeCreateTime;
    }

    public void setIsisNodeCreateTime(Date isisNodeCreateTime) {
        m_isisNodeCreateTime = isisNodeCreateTime;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "isisNodeLastPollTime", nullable = false)
    public Date getIsisNodeLastPollTime() {
        return m_isisNodeLastPollTime;
    }

    public void setIsisNodeLastPollTime(Date isisNodeLastPollTime) {
        m_isisNodeLastPollTime = isisNodeLastPollTime;
    }
}
