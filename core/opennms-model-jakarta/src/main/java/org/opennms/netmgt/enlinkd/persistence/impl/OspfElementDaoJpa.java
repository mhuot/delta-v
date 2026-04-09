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
import java.util.List;

import org.deltav.core.daemon.common.AbstractDaoJpa;
import org.opennms.netmgt.enlinkd.persistence.api.OspfElementDao;
import org.opennms.netmgt.enlinkd.model.OspfElement;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of OspfElement data access.
 */
@Repository
@Transactional
public class OspfElementDaoJpa extends AbstractDaoJpa<OspfElement, Integer> implements OspfElementDao {

    public OspfElementDaoJpa() {
        super(OspfElement.class);
    }

    public OspfElement findByNodeId(Integer id) {
        return findUnique("SELECT r FROM OspfElement r WHERE r.node.id = ?1", id);
    }

    public OspfElement findByRouterId(InetAddress routerId) {
        return findUnique("SELECT r FROM OspfElement r WHERE r.ospfRouterId = ?1", routerId);
    }

    public List<OspfElement> findAllByRouterId(InetAddress routerId) {
        return find("SELECT r FROM OspfElement r WHERE r.ospfRouterId = ?1", routerId);
    }

    public List<OspfElement> findByRouterIdOfRelatedOspfLink(int nodeId) {
        return find(
                "SELECT r FROM OspfElement r WHERE r.ospfRouterId IN " +
                "(SELECT l.ospfRemRouterId FROM OspfLink l WHERE l.node.id = ?1)",
                nodeId);
    }

    public void deleteByNodeId(Integer nodeId) {
        entityManager().createQuery("DELETE FROM OspfElement r WHERE r.node.id = ?1")
                .setParameter(1, nodeId)
                .executeUpdate();
    }

    public void deleteAll() {
        entityManager().createQuery("DELETE FROM OspfElement").executeUpdate();
    }
}
