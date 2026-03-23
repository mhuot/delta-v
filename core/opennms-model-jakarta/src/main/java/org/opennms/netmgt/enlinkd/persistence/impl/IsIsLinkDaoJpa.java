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

import org.opennms.core.daemon.common.AbstractDaoJpa;
import org.opennms.netmgt.enlinkd.persistence.api.IsIsLinkDao;
import org.opennms.netmgt.enlinkd.model.IsIsLink;
import org.opennms.netmgt.model.OnmsNode;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * JPA implementation of IsIsLink data access.
 */
@Repository
@Transactional
public class IsIsLinkDaoJpa extends AbstractDaoJpa<IsIsLink, Integer> implements IsIsLinkDao {

    public IsIsLinkDaoJpa() {
        super(IsIsLink.class);
    }

    public IsIsLink get(OnmsNode node, Integer isisCircIndex, Integer isisISAdjIndex) {
        return findUnique(
                "SELECT i FROM IsIsLink i WHERE i.node = ?1 AND i.isisCircIndex = ?2 AND i.isisISAdjIndex = ?3",
                node, isisCircIndex, isisISAdjIndex);
    }

    public IsIsLink get(Integer nodeId, Integer isisCircIndex, Integer isisISAdjIndex) {
        Assert.notNull(nodeId, "nodeId cannot be null");
        Assert.notNull(isisCircIndex, "isisCircIndex cannot be null");
        Assert.notNull(isisISAdjIndex, "isisISAdjIndex cannot be null");
        return findUnique(
                "SELECT i FROM IsIsLink i WHERE i.node.id = ?1 AND i.isisCircIndex = ?2 AND i.isisISAdjIndex = ?3",
                nodeId, isisCircIndex, isisISAdjIndex);
    }

    public List<IsIsLink> findByNodeId(Integer nodeId) {
        Assert.notNull(nodeId, "nodeId cannot be null");
        return find("SELECT i FROM IsIsLink i WHERE i.node.id = ?1", nodeId);
    }

    public List<IsIsLink> findBySysIdAndAdjAndCircIndex(int nodeId) {
        return find(
                "SELECT r FROM IsIsLink r WHERE EXISTS " +
                "(SELECT e FROM IsIsElement e, IsIsLink l WHERE " +
                "r.node.id = e.node.id AND r.isisISAdjIndex = l.isisISAdjIndex " +
                "AND r.isisCircIndex = l.isisCircIndex AND e.isisSysID = l.isisISAdjNeighSysID " +
                "AND l.node.id = ?1)",
                nodeId);
    }

    public void deleteByNodeIdOlderThen(Integer nodeId, Date now) {
        entityManager().createQuery(
                "DELETE FROM IsIsLink i WHERE i.node.id = ?1 AND i.isisLinkLastPollTime < ?2")
                .setParameter(1, nodeId)
                .setParameter(2, now)
                .executeUpdate();
    }

    public void deleteByNodeId(Integer nodeId) {
        entityManager().createQuery("DELETE FROM IsIsLink i WHERE i.node.id = ?1")
                .setParameter(1, nodeId)
                .executeUpdate();
    }

    public void deleteAll() {
        entityManager().createQuery("DELETE FROM IsIsLink").executeUpdate();
    }
}
