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
package org.opennms.netmgt.enlinkd.persistence.impl;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.List;

import org.deltav.core.daemon.common.AbstractDaoJpa;
import org.opennms.netmgt.enlinkd.persistence.api.LldpLinkDao;
import org.opennms.netmgt.enlinkd.model.LldpLink;
import org.opennms.netmgt.model.OnmsIpInterface;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.OnmsSnmpInterface;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * JPA implementation of LldpLink data access.
 */
@Repository
@Transactional
public class LldpLinkDaoJpa extends AbstractDaoJpa<LldpLink, Integer> implements LldpLinkDao {

    public LldpLinkDaoJpa() {
        super(LldpLink.class);
    }

    public LldpLink get(OnmsNode node, Integer lldpRemLocalPortNum, Integer lldpRemIndex) {
        return findUnique(
                "SELECT l FROM LldpLink l WHERE l.node = ?1 AND l.lldpRemLocalPortNum = ?2 AND l.lldpRemIndex = ?3",
                node, lldpRemLocalPortNum, lldpRemIndex);
    }

    public LldpLink get(Integer nodeId, Integer lldpRemLocalPortNum, Integer lldpRemIndex) {
        Assert.notNull(nodeId, "nodeId cannot be null");
        Assert.notNull(lldpRemLocalPortNum, "lldpRemLocalPortNum cannot be null");
        Assert.notNull(lldpRemIndex, "lldpRemIndex cannot be null");
        return findUnique(
                "SELECT l FROM LldpLink l WHERE l.node.id = ?1 AND l.lldpRemLocalPortNum = ?2 AND l.lldpRemIndex = ?3",
                nodeId, lldpRemLocalPortNum, lldpRemIndex);
    }

    public List<LldpLink> findByNodeId(Integer nodeId) {
        Assert.notNull(nodeId, "nodeId cannot be null");
        return find("SELECT l FROM LldpLink l WHERE l.node.id = ?1", nodeId);
    }

    public void deleteByNodeIdOlderThen(Integer nodeId, Date now) {
        entityManager().createQuery(
                "DELETE FROM LldpLink l WHERE l.node.id = ?1 AND l.lldpLinkLastPollTime < ?2")
                .setParameter(1, nodeId)
                .setParameter(2, now)
                .executeUpdate();
    }

    public void deleteByNodeId(Integer nodeId) {
        entityManager().createQuery("DELETE FROM LldpLink l WHERE l.node.id = ?1")
                .setParameter(1, nodeId)
                .executeUpdate();
    }

    public void deleteAll() {
        entityManager().createQuery("DELETE FROM LldpLink").executeUpdate();
    }

    public List<LldpLink> findLinksForIds(List<Integer> linkIds) {
        if (linkIds == null || linkIds.isEmpty()) {
            return List.of();
        }
        return find("SELECT l FROM LldpLink l WHERE l.id IN ?1", linkIds);
    }

    @SuppressWarnings("unchecked")
    public Integer getIfIndex(Integer nodeId, String portId) {
        Assert.notNull(nodeId, "nodeId may not be null");
        Assert.notNull(portId, "portId may not be null");

        List<OnmsSnmpInterface> snmpIfaces = entityManager().createQuery(
                "SELECT s FROM OnmsSnmpInterface s WHERE s.node.id = ?1 " +
                "AND (LOWER(s.ifDescr) = LOWER(?2) OR LOWER(s.ifName) = LOWER(?3) OR s.physAddr = ?4)")
                .setParameter(1, nodeId)
                .setParameter(2, portId)
                .setParameter(3, portId)
                .setParameter(4, portId)
                .getResultList();
        if (snmpIfaces.size() == 1) {
            return snmpIfaces.get(0).getIfIndex();
        }

        InetAddress portAddr;
        try {
            portAddr = InetAddress.getByName(portId);
        } catch (UnknownHostException e) {
            return -1;
        }

        List<OnmsIpInterface> ipIfaces = entityManager().createQuery(
                "SELECT i FROM OnmsIpInterface i WHERE i.node.id = ?1 AND i.ipAddress = ?2")
                .setParameter(1, nodeId)
                .setParameter(2, portAddr)
                .getResultList();
        if (ipIfaces.size() == 1) {
            OnmsIpInterface ipIf = ipIfaces.get(0);
            if (ipIf.getSnmpInterface() != null) {
                List<OnmsSnmpInterface> snmpByPk = entityManager().createQuery(
                        "SELECT s FROM OnmsSnmpInterface s WHERE s.id = ?1")
                        .setParameter(1, ipIf.getSnmpInterface().getId())
                        .getResultList();
                if (snmpByPk.size() == 1) {
                    return snmpByPk.get(0).getIfIndex();
                }
            }
        }
        return -1;
    }
}
