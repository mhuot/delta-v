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

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
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

    public enum ImportAsExtern {
        IMPORT_EXTERNAL(1), IMPORT_NO_EXTERNAL(2), IMPORT_NSSA(3);
        private final int value;
        private static Map map = new HashMap<>();
        private ImportAsExtern(Integer value) { this.value = value; }
        static { for (ImportAsExtern importAsExtern : ImportAsExtern.values()) { map.put(importAsExtern.value, importAsExtern); } }
        public static ImportAsExtern valueOf(int importAsExtern) { return (ImportAsExtern) map.get(importAsExtern); }
        public Integer getValue() { return value; }
    }

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

    public void merge(OspfArea area) {
        if (area == null)
            return;
        setOspfAreaId(area.getOspfAreaId());
        setOspfAuthType(area.getOspfAuthType());
        setOspfImportAsExtern(area.getOspfImportAsExtern());
        setOspfAreaBdrRtrCount(area.getOspfAreaBdrRtrCount());
        setOspfAsBdrRtrCount(area.getOspfAsBdrRtrCount());
        setOspfAreaLsaCount(area.getOspfAreaLsaCount());
        setOspfAreaCreateTime(area.getOspfAreaCreateTime());
        setOspfAreaLastPollTime(area.getOspfAreaCreateTime());
    }

}
