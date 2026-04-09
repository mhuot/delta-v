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
import org.opennms.netmgt.enlinkd.persistence.api.LldpElementDao;
import org.opennms.core.utils.LldpUtils.LldpChassisIdSubType;
import org.opennms.netmgt.enlinkd.model.LldpElement;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of LldpElement data access.
 */
@Repository
@Transactional
public class LldpElementDaoJpa extends AbstractDaoJpa<LldpElement, Integer> implements LldpElementDao {

    public LldpElementDaoJpa() {
        super(LldpElement.class);
    }

    public LldpElement findByNodeId(Integer id) {
        return findUnique("SELECT r FROM LldpElement r WHERE r.node.id = ?1", id);
    }

    public List<LldpElement> findByChassisId(String chassisId, LldpChassisIdSubType type) {
        return find(
                "SELECT r FROM LldpElement r WHERE r.lldpChassisId = ?1 AND r.lldpChassisIdSubType = ?2",
                chassisId, type);
    }

    public List<LldpElement> findByChassisOfLldpLinksOfNode(int nodeId) {
        return find(
                "SELECT r FROM LldpElement r WHERE EXISTS " +
                "(SELECT l FROM LldpLink l WHERE r.lldpChassisId = l.lldpRemChassisId " +
                "AND r.lldpChassisIdSubType = l.lldpRemChassisIdSubType AND l.node.id = ?1)",
                nodeId);
    }

    public LldpElement findBySysname(String sysname) {
        return findUnique("SELECT r FROM LldpElement r WHERE r.lldpSysname = ?1", sysname);
    }

    public void deleteByNodeId(Integer nodeId) {
        entityManager().createQuery("DELETE FROM LldpElement r WHERE r.node.id = ?1")
                .setParameter(1, nodeId)
                .executeUpdate();
    }

    public void deleteAll() {
        entityManager().createQuery("DELETE FROM LldpElement").executeUpdate();
    }
}
