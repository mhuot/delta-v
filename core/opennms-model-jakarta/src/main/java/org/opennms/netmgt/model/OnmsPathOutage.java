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
package org.opennms.netmgt.model;

import java.io.Serializable;
import java.net.InetAddress;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

import org.opennms.netmgt.model.jakarta.converter.InetAddressConverter;

/**
 * <p>OnmsPathOutage class</p>
 *
 * @author <a href="ryan@mail1.opennms.com"> Ryan Lambeth </a>
 */
@Entity
@Table(name="pathoutage")
public class OnmsPathOutage implements Serializable {

    private static final long serialVersionUID = 2180867754702562743L;

    private int m_nodeId;
    private InetAddress m_criticalPathIp;
    private String m_criticalPathServiceName;
    private OnmsNode m_node;

    /**
     * <p>Constructor for OnmsPathOutage</p>
     */
    public OnmsPathOutage(OnmsNode node, InetAddress criticalPathIp, String criticalPathServiceName) {
        m_nodeId = node.getId();
        m_node = node;
        m_criticalPathIp = criticalPathIp;
        m_criticalPathServiceName = criticalPathServiceName;
    }

    public OnmsPathOutage() {
    }

    /**
     * The node this path outage information belongs to.
     *
     * @return a {@link org.opennms.netmgt.model.OnmsNode} object.
     */
    @OneToOne
    @PrimaryKeyJoinColumn
    @MapsId
    public OnmsNode getNode() {
        return m_node;
    }

    public void setNode(OnmsNode node) {
        m_node = node;
    }

    /**
     * The pathOutage table uses the node ID as its primary key.
     * In Hibernate 7, the legacy "foreign" GenericGenerator is replaced by @MapsId
     * on the @OneToOne association, which derives the ID from the related entity.
     */
    @Id
    @Column(name="nodeId")
    public int getNodeId() {
        return m_nodeId;
    }

    public void setNodeId(int id) {
        m_nodeId = id;
    }

    @Column(name="criticalpathip", nullable = false)
    @Convert(converter = InetAddressConverter.class)
    public InetAddress getCriticalPathIp() {
        return m_criticalPathIp;
    }

    public void setCriticalPathIp(InetAddress criticalPathIp) {
        m_criticalPathIp = criticalPathIp;
    }

    @Column(name="criticalpathservicename")
    public String getCriticalPathServiceName() {
        return m_criticalPathServiceName;
    }

    public void setCriticalPathServiceName(String criticalPathServiceName) {
        m_criticalPathServiceName = criticalPathServiceName;
    }
}
