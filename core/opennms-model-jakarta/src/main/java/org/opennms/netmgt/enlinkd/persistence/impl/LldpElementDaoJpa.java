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
