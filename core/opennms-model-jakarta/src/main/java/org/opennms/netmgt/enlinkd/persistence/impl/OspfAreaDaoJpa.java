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
import org.opennms.netmgt.enlinkd.persistence.api.OspfAreaDao;
import org.opennms.netmgt.enlinkd.model.OspfArea;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * JPA implementation of OspfArea data access.
 */
@Repository
@Transactional
public class OspfAreaDaoJpa extends AbstractDaoJpa<OspfArea, Integer> implements OspfAreaDao {

    public OspfAreaDaoJpa() {
        super(OspfArea.class);
    }

    public OspfArea get(Integer nodeId, InetAddress ospfAreaId) {
        return findUnique(
                "SELECT o FROM OspfArea o WHERE o.node.id = ?1 AND o.ospfAreaId = ?2",
                nodeId, ospfAreaId);
    }

    public List<OspfArea> findByNodeId(Integer nodeId) {
        Assert.notNull(nodeId, "nodeId cannot be null");
        return find("SELECT o FROM OspfArea o WHERE o.node.id = ?1", nodeId);
    }

    public void deleteByNodeIdOlderThen(Integer nodeId, Date now) {
        entityManager().createQuery(
                "DELETE FROM OspfArea o WHERE o.node.id = ?1 AND o.ospfAreaLastPollTime < ?2")
                .setParameter(1, nodeId)
                .setParameter(2, now)
                .executeUpdate();
    }

    public void deleteByNodeId(Integer nodeId) {
        entityManager().createQuery("DELETE FROM OspfArea o WHERE o.node.id = ?1")
                .setParameter(1, nodeId)
                .executeUpdate();
    }
}
