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
import org.opennms.netmgt.enlinkd.persistence.api.BridgeStpLinkDao;
import org.opennms.netmgt.enlinkd.model.BridgeStpLink;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of BridgeStpLink data access.
 */
@Repository
@Transactional
public class BridgeStpLinkDaoJpa extends AbstractDaoJpa<BridgeStpLink, Integer> implements BridgeStpLinkDao {

    public BridgeStpLinkDaoJpa() {
        super(BridgeStpLink.class);
    }

    public List<BridgeStpLink> findByNodeId(Integer id) {
        return find("SELECT r FROM BridgeStpLink r WHERE r.node.id = ?1", id);
    }

    public BridgeStpLink getByNodeIdBridgePort(Integer id, Integer port) {
        return findUnique(
                "SELECT r FROM BridgeStpLink r WHERE r.node.id = ?1 AND r.stpPort = ?2",
                id, port);
    }

    public List<BridgeStpLink> findByDesignatedBridge(String designated) {
        return find("SELECT r FROM BridgeStpLink r WHERE r.designatedBridge = ?1", designated);
    }

    public List<BridgeStpLink> findByDesignatedRoot(String root) {
        return find("SELECT r FROM BridgeStpLink r WHERE r.designatedRoot = ?1", root);
    }

    public void deleteByNodeIdOlderThen(Integer nodeId, Date now) {
        entityManager().createQuery(
                "DELETE FROM BridgeStpLink r WHERE r.node.id = ?1 AND r.bridgeStpLinkLastPollTime < ?2")
                .setParameter(1, nodeId)
                .setParameter(2, now)
                .executeUpdate();
    }

    public void deleteByNodeId(Integer nodeId) {
        entityManager().createQuery("DELETE FROM BridgeStpLink r WHERE r.node.id = ?1")
                .setParameter(1, nodeId)
                .executeUpdate();
    }

    public void deleteAll() {
        entityManager().createQuery("DELETE FROM BridgeStpLink").executeUpdate();
    }
}
