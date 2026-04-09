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
import org.opennms.netmgt.enlinkd.model.OspfElement.TruthValue;
import org.opennms.netmgt.enlinkd.model.converter.CdpGlobalDeviceIdFormatConverter;
import org.opennms.netmgt.enlinkd.model.converter.TruthValueConverter;
import org.opennms.netmgt.model.FilterManager;
import org.opennms.netmgt.model.OnmsNode;

@Entity
@Table(name = "cdpElement")
@Filter(name = FilterManager.AUTH_FILTER_NAME, condition = "exists (select distinct x.nodeid from node x join category_node cn on x.nodeid = cn.nodeid join category_group cg on cn.categoryId = cg.categoryId where x.nodeid = nodeid and cg.groupId in (:userGroups))")
public final class CdpElement implements Serializable {

    private static final long serialVersionUID = -3134355798509685991L;

    public enum CdpGlobalDeviceIdFormat {
        serialNumber(1), macAddress(2), other(3);
        private final int m_type;
        CdpGlobalDeviceIdFormat(int type) { m_type = type; }
        protected static final Map<Integer, String> s_typeMap = new HashMap<>();
        static { s_typeMap.put(1, "serialNumber"); s_typeMap.put(2, "macAddress"); s_typeMap.put(3, "other"); }
        public static String getTypeString(Integer code) {
            if (s_typeMap.containsKey(code)) return s_typeMap.get(code);
            return null;
        }
        public Integer getValue() { return m_type; }
        public static CdpGlobalDeviceIdFormat get(Integer code) {
            if (code == null) throw new IllegalArgumentException("Cannot create CdpDeviceFormat from null code");
            switch (code) {
                case 1: return serialNumber; case 2: return macAddress; case 3: return other;
                default: throw new IllegalArgumentException("Cannot create CdpDeviceIdFormat from code " + code);
            }
        }
    }

    private Integer m_id;
    private TruthValue m_cdpGlobalRun;
    private String m_cdpGlobalDeviceId;
    private CdpGlobalDeviceIdFormat m_cdpGlobalDeviceIdFormat;
    private Date m_cdpNodeCreateTime = new Date();
    private Date m_cdpNodeLastPollTime;
    private OnmsNode m_node;

    public CdpElement() {
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

    @Column(name = "cdpGlobalRun", nullable = false)
    @Convert(converter = TruthValueConverter.class)
    public TruthValue getCdpGlobalRun() {
        return m_cdpGlobalRun;
    }

    public void setCdpGlobalRun(TruthValue cdpGlobalRun) {
        m_cdpGlobalRun = cdpGlobalRun;
    }

    @Column(name = "cdpGlobalDeviceId", length = 256, nullable = false)
    public String getCdpGlobalDeviceId() {
        return m_cdpGlobalDeviceId;
    }

    public void setCdpGlobalDeviceId(String cdpGlobalDeviceId) {
        m_cdpGlobalDeviceId = cdpGlobalDeviceId;
    }

    @Column(name = "cdpGlobalDeviceIdFormat")
    @Convert(converter = CdpGlobalDeviceIdFormatConverter.class)
    public CdpGlobalDeviceIdFormat getCdpGlobalDeviceIdFormat() {
        return m_cdpGlobalDeviceIdFormat;
    }

    public void setCdpGlobalDeviceIdFormat(CdpGlobalDeviceIdFormat cdpGlobalDeviceIdFormat) {
        m_cdpGlobalDeviceIdFormat = cdpGlobalDeviceIdFormat;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "cdpNodeCreateTime", nullable = false)
    public Date getCdpNodeCreateTime() {
        return m_cdpNodeCreateTime;
    }

    public void setCdpNodeCreateTime(Date cdpNodeCreateTime) {
        m_cdpNodeCreateTime = cdpNodeCreateTime;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "cdpNodeLastPollTime", nullable = false)
    public Date getCdpNodeLastPollTime() {
        return m_cdpNodeLastPollTime;
    }

    public void setCdpNodeLastPollTime(Date cdpNodeLastPollTime) {
        m_cdpNodeLastPollTime = cdpNodeLastPollTime;
    }

    public void merge(CdpElement element) {
        if (element == null)
            return;
        setCdpGlobalRun(element.getCdpGlobalRun());
        setCdpGlobalDeviceId(element.getCdpGlobalDeviceId());
        setCdpNodeLastPollTime(element.getCdpNodeCreateTime());
    }

}
