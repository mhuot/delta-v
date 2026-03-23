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
package org.opennms.netmgt.enlinkd.model.jakarta.dao;

import java.util.Date;
import java.util.List;

import org.opennms.core.daemon.common.AbstractDaoJpa;
import org.opennms.netmgt.enlinkd.model.jakarta.BridgeMacLink;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of BridgeMacLink data access.
 */
@Repository
@Transactional
public class BridgeMacLinkDaoJpa extends AbstractDaoJpa<BridgeMacLink, Integer> {

    public BridgeMacLinkDaoJpa() {
        super(BridgeMacLink.class);
    }

    public List<BridgeMacLink> findByNodeId(Integer id) {
        return find("SELECT r FROM BridgeMacLink r WHERE r.node.id = ?1", id);
    }

    public BridgeMacLink getByNodeIdBridgePortMac(Integer id, Integer port, String mac) {
        return findUnique(
                "SELECT r FROM BridgeMacLink r WHERE r.node.id = ?1 AND r.bridgePort = ?2 AND r.macAddress = ?3",
                id, port, mac);
    }

    public List<BridgeMacLink> findByNodeIdBridgePort(Integer id, Integer port) {
        return find(
                "SELECT r FROM BridgeMacLink r WHERE r.node.id = ?1 AND r.bridgePort = ?2",
                id, port);
    }

    public List<BridgeMacLink> findByMacAddress(String mac) {
        return find("SELECT r FROM BridgeMacLink r WHERE r.macAddress = ?1", mac);
    }

    public void deleteByNodeIdOlderThen(Integer nodeId, Date now) {
        entityManager().createQuery(
                "DELETE FROM BridgeMacLink r WHERE r.node.id = ?1 AND r.bridgeMacLinkLastPollTime < ?2")
                .setParameter(1, nodeId)
                .setParameter(2, now)
                .executeUpdate();
    }

    public void deleteByNodeId(Integer nodeId) {
        entityManager().createQuery("DELETE FROM BridgeMacLink r WHERE r.node.id = ?1")
                .setParameter(1, nodeId)
                .executeUpdate();
    }

    public void deleteAll() {
        entityManager().createQuery("DELETE FROM BridgeMacLink").executeUpdate();
    }
}
