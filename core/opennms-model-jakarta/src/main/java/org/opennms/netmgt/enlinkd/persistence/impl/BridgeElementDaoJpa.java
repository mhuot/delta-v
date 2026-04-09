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
import org.opennms.netmgt.enlinkd.persistence.api.BridgeElementDao;
import org.opennms.netmgt.enlinkd.model.BridgeElement;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of BridgeElement data access.
 *
 * <p>Note: {@link #findByNodeId(Integer)} returns a List because a node
 * can have multiple bridge elements (one per VLAN).</p>
 */
@Repository
@Transactional
public class BridgeElementDaoJpa extends AbstractDaoJpa<BridgeElement, Integer> implements BridgeElementDao {

    public BridgeElementDaoJpa() {
        super(BridgeElement.class);
    }

    public List<BridgeElement> findByNodeId(Integer id) {
        return find("SELECT r FROM BridgeElement r WHERE r.node.id = ?1", id);
    }

    public BridgeElement getByNodeIdVlan(Integer id, Integer vlanId) {
        if (vlanId == null) {
            return findUnique(
                    "SELECT r FROM BridgeElement r WHERE r.node.id = ?1 AND r.vlan IS NULL",
                    id);
        }
        return findUnique(
                "SELECT r FROM BridgeElement r WHERE r.node.id = ?1 AND r.vlan = ?2",
                id, vlanId);
    }

    public List<BridgeElement> findByBridgeId(String id) {
        return find("SELECT r FROM BridgeElement r WHERE r.baseBridgeAddress = ?1", id);
    }

    public BridgeElement getByBridgeIdVlan(String id, Integer vlanId) {
        return findUnique(
                "SELECT r FROM BridgeElement r WHERE r.baseBridgeAddress = ?1 AND r.vlan = ?2",
                id, vlanId);
    }

    public void deleteByNodeIdOlderThen(Integer nodeId, Date now) {
        entityManager().createQuery(
                "DELETE FROM BridgeElement r WHERE r.node.id = ?1 AND r.bridgeNodeLastPollTime < ?2")
                .setParameter(1, nodeId)
                .setParameter(2, now)
                .executeUpdate();
    }

    public void deleteByNodeId(Integer nodeId) {
        entityManager().createQuery("DELETE FROM BridgeElement r WHERE r.node.id = ?1")
                .setParameter(1, nodeId)
                .executeUpdate();
    }

    public void deleteAll() {
        entityManager().createQuery("DELETE FROM BridgeElement").executeUpdate();
    }
}
