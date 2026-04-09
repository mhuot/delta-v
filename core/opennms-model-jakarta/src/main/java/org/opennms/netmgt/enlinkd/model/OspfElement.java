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
import java.net.InetAddress;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

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
import org.opennms.netmgt.enlinkd.model.converter.StatusConverter;
import org.opennms.netmgt.enlinkd.model.converter.TruthValueConverter;
import org.opennms.netmgt.model.FilterManager;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.jakarta.converter.InetAddressConverter;

@Entity
@Table(name = "ospfElement")
@Filter(name = FilterManager.AUTH_FILTER_NAME, condition = "exists (select distinct x.nodeid from node x join category_node cn on x.nodeid = cn.nodeid join category_group cg on cn.categoryId = cg.categoryId where x.nodeid = nodeid and cg.groupId in (:userGroups))")
public final class OspfElement implements Serializable {

    private static final long serialVersionUID = 7820026592390162672L;

    public enum TruthValue {
        TRUE(1), FALSE(2);
        private final int m_type;
        TruthValue(int type) { m_type = type; }
        protected static final Map<Integer, String> s_typeMap = new HashMap<>();
        static { s_typeMap.put(1, "true"); s_typeMap.put(2, "false"); }
        public static String getTypeString(Integer code) {
            if (s_typeMap.containsKey(code)) return s_typeMap.get(code);
            return null;
        }
        public Integer getValue() { return m_type; }
        public static TruthValue get(Integer code) {
            if (code == null) throw new IllegalArgumentException("Cannot create TruthValue from null code");
            switch (code) {
                case 1: return TRUE; case 2: return FALSE;
                default: throw new IllegalArgumentException("Cannot create TruthValue from code " + code);
            }
        }
    }

    public enum Status {
        enabled(1), disabled(2);
        private final int m_type;
        Status(int type) { m_type = type; }
        protected static final Map<Integer, String> s_typeMap = new HashMap<>();
        static { s_typeMap.put(1, "enabled"); s_typeMap.put(2, "disabled"); }
        public static String getTypeString(Integer code) {
            if (s_typeMap.containsKey(code)) return s_typeMap.get(code);
            return null;
        }
        public Integer getValue() { return m_type; }
        public static Status get(Integer code) {
            if (code == null) throw new IllegalArgumentException("Cannot create Status from null code");
            switch (code) {
                case 1: return enabled; case 2: return disabled;
                default: throw new IllegalArgumentException("Cannot create Status from code " + code);
            }
        }
    }

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

    public void merge(OspfElement element) {
        if (element == null)
            return;
        setOspfRouterId(element.getOspfRouterId());
        setOspfRouterIdIfindex(element.getOspfRouterIdIfindex());
        setOspfRouterIdNetmask(element.getOspfRouterIdNetmask());
        setOspfAdminStat(element.getOspfAdminStat());
        setOspfASBdrRtrStatus(element.getOspfASBdrRtrStatus());
        setOspfBdrRtrStatus(element.getOspfASBdrRtrStatus());
        setOspfNodeLastPollTime(element.getOspfNodeCreateTime());
    }

}
