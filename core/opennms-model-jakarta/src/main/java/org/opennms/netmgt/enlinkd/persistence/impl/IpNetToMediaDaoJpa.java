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
import java.util.Date;
import java.util.List;

import org.deltav.core.daemon.common.AbstractDaoJpa;
import org.opennms.netmgt.enlinkd.persistence.api.IpNetToMediaDao;
import org.opennms.netmgt.enlinkd.model.IpNetToMedia;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of IpNetToMedia data access.
 *
 * <p>Note: IpNetToMedia uses {@code sourceNode} instead of {@code node}
 * for the node relationship.</p>
 */
@Repository
@Transactional
public class IpNetToMediaDaoJpa extends AbstractDaoJpa<IpNetToMedia, Integer> implements IpNetToMediaDao {

    public IpNetToMediaDaoJpa() {
        super(IpNetToMedia.class);
    }

    public List<IpNetToMedia> findBySourceNodeId(Integer id) {
        return find("SELECT m FROM IpNetToMedia m WHERE m.sourceNode.id = ?1", id);
    }

    public IpNetToMedia getByNetAndPhysAddress(InetAddress netAddress, String physAddress) {
        return findUnique(
                "SELECT m FROM IpNetToMedia m WHERE m.netAddress = ?1 AND m.physAddress = ?2",
                netAddress, physAddress);
    }

    public void deleteBySourceNodeIdOlderThen(Integer nodeId, Date now) {
        // Original uses find-then-delete pattern for cascade; replicate with bulk delete
        for (IpNetToMedia elem : find(
                "SELECT m FROM IpNetToMedia m WHERE m.sourceNode.id = ?1 AND m.lastPollTime < ?2",
                nodeId, now)) {
            delete(elem);
        }
    }

    public void deleteBySourceNodeId(Integer nodeId) {
        for (IpNetToMedia elem : find(
                "SELECT m FROM IpNetToMedia m WHERE m.sourceNode.id = ?1", nodeId)) {
            delete(elem);
        }
    }

    public List<IpNetToMedia> findByPhysAddress(String physAddress) {
        return find("SELECT m FROM IpNetToMedia m WHERE m.physAddress = ?1", physAddress);
    }

    public List<IpNetToMedia> findByNetAddress(InetAddress netAddress) {
        return find("SELECT m FROM IpNetToMedia m WHERE m.netAddress = ?1", netAddress);
    }

    public List<IpNetToMedia> findByMacLinksOfNode(Integer nodeId) {
        return find(
                "SELECT m FROM IpNetToMedia m WHERE m.physAddress IN " +
                "(SELECT l.macAddress FROM BridgeMacLink l WHERE l.node.id = ?1)",
                nodeId);
    }

    public void deleteAll() {
        entityManager().createQuery("DELETE FROM IpNetToMedia").executeUpdate();
    }
}
