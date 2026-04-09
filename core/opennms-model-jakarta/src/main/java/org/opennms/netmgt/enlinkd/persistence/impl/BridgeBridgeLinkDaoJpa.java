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

import java.util.Date;
import java.util.List;

import org.deltav.core.daemon.common.AbstractDaoJpa;
import org.opennms.netmgt.enlinkd.persistence.api.BridgeBridgeLinkDao;
import org.opennms.netmgt.enlinkd.model.BridgeBridgeLink;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of BridgeBridgeLink data access.
 */
@Repository
@Transactional
public class BridgeBridgeLinkDaoJpa extends AbstractDaoJpa<BridgeBridgeLink, Integer> implements BridgeBridgeLinkDao {

    public BridgeBridgeLinkDaoJpa() {
        super(BridgeBridgeLink.class);
    }

    public List<BridgeBridgeLink> findByNodeId(Integer id) {
        return find("SELECT r FROM BridgeBridgeLink r WHERE r.node.id = ?1", id);
    }

    public List<BridgeBridgeLink> findByDesignatedNodeId(Integer id) {
        return find("SELECT r FROM BridgeBridgeLink r WHERE r.designatedNode.id = ?1", id);
    }

    public BridgeBridgeLink getByNodeIdBridgePort(Integer id, Integer port) {
        return findUnique(
                "SELECT r FROM BridgeBridgeLink r WHERE r.node.id = ?1 AND r.bridgePort = ?2",
                id, port);
    }

    public BridgeBridgeLink getByNodeIdBridgePortIfIndex(Integer id, Integer ifindex) {
        return findUnique(
                "SELECT r FROM BridgeBridgeLink r WHERE r.node.id = ?1 AND r.bridgePortIfIndex = ?2",
                id, ifindex);
    }

    public List<BridgeBridgeLink> getByDesignatedNodeIdBridgePort(Integer id, Integer port) {
        return find(
                "SELECT r FROM BridgeBridgeLink r WHERE r.designatedNode.id = ?1 AND r.designatedPort = ?2",
                id, port);
    }

    public List<BridgeBridgeLink> getByDesignatedNodeIdBridgePortIfIndex(Integer id, Integer ifindex) {
        return find(
                "SELECT r FROM BridgeBridgeLink r WHERE r.designatedNode.id = ?1 AND r.designatedPortIfIndex = ?2",
                id, ifindex);
    }

    public void deleteByNodeIdOlderThen(Integer nodeId, Date now) {
        entityManager().createQuery(
                "DELETE FROM BridgeBridgeLink r WHERE r.node.id = ?1 AND r.bridgeBridgeLinkLastPollTime < ?2")
                .setParameter(1, nodeId)
                .setParameter(2, now)
                .executeUpdate();
    }

    public void deleteByDesignatedNodeIdOlderThen(Integer nodeId, Date now) {
        entityManager().createQuery(
                "DELETE FROM BridgeBridgeLink r WHERE r.designatedNode.id = ?1 AND r.bridgeBridgeLinkLastPollTime < ?2")
                .setParameter(1, nodeId)
                .setParameter(2, now)
                .executeUpdate();
    }

    public void deleteByNodeId(Integer nodeId) {
        entityManager().createQuery("DELETE FROM BridgeBridgeLink r WHERE r.node.id = ?1")
                .setParameter(1, nodeId)
                .executeUpdate();
    }

    public void deleteByDesignatedNodeId(Integer nodeId) {
        entityManager().createQuery("DELETE FROM BridgeBridgeLink r WHERE r.designatedNode.id = ?1")
                .setParameter(1, nodeId)
                .executeUpdate();
    }

    public void deleteAll() {
        entityManager().createQuery("DELETE FROM BridgeBridgeLink").executeUpdate();
    }
}
