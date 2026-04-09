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
import org.opennms.core.utils.LldpUtils.LldpChassisIdSubType;
import org.opennms.netmgt.enlinkd.model.converter.LldpChassisIdSubTypeConverter;
import org.opennms.netmgt.model.FilterManager;
import org.opennms.netmgt.model.OnmsNode;

@Entity
@Table(name = "lldpElement")
@Filter(name = FilterManager.AUTH_FILTER_NAME, condition = "exists (select distinct x.nodeid from node x join category_node cn on x.nodeid = cn.nodeid join category_group cg on cn.categoryId = cg.categoryId where x.nodeid = nodeid and cg.groupId in (:userGroups))")
public final class LldpElement implements Serializable {

    private static final long serialVersionUID = -3134355798509685991L;

    private Integer m_id;
    private String m_lldpChassisId;
    private String m_lldpSysname;
    private LldpChassisIdSubType m_lldpChassisIdSubType;
    private Date m_lldpNodeCreateTime = new Date();
    private Date m_lldpNodeLastPollTime;
    private OnmsNode m_node;

    public LldpElement() {
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

    @Column(name = "lldpChassisIdSubType", nullable = false)
    @Convert(converter = LldpChassisIdSubTypeConverter.class)
    public LldpChassisIdSubType getLldpChassisIdSubType() {
        return m_lldpChassisIdSubType;
    }

    public void setLldpChassisIdSubType(LldpChassisIdSubType lldpChassisIdSubType) {
        m_lldpChassisIdSubType = lldpChassisIdSubType;
    }

    @Column(name = "lldpSysname", length = 256, nullable = false)
    public String getLldpSysname() {
        return m_lldpSysname;
    }

    public void setLldpSysname(String lldpSysname) {
        m_lldpSysname = lldpSysname;
    }

    @Column(name = "lldpChassisId", length = 256, nullable = false)
    public String getLldpChassisId() {
        return m_lldpChassisId;
    }

    public void setLldpChassisId(String lldpChassisId) {
        m_lldpChassisId = lldpChassisId;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "lldpNodeCreateTime", nullable = false)
    public Date getLldpNodeCreateTime() {
        return m_lldpNodeCreateTime;
    }

    public void setLldpNodeCreateTime(Date lldpNodeCreateTime) {
        m_lldpNodeCreateTime = lldpNodeCreateTime;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "lldpNodeLastPollTime", nullable = false)
    public Date getLldpNodeLastPollTime() {
        return m_lldpNodeLastPollTime;
    }

    public void setLldpNodeLastPollTime(Date lldpNodeLastPollTime) {
        m_lldpNodeLastPollTime = lldpNodeLastPollTime;
    }

    public void merge(LldpElement element) {
        if (element == null)
            return;
        setLldpChassisId(element.getLldpChassisId());
        setLldpChassisIdSubType(element.getLldpChassisIdSubType());
        setLldpSysname(element.getLldpSysname());
        setLldpNodeLastPollTime(element.getLldpNodeCreateTime());
    }

}
