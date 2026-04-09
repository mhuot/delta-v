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

import java.util.Date;
import java.util.List;

import org.deltav.core.daemon.common.AbstractDaoJpa;
import org.opennms.netmgt.enlinkd.persistence.api.BridgeElementDao;
import org.opennms.netmgt.enlinkd.model.BridgeElement;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of BridgeElement data access.
 *
 * <p>Note: {@link #findByNodeId(Integer)} returns a List because a node
 * can have multiple bridge elements (one per VLAN).</p>
 */
@Repository
@Transactional
public class BridgeElementDaoJpa extends AbstractDaoJpa<BridgeElement, Integer> implements BridgeElementDao {

    public BridgeElementDaoJpa() {
        super(BridgeElement.class);
    }

    public List<BridgeElement> findByNodeId(Integer id) {
        return find("SELECT r FROM BridgeElement r WHERE r.node.id = ?1", id);
    }

    public BridgeElement getByNodeIdVlan(Integer id, Integer vlanId) {
        if (vlanId == null) {
            return findUnique(
                    "SELECT r FROM BridgeElement r WHERE r.node.id = ?1 AND r.vlan IS NULL",
                    id);
        }
        return findUnique(
                "SELECT r FROM BridgeElement r WHERE r.node.id = ?1 AND r.vlan = ?2",
                id, vlanId);
    }

    public List<BridgeElement> findByBridgeId(String id) {
        return find("SELECT r FROM BridgeElement r WHERE r.baseBridgeAddress = ?1", id);
    }

    public BridgeElement getByBridgeIdVlan(String id, Integer vlanId) {
        return findUnique(
                "SELECT r FROM BridgeElement r WHERE r.baseBridgeAddress = ?1 AND r.vlan = ?2",
                id, vlanId);
    }

    public void deleteByNodeIdOlderThen(Integer nodeId, Date now) {
        entityManager().createQuery(
                "DELETE FROM BridgeElement r WHERE r.node.id = ?1 AND r.bridgeNodeLastPollTime < ?2")
                .setParameter(1, nodeId)
                .setParameter(2, now)
                .executeUpdate();
    }

    public void deleteByNodeId(Integer nodeId) {
        entityManager().createQuery("DELETE FROM BridgeElement r WHERE r.node.id = ?1")
                .setParameter(1, nodeId)
                .executeUpdate();
    }

    public void deleteAll() {
        entityManager().createQuery("DELETE FROM BridgeElement").executeUpdate();
    }
}
