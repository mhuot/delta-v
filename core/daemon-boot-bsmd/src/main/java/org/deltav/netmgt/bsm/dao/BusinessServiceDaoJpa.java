/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
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
package org.deltav.netmgt.bsm.dao;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.deltav.core.daemon.common.AbstractDaoJpa;
import org.opennms.netmgt.bsm.persistence.api.BusinessServiceDao;
import org.opennms.netmgt.bsm.persistence.api.BusinessServiceEdgeEntity;
import org.opennms.netmgt.bsm.persistence.api.BusinessServiceEntity;
import org.springframework.stereotype.Repository;

@Repository
public class BusinessServiceDaoJpa extends AbstractDaoJpa<BusinessServiceEntity, Long>
        implements BusinessServiceDao {

    public BusinessServiceDaoJpa() {
        super(BusinessServiceEntity.class);
    }

    @Override
    public Set<BusinessServiceEntity> findParents(BusinessServiceEntity child) {
        List<BusinessServiceEdgeEntity> edges = entityManager()
            .createQuery(
                "SELECT edge FROM BusinessServiceEdgeEntity edge " +
                "WHERE TYPE(edge) = BusinessServiceChildEdgeEntity " +
                "AND edge.child.id = :childId",
                BusinessServiceEdgeEntity.class)
            .setParameter("childId", child.getId())
            .getResultList();
        return edges.stream()
            .map(BusinessServiceEdgeEntity::getBusinessService)
            .collect(Collectors.toSet());
    }
}
