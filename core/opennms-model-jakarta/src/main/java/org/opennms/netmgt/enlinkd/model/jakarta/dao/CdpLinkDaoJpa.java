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
import org.opennms.netmgt.enlinkd.model.jakarta.CdpLink;
import org.opennms.netmgt.model.OnmsNode;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * JPA implementation of CdpLink data access.
 */
@Repository
@Transactional
public class CdpLinkDaoJpa extends AbstractDaoJpa<CdpLink, Integer> {

    public CdpLinkDaoJpa() {
        super(CdpLink.class);
    }

    public CdpLink get(OnmsNode node, Integer cdpCacheIfIndex, Integer cdpCacheDeviceIndex) {
        Assert.notNull(node, "node cannot be null");
        Assert.notNull(cdpCacheIfIndex, "cdpCacheIfIndex cannot be null");
        Assert.notNull(cdpCacheDeviceIndex, "cdpCacheDeviceIndex cannot be null");
        return findUnique(
                "SELECT c FROM CdpLink c WHERE c.node = ?1 AND c.cdpCacheIfIndex = ?2 AND c.cdpCacheDeviceIndex = ?3",
                node, cdpCacheIfIndex, cdpCacheDeviceIndex);
    }

    public CdpLink get(Integer nodeId, Integer cdpCacheIfIndex, Integer cdpCacheDeviceIndex) {
        Assert.notNull(nodeId, "nodeId cannot be null");
        Assert.notNull(cdpCacheIfIndex, "cdpCacheIfIndex cannot be null");
        Assert.notNull(cdpCacheDeviceIndex, "cdpCacheDeviceIndex cannot be null");
        return findUnique(
                "SELECT c FROM CdpLink c WHERE c.node.id = ?1 AND c.cdpCacheIfIndex = ?2 AND c.cdpCacheDeviceIndex = ?3",
                nodeId, cdpCacheIfIndex, cdpCacheDeviceIndex);
    }

    public List<CdpLink> findByNodeId(Integer nodeId) {
        Assert.notNull(nodeId, "nodeId cannot be null");
        return find("SELECT c FROM CdpLink c WHERE c.node.id = ?1", nodeId);
    }

    public void deleteByNodeIdOlderThen(Integer nodeId, Date now) {
        entityManager().createQuery(
                "DELETE FROM CdpLink c WHERE c.node.id = ?1 AND c.cdpLinkLastPollTime < ?2")
                .setParameter(1, nodeId)
                .setParameter(2, now)
                .executeUpdate();
    }

    public void deleteByNodeId(Integer nodeId) {
        entityManager().createQuery("DELETE FROM CdpLink c WHERE c.node.id = ?1")
                .setParameter(1, nodeId)
                .executeUpdate();
    }

    public void deleteAll() {
        entityManager().createQuery("DELETE FROM CdpLink").executeUpdate();
    }
}
