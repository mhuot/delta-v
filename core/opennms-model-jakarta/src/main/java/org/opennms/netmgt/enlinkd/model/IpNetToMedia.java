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
import jakarta.persistence.Transient;

import org.hibernate.annotations.Filter;
import org.opennms.netmgt.model.FilterManager;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.jakarta.converter.InetAddressConverter;

@Entity
@Table(name = "ipNetToMedia")
@Filter(name = FilterManager.AUTH_FILTER_NAME, condition = "exists (select distinct x.nodeid from node x join category_node cn on x.nodeid = cn.nodeid join category_group cg on cn.categoryId = cg.categoryId where x.nodeid = nodeid and cg.groupId in (:userGroups))")
public class IpNetToMedia implements Serializable {

    private static final long serialVersionUID = 7750043250236397014L;

    public enum IpNetToMediaType {
        IPNETTOMEDIA_TYPE_OTHER(1), IPNETTOMEDIA_TYPE_INVALID(2), IPNETTOMEDIA_TYPE_DYNAMIC(3), IPNETTOMEDIA_TYPE_STATIC(4);
        private final int m_value;
        IpNetToMediaType(int value) { m_value = value; }
        static final Map<Integer, String> s_typeMap = new HashMap<>();
        static { s_typeMap.put(1, "other"); s_typeMap.put(2, "invalid"); s_typeMap.put(3, "dynamic"); s_typeMap.put(4, "static"); }
        public static String getTypeString(Integer code) { if (s_typeMap.containsKey(code)) return s_typeMap.get(code); return null; }
        public static IpNetToMediaType get(Integer code) {
            if (code == null) return null;
            switch (code) { case 1: return IPNETTOMEDIA_TYPE_OTHER; case 2: return IPNETTOMEDIA_TYPE_INVALID;
                case 3: return IPNETTOMEDIA_TYPE_DYNAMIC; case 4: return IPNETTOMEDIA_TYPE_STATIC; default: return null; }
        }
        public Integer getValue() { return m_value; }
    }

    private Integer m_id;
    private OnmsNode m_node;
    private Integer m_ifIndex;
    private String m_port;
    private InetAddress m_netAddress;
    private String m_physAddress;
    private IpNetToMediaType m_ipNetToMediaType;
    private OnmsNode m_sourceNode;
    private Integer m_sourceIfIndex;
    private Date m_createTime = new Date();
    private Date m_lastPollTime;

    public IpNetToMedia() {
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

    @Column(name = "netAddress", nullable = false)
    @Convert(converter = InetAddressConverter.class)
    public InetAddress getNetAddress() {
        return m_netAddress;
    }

    public void setNetAddress(InetAddress netAddress) {
        m_netAddress = netAddress;
    }

    @Column(name = "physAddress", length = 32, nullable = false)
    public String getPhysAddress() {
        return m_physAddress;
    }

    public void setPhysAddress(String physAddr) {
        m_physAddress = physAddr;
    }

    @Transient
    public IpNetToMediaType getIpNetToMediaType() {
        return m_ipNetToMediaType;
    }

    public void setIpNetToMediaType(IpNetToMediaType ipNetToMediaType) {
        m_ipNetToMediaType = ipNetToMediaType;
    }

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sourceNodeId", nullable = false)
    public OnmsNode getSourceNode() {
        return m_sourceNode;
    }

    public void setSourceNode(OnmsNode sourceNode) {
        m_sourceNode = sourceNode;
    }

    @Column(name = "sourceIfIndex", nullable = false)
    public Integer getSourceIfIndex() {
        return m_sourceIfIndex;
    }

    public void setSourceIfIndex(Integer sourceIfIndex) {
        m_sourceIfIndex = sourceIfIndex;
    }

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "nodeId")
    public OnmsNode getNode() {
        return m_node;
    }

    public void setNode(OnmsNode node) {
        m_node = node;
    }

    @Column(name = "ifIndex")
    public Integer getIfIndex() {
        return m_ifIndex;
    }

    public void setIfIndex(Integer ifIndex) {
        m_ifIndex = ifIndex;
    }

    public String getPort() {
        return m_port;
    }

    public void setPort(String port) {
        m_port = port;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "createTime", nullable = false)
    public Date getCreateTime() {
        return m_createTime;
    }

    public void setCreateTime(Date createTime) {
        m_createTime = createTime;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "lastPollTime", nullable = false)
    public Date getLastPollTime() {
        return m_lastPollTime;
    }

    public void setLastPollTime(Date lastPollTime) {
        m_lastPollTime = lastPollTime;
    }

    public void merge(IpNetToMedia element) {
        setNode(element.getNode());
        setIfIndex(element.getIfIndex());
        setPort(element.getPort());
        setSourceNode(element.getSourceNode());
        setSourceIfIndex(element.getSourceIfIndex());
        setLastPollTime(element.getCreateTime());
    }

}
