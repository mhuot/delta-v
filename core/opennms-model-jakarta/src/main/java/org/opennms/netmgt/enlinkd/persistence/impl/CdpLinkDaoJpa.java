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
import org.opennms.netmgt.enlinkd.persistence.api.CdpLinkDao;
import org.opennms.netmgt.enlinkd.model.CdpLink;
import org.opennms.netmgt.model.OnmsNode;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * JPA implementation of CdpLink data access.
 */
@Repository
@Transactional
public class CdpLinkDaoJpa extends AbstractDaoJpa<CdpLink, Integer> implements CdpLinkDao {

    public CdpLinkDaoJpa() {
        super(CdpLink.class);
    }

    public CdpLink get(OnmsNode node, Integer cdpCacheIfIndex, Integer cdpCacheDeviceIndex) {
        Assert.notNull(node, "node cannot be null");
        Assert.notNull(cdpCacheIfIndex, "cdpCacheIfIndex cannot be null");
        Assert.notNull(cdpCacheDeviceIndex, "cdpCacheDeviceIndex cannot be null");
        return findUnique(
                "SELECT c FROM CdpLink c WHERE c.node = ?1 AND c.cdpCacheIfIndex = ?2 AND c.cdpCacheDeviceIndex = ?3",
                node, cdpCacheIfIndex, cdpCacheDeviceIndex);
    }

    public CdpLink get(Integer nodeId, Integer cdpCacheIfIndex, Integer cdpCacheDeviceIndex) {
        Assert.notNull(nodeId, "nodeId cannot be null");
        Assert.notNull(cdpCacheIfIndex, "cdpCacheIfIndex cannot be null");
        Assert.notNull(cdpCacheDeviceIndex, "cdpCacheDeviceIndex cannot be null");
        return findUnique(
                "SELECT c FROM CdpLink c WHERE c.node.id = ?1 AND c.cdpCacheIfIndex = ?2 AND c.cdpCacheDeviceIndex = ?3",
                nodeId, cdpCacheIfIndex, cdpCacheDeviceIndex);
    }

    public List<CdpLink> findByNodeId(Integer nodeId) {
        Assert.notNull(nodeId, "nodeId cannot be null");
        return find("SELECT c FROM CdpLink c WHERE c.node.id = ?1", nodeId);
    }

    public void deleteByNodeIdOlderThen(Integer nodeId, Date now) {
        entityManager().createQuery(
                "DELETE FROM CdpLink c WHERE c.node.id = ?1 AND c.cdpLinkLastPollTime < ?2")
                .setParameter(1, nodeId)
                .setParameter(2, now)
                .executeUpdate();
    }

    public void deleteByNodeId(Integer nodeId) {
        entityManager().createQuery("DELETE FROM CdpLink c WHERE c.node.id = ?1")
                .setParameter(1, nodeId)
                .executeUpdate();
    }

    public void deleteAll() {
        entityManager().createQuery("DELETE FROM CdpLink").executeUpdate();
    }
}
