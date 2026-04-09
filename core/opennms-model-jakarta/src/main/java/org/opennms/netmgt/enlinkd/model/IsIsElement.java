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
import org.opennms.netmgt.enlinkd.model.converter.IsisAdminStateConverter;
import org.opennms.netmgt.model.FilterManager;
import org.opennms.netmgt.model.OnmsNode;

@Entity
@Table(name = "isisElement")
@Filter(name = FilterManager.AUTH_FILTER_NAME, condition = "exists (select distinct x.nodeid from node x join category_node cn on x.nodeid = cn.nodeid join category_group cg on cn.categoryId = cg.categoryId where x.nodeid = nodeid and cg.groupId in (:userGroups))")
public final class IsIsElement implements Serializable {

    private static final long serialVersionUID = -3134355798509685991L;

    public enum IsisAdminState {
        on(1), off(2);
        private final int m_value;
        IsisAdminState(int value) { m_value = value; }
        protected static final Map<Integer, String> s_typeMap = new HashMap<>();
        static { s_typeMap.put(1, "on"); s_typeMap.put(2, "off"); }
        public static String getTypeString(Integer code) {
            if (s_typeMap.containsKey(code)) return s_typeMap.get(code);
            return null;
        }
        public static IsisAdminState get(Integer code) {
            if (code == null) throw new IllegalArgumentException("Cannot create IsisAdminState from null code");
            switch (code) {
                case 1: return on; case 2: return off;
                default: throw new IllegalArgumentException("Cannot create IsisAdminState from code " + code);
            }
        }
        public Integer getValue() { return m_value; }
    }

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

    public void merge(IsIsElement element) {
        if (element == null)
            return;
        setIsisSysID(element.getIsisSysID());
        setIsisSysAdminState(element.getIsisSysAdminState());

        setIsisNodeLastPollTime(element.getIsisNodeCreateTime());
    }

}
