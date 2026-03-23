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

import org.hibernate.annotations.Filter;
import org.opennms.netmgt.model.FilterManager;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.jakarta.converter.InetAddressConverter;

@Entity
@Table(name = "ospfArea")
@Filter(name = FilterManager.AUTH_FILTER_NAME, condition = "exists (select distinct x.nodeid from node x join category_node cn on x.nodeid = cn.nodeid join category_group cg on cn.categoryId = cg.categoryId where x.nodeid = nodeid and cg.groupId in (:userGroups))")
public class OspfArea implements Serializable {

    private static final long serialVersionUID = 3798160983917807494L;

    private Integer m_id;
    private OnmsNode m_node;
    private InetAddress m_ospfAreaId;
    private Integer m_ospfAuthType;
    private Integer m_ospfImportAsExtern;
    private Integer m_ospfAreaBdrRtrCount;
    private Integer m_ospfAsBdrRtrCount;
    private Integer m_ospfAreaLsaCount;
    private Date m_ospfAreaCreateTime = new Date();
    private Date m_ospfAreaLastPollTime;

    public OspfArea() {
    }

    @Id
    @Column(nullable = false)
    @SequenceGenerator(name = "opennmsSequence", sequenceName = "opennmsNxtId", allocationSize = 1)
    @GeneratedValue(generator = "opennmsSequence")
    public Integer getId() {
        return m_id;
    }

    public OspfArea setId(Integer id) {
        m_id = id;
        return this;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nodeId")
    public OnmsNode getNode() {
        return m_node;
    }

    public OspfArea setNode(OnmsNode node) {
        m_node = node;
        return this;
    }

    @Convert(converter = InetAddressConverter.class)
    @Column(name = "ospfAreaId")
    public InetAddress getOspfAreaId() {
        return m_ospfAreaId;
    }

    public OspfArea setOspfAreaId(InetAddress ospfAreaId) {
        m_ospfAreaId = ospfAreaId;
        return this;
    }

    @Column(name = "ospfAuthType")
    public Integer getOspfAuthType() {
        return m_ospfAuthType;
    }

    public OspfArea setOspfAuthType(Integer ospfAuthType) {
        m_ospfAuthType = ospfAuthType;
        return this;
    }

    @Column(name = "ospfImportAsExtern")
    public Integer getOspfImportAsExtern() {
        return m_ospfImportAsExtern;
    }

    public OspfArea setOspfImportAsExtern(Integer ospfImportAsExtern) {
        m_ospfImportAsExtern = ospfImportAsExtern;
        return this;
    }

    @Column(name = "ospfAreaBdrRtrCount")
    public Integer getOspfAreaBdrRtrCount() {
        return m_ospfAreaBdrRtrCount;
    }

    public OspfArea setOspfAreaBdrRtrCount(Integer ospfAreaBdrRtrCount) {
        m_ospfAreaBdrRtrCount = ospfAreaBdrRtrCount;
        return this;
    }

    @Column(name = "ospfAsBdrRtrCount")
    public Integer getOspfAsBdrRtrCount() {
        return m_ospfAsBdrRtrCount;
    }

    public OspfArea setOspfAsBdrRtrCount(Integer ospfAsBdrRtrCount) {
        m_ospfAsBdrRtrCount = ospfAsBdrRtrCount;
        return this;
    }

    @Column(name = "ospfAreaLsaCount")
    public Integer getOspfAreaLsaCount() {
        return m_ospfAreaLsaCount;
    }

    public OspfArea setOspfAreaLsaCount(Integer ospfAreaLsaCount) {
        m_ospfAreaLsaCount = ospfAreaLsaCount;
        return this;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "ospfAreaCreateTime", nullable = false)
    public Date getOspfAreaCreateTime() {
        return m_ospfAreaCreateTime;
    }

    public OspfArea setOspfAreaCreateTime(Date ospfAreaCreateTime) {
        m_ospfAreaCreateTime = ospfAreaCreateTime;
        return this;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "ospfAreaLastPollTime", nullable = false)
    public Date getOspfAreaLastPollTime() {
        return m_ospfAreaLastPollTime;
    }

    public OspfArea setOspfAreaLastPollTime(Date ospfAreaLastPollTime) {
        m_ospfAreaLastPollTime = ospfAreaLastPollTime;
        return this;
    }
}
