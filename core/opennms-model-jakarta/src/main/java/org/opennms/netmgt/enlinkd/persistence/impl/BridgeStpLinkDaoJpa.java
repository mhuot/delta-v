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
import org.opennms.netmgt.enlinkd.persistence.api.BridgeStpLinkDao;
import org.opennms.netmgt.enlinkd.model.BridgeStpLink;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of BridgeStpLink data access.
 */
@Repository
@Transactional
public class BridgeStpLinkDaoJpa extends AbstractDaoJpa<BridgeStpLink, Integer> implements BridgeStpLinkDao {

    public BridgeStpLinkDaoJpa() {
        super(BridgeStpLink.class);
    }

    public List<BridgeStpLink> findByNodeId(Integer id) {
        return find("SELECT r FROM BridgeStpLink r WHERE r.node.id = ?1", id);
    }

    public BridgeStpLink getByNodeIdBridgePort(Integer id, Integer port) {
        return findUnique(
                "SELECT r FROM BridgeStpLink r WHERE r.node.id = ?1 AND r.stpPort = ?2",
                id, port);
    }

    public List<BridgeStpLink> findByDesignatedBridge(String designated) {
        return find("SELECT r FROM BridgeStpLink r WHERE r.designatedBridge = ?1", designated);
    }

    public List<BridgeStpLink> findByDesignatedRoot(String root) {
        return find("SELECT r FROM BridgeStpLink r WHERE r.designatedRoot = ?1", root);
    }

    public void deleteByNodeIdOlderThen(Integer nodeId, Date now) {
        entityManager().createQuery(
                "DELETE FROM BridgeStpLink r WHERE r.node.id = ?1 AND r.bridgeStpLinkLastPollTime < ?2")
                .setParameter(1, nodeId)
                .setParameter(2, now)
                .executeUpdate();
    }

    public void deleteByNodeId(Integer nodeId) {
        entityManager().createQuery("DELETE FROM BridgeStpLink r WHERE r.node.id = ?1")
                .setParameter(1, nodeId)
                .executeUpdate();
    }

    public void deleteAll() {
        entityManager().createQuery("DELETE FROM BridgeStpLink").executeUpdate();
    }
}
