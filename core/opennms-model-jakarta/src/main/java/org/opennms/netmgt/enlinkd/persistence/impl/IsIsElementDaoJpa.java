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
import org.opennms.netmgt.enlinkd.persistence.api.IsIsElementDao;
import org.opennms.netmgt.enlinkd.model.IsIsElement;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of IsIsElement data access.
 */
@Repository
@Transactional
public class IsIsElementDaoJpa extends AbstractDaoJpa<IsIsElement, Integer> implements IsIsElementDao {

    public IsIsElementDaoJpa() {
        super(IsIsElement.class);
    }

    public IsIsElement findByNodeId(Integer id) {
        return findUnique("SELECT r FROM IsIsElement r WHERE r.node.id = ?1", id);
    }

    public IsIsElement findByIsIsSysId(String isisSysId) {
        return findUnique("SELECT r FROM IsIsElement r WHERE r.isisSysID = ?1", isisSysId);
    }

    public List<IsIsElement> findBySysIdOfIsIsLinksOfNode(int nodeId) {
        return find(
                "SELECT r FROM IsIsElement r WHERE r.isisSysID IN " +
                "(SELECT l.isisISAdjNeighSysID FROM IsIsLink l WHERE l.node.id = ?1)",
                nodeId);
    }

    public void deleteByNodeId(Integer nodeId) {
        entityManager().createQuery("DELETE FROM IsIsElement r WHERE r.node.id = ?1")
                .setParameter(1, nodeId)
                .executeUpdate();
    }

    public void deleteAll() {
        entityManager().createQuery("DELETE FROM IsIsElement").executeUpdate();
    }
}
