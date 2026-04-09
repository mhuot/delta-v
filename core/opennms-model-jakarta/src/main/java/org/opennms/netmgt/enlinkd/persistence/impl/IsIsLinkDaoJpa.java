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
import org.opennms.netmgt.enlinkd.persistence.api.IsIsLinkDao;
import org.opennms.netmgt.enlinkd.model.IsIsLink;
import org.opennms.netmgt.model.OnmsNode;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * JPA implementation of IsIsLink data access.
 */
@Repository
@Transactional
public class IsIsLinkDaoJpa extends AbstractDaoJpa<IsIsLink, Integer> implements IsIsLinkDao {

    public IsIsLinkDaoJpa() {
        super(IsIsLink.class);
    }

    public IsIsLink get(OnmsNode node, Integer isisCircIndex, Integer isisISAdjIndex) {
        return findUnique(
                "SELECT i FROM IsIsLink i WHERE i.node = ?1 AND i.isisCircIndex = ?2 AND i.isisISAdjIndex = ?3",
                node, isisCircIndex, isisISAdjIndex);
    }

    public IsIsLink get(Integer nodeId, Integer isisCircIndex, Integer isisISAdjIndex) {
        Assert.notNull(nodeId, "nodeId cannot be null");
        Assert.notNull(isisCircIndex, "isisCircIndex cannot be null");
        Assert.notNull(isisISAdjIndex, "isisISAdjIndex cannot be null");
        return findUnique(
                "SELECT i FROM IsIsLink i WHERE i.node.id = ?1 AND i.isisCircIndex = ?2 AND i.isisISAdjIndex = ?3",
                nodeId, isisCircIndex, isisISAdjIndex);
    }

    public List<IsIsLink> findByNodeId(Integer nodeId) {
        Assert.notNull(nodeId, "nodeId cannot be null");
        return find("SELECT i FROM IsIsLink i WHERE i.node.id = ?1", nodeId);
    }

    public List<IsIsLink> findBySysIdAndAdjAndCircIndex(int nodeId) {
        return find(
                "SELECT r FROM IsIsLink r WHERE EXISTS " +
                "(SELECT e FROM IsIsElement e, IsIsLink l WHERE " +
                "r.node.id = e.node.id AND r.isisISAdjIndex = l.isisISAdjIndex " +
                "AND r.isisCircIndex = l.isisCircIndex AND e.isisSysID = l.isisISAdjNeighSysID " +
                "AND l.node.id = ?1)",
                nodeId);
    }

    public void deleteByNodeIdOlderThen(Integer nodeId, Date now) {
        entityManager().createQuery(
                "DELETE FROM IsIsLink i WHERE i.node.id = ?1 AND i.isisLinkLastPollTime < ?2")
                .setParameter(1, nodeId)
                .setParameter(2, now)
                .executeUpdate();
    }

    public void deleteByNodeId(Integer nodeId) {
        entityManager().createQuery("DELETE FROM IsIsLink i WHERE i.node.id = ?1")
                .setParameter(1, nodeId)
                .executeUpdate();
    }

    public void deleteAll() {
        entityManager().createQuery("DELETE FROM IsIsLink").executeUpdate();
    }
}
