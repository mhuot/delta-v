/*
 * Licensed to The OpenNMS Group, Inc (TOG) under one or more
 * contributor license agreements.  See the LICENSE.md file
 * distributed with this work for additional information
 * regarding copyright ownership.
 *
 * TOG licenses this file to You under the GNU Affero General
 * Public License Version 3 (the "License") or (at your option)
 * any later version.  You may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at:
 *
 *      https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */
package org.opennms.netmgt.bsm.dao;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.opennms.core.daemon.common.AbstractDaoJpa;
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
