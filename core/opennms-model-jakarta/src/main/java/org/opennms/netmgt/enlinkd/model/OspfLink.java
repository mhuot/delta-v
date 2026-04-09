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
@Table(name = "ospfLink")
@Filter(name = FilterManager.AUTH_FILTER_NAME, condition = "exists (select distinct x.nodeid from node x join category_node cn on x.nodeid = cn.nodeid join category_group cg on cn.categoryId = cg.categoryId where x.nodeid = nodeid and cg.groupId in (:userGroups))")
public class OspfLink implements Serializable {

    private static final long serialVersionUID = 3798160983917807494L;

    private Integer m_id;
    private OnmsNode m_node;
    private InetAddress m_ospfIpAddr;
    private InetAddress m_ospfIpMask;
    private Integer m_ospfIfIndex;
    private Integer m_ospfAddressLessIndex;
    private InetAddress m_ospfRemRouterId;
    private InetAddress m_ospfRemIpAddr;
    private Integer m_ospfRemAddressLessIndex;
    private Date m_ospfLinkCreateTime = new Date();
    private Date m_ospfLinkLastPollTime;
    private InetAddress m_ospfIfAreaId;

    public OspfLink() {
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

    @Convert(converter = InetAddressConverter.class)
    @Column(name = "ospfIpAddr")
    public InetAddress getOspfIpAddr() {
        return m_ospfIpAddr;
    }

    public void setOspfIpAddr(InetAddress ospfIpAddr) {
        m_ospfIpAddr = ospfIpAddr;
    }

    @Convert(converter = InetAddressConverter.class)
    @Column(name = "ospfIpMask")
    public InetAddress getOspfIpMask() {
        return m_ospfIpMask;
    }

    public void setOspfIpMask(InetAddress ospfIpMask) {
        m_ospfIpMask = ospfIpMask;
    }

    @Column(name = "ospfAddressLessIndex")
    public Integer getOspfAddressLessIndex() {
        return m_ospfAddressLessIndex;
    }

    public void setOspfAddressLessIndex(Integer ospfAddressLessIndex) {
        m_ospfAddressLessIndex = ospfAddressLessIndex;
    }

    @Convert(converter = InetAddressConverter.class)
    @Column(name = "ospfIfAreaId")
    public InetAddress getOspfIfAreaId() {
        return m_ospfIfAreaId;
    }

    public void setOspfIfAreaId(InetAddress ospfIfAreaId) {
        m_ospfIfAreaId = ospfIfAreaId;
    }

    @Column(name = "ospfIfIndex")
    public Integer getOspfIfIndex() {
        return m_ospfIfIndex;
    }

    public void setOspfIfIndex(Integer ospfIfIndex) {
        m_ospfIfIndex = ospfIfIndex;
    }

    @Convert(converter = InetAddressConverter.class)
    @Column(name = "ospfRemRouterId", nullable = false)
    public InetAddress getOspfRemRouterId() {
        return m_ospfRemRouterId;
    }

    public void setOspfRemRouterId(InetAddress ospfRemRouterId) {
        m_ospfRemRouterId = ospfRemRouterId;
    }

    @Convert(converter = InetAddressConverter.class)
    @Column(name = "ospfRemIpAddr", nullable = false)
    public InetAddress getOspfRemIpAddr() {
        return m_ospfRemIpAddr;
    }

    public void setOspfRemIpAddr(InetAddress ospfRemIpAddr) {
        m_ospfRemIpAddr = ospfRemIpAddr;
    }

    @Column(name = "ospfRemAddressLessIndex", nullable = false)
    public Integer getOspfRemAddressLessIndex() {
        return m_ospfRemAddressLessIndex;
    }

    public void setOspfRemAddressLessIndex(Integer ospfRemAddressLessIndex) {
        m_ospfRemAddressLessIndex = ospfRemAddressLessIndex;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "ospfLinkCreateTime", nullable = false)
    public Date getOspfLinkCreateTime() {
        return m_ospfLinkCreateTime;
    }

    public void setOspfLinkCreateTime(Date ospfLinkCreateTime) {
        m_ospfLinkCreateTime = ospfLinkCreateTime;
    }

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "ospfLinkLastPollTime", nullable = false)
    public Date getOspfLinkLastPollTime() {
        return m_ospfLinkLastPollTime;
    }

    public void setOspfLinkLastPollTime(Date ospfLinkLastPollTime) {
        m_ospfLinkLastPollTime = ospfLinkLastPollTime;
    }

    public void merge(OspfLink link) {
        if (link == null)
            return;
        setOspfIpAddr(link.getOspfIpAddr());
        setOspfIpMask(link.getOspfIpMask());
        setOspfIfIndex(link.getOspfIfIndex());
        setOspfAddressLessIndex(link.getOspfAddressLessIndex());
        setOspfIfAreaId(link.getOspfIfAreaId());

        setOspfRemRouterId(link.getOspfRemRouterId());
        setOspfRemIpAddr(link.getOspfRemIpAddr());
        setOspfRemAddressLessIndex(link.getOspfRemAddressLessIndex());

        setOspfLinkLastPollTime(link.getOspfLinkCreateTime());
    }

}
