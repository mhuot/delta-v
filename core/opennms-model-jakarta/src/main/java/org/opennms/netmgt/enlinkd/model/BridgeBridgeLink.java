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

@Entity
@Table(name = "bridgeBridgeLink")
@Filter(name = FilterManager.AUTH_FILTER_NAME, condition =
        "exists (select distinct x.nodeid from node x join category_node cn on x.nodeid = cn.nodeid join category_group cg on cn.categoryId = cg.categoryId where x.nodeid = nodeid and cg.groupId in (:userGroups))"
                + " and "
                + "exists (select distinct x.nodeid from node x join category_node cn on x.nodeid = cn.nodeid join category_group cg on cn.categoryId = cg.categoryId where x.nodeid = designatednodeId and cg.groupId in (:userGroups))")
public class BridgeBridgeLink implements Serializable {

    private static final long serialVersionUID = 5224397770784854885L;

    private Integer m_id;
    private OnmsNode m_node;
    private Integer m_bridgePort;
    private Integer m_bridgePortIfIndex;
    private String m_bridgePortIfName;
    private Integer m_vlan;
    private OnmsNode m_designatedNode;
    private Integer m_designatedPort;
    private Integer m_designatedPortIfIndex;
    private String m_designatedPortIfName;
    private Integer m_designatedVlan;
    private Date m_bridgeBridgeLinkCreateTime = new Date();
    private Date m_bridgeBridgeLinkLastPollTime;

    public BridgeBridgeLink() {
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

    @Column(name = "bridgePort")
    public Integer getBridgePort() {
        return m_bridgePort;
    }

    public void setBridgePort(Integer bridgePort) {
        m_bridgePort = bridgePort;
    }

    @Column(name = "bridgePortIfIndex")
    public Integer getBridgePortIfIndex() {
        return m_bridgePortIfIndex;
    }

    public void setBridgePortIfIndex(Integer bridgePortIfIndex) {
        m_bridgePortIfIndex = bridgePortIfIndex;
    }

    @Column(name = "bridgePortIfName", length = 32)
    public String getBridgePortIfName() {
        return m_bridgePortIfName;
    }

    public void setBridgePortIfName(String bridgePortIfName) {
        m_bridgePortIfName = bridgePortIfName;
    }

    @Column(name = "vlan")
    public Integer getVlan() {
        return m_vlan;
    }

    public void setVlan(Integer vlan) {
        m_vlan = vlan;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "designatednodeId", referencedColumnName = "nodeId")
    public OnmsNode getDesignatedNode() {
        return m_designatedNode;
    }

    public void setDesignatedNode(OnmsNode designatedNode) {
        m_designatedNode = designatedNode;
    }

    @Column(name = "designatedBridgePort")
    public Integer getDesignatedPort() {
        return m_designatedPort;
    }

    public void setDesignatedPort(Integer bridgePort) {
        m_designatedPort = bridgePort;
    }

    @Column(name = "designatedBridgePortIfIndex")
    public Integer getDesignatedPortIfIndex() {
        return m_designatedPortIfIndex;
    }

    public void setDesignatedPortIfIndex(Integer bridgePortIfIndex) {
        m_designatedPortIfIndex = bridgePortIfIndex;
    }

    @Column(name = "designatedBridgePortIfName", length = 32)
    public String getDesignatedPortIfName() {
        return m_designatedPortIfName;
    }

    public void setDesignatedPortIfName(String bridgePortIfName) {
        m_designatedPortIfName = bridgePortIfName;
    }

    @Column(name = "designatedVlan")
    public Integer getDesignatedVlan() {
        return m_designatedVlan;
    }

    public void setDesignatedVlan(Integer vlan) {
        m_designatedVlan = vlan;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "bridgeBridgeLinkCreateTime", nullable = false)
    public Date getBridgeBridgeLinkCreateTime() {
        return m_bridgeBridgeLinkCreateTime;
    }

    public void setBridgeBridgeLinkCreateTime(Date bridgeLinkCreateTime) {
        m_bridgeBridgeLinkCreateTime = bridgeLinkCreateTime;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "bridgeBridgeLinkLastPollTime", nullable = false)
    public Date getBridgeBridgeLinkLastPollTime() {
        return m_bridgeBridgeLinkLastPollTime;
    }

    public void setBridgeBridgeLinkLastPollTime(Date bridgeLinkLastPollTime) {
        m_bridgeBridgeLinkLastPollTime = bridgeLinkLastPollTime;
    }

    public void merge(BridgeBridgeLink element) {
        if (element == null)
                return;

        setBridgePortIfIndex(element.getBridgePortIfIndex());
        setBridgePortIfName(element.getBridgePortIfName());
        setVlan(element.getVlan());

        setDesignatedNode(element.getDesignatedNode());
        setDesignatedPort(element.getDesignatedPort());
        setDesignatedPortIfIndex(element.getDesignatedPortIfIndex());
        setDesignatedPortIfName(element.getDesignatedPortIfName());
        setDesignatedVlan(element.getDesignatedVlan());
        if (element.getBridgeBridgeLinkLastPollTime() == null)
            setBridgeBridgeLinkLastPollTime(element.getBridgeBridgeLinkCreateTime());
        else
           setBridgeBridgeLinkLastPollTime(element.getBridgeBridgeLinkLastPollTime());
    }

}
