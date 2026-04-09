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

import java.util.List;

import org.deltav.core.daemon.common.AbstractDaoJpa;
import org.opennms.netmgt.enlinkd.persistence.api.CdpElementDao;
import org.opennms.netmgt.enlinkd.model.CdpElement;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of CdpElement data access.
 */
@Repository
@Transactional
public class CdpElementDaoJpa extends AbstractDaoJpa<CdpElement, Integer> implements CdpElementDao {

    public CdpElementDaoJpa() {
        super(CdpElement.class);
    }

    public CdpElement findByNodeId(Integer id) {
        return findUnique("SELECT r FROM CdpElement r WHERE r.node.id = ?1", id);
    }

    public CdpElement findByGlobalDeviceId(String deviceId) {
        List<CdpElement> elements = find(
                "SELECT r FROM CdpElement r WHERE r.cdpGlobalDeviceId = ?1 ORDER BY r.id",
                deviceId);
        if (elements.size() > 1) {
            LoggerFactory.getLogger(getClass()).warn(
                    "Expected 1 CdpElement for device with id '{}' but found {}. Using CdpElement {} and ignoring others.",
                    deviceId, elements.size(), elements.get(0));
        }
        return elements.isEmpty() ? null : elements.get(0);
    }

    public List<CdpElement> findByCacheDeviceIdOfCdpLinksOfNode(int nodeId) {
        return find(
                "SELECT r FROM CdpElement r WHERE r.cdpGlobalDeviceId IN " +
                "(SELECT l.cdpCacheDeviceId FROM CdpLink l WHERE l.node.id = ?1)",
                nodeId);
    }

    public void deleteByNodeId(Integer nodeId) {
        entityManager().createQuery("DELETE FROM CdpElement r WHERE r.node.id = ?1")
                .setParameter(1, nodeId)
                .executeUpdate();
    }

    public void deleteAll() {
        entityManager().createQuery("DELETE FROM CdpElement").executeUpdate();
    }
}
