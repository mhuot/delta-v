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

import java.net.InetAddress;
import java.util.Date;
import java.util.List;

import org.deltav.core.daemon.common.AbstractDaoJpa;
import org.opennms.netmgt.enlinkd.persistence.api.OspfLinkDao;
import org.opennms.netmgt.enlinkd.model.OspfLink;
import org.opennms.netmgt.model.OnmsNode;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * JPA implementation of OspfLink data access.
 */
@Repository
@Transactional
public class OspfLinkDaoJpa extends AbstractDaoJpa<OspfLink, Integer> implements OspfLinkDao {

    public OspfLinkDaoJpa() {
        super(OspfLink.class);
    }

    public OspfLink get(OnmsNode node, InetAddress ospfRemRouterId,
                        InetAddress ospfRemIpAddr, Integer ospfRemAddressLessIndex) {
        return findUnique(
                "SELECT o FROM OspfLink o WHERE o.node = ?1 AND o.ospfRemRouterId = ?2 " +
                "AND o.ospfRemIpAddr = ?3 AND o.ospfRemAddressLessIndex = ?4",
                node, ospfRemRouterId, ospfRemIpAddr, ospfRemAddressLessIndex);
    }

    public OspfLink get(Integer nodeId, InetAddress ospfRemRouterId,
                        InetAddress ospfRemIpAddr, Integer ospfRemAddressLessIndex) {
        Assert.notNull(nodeId, "nodeId cannot be null");
        Assert.notNull(ospfRemRouterId, "ospfRemRouterId cannot be null");
        Assert.notNull(ospfRemIpAddr, "ospfRemIpAddr cannot be null");
        Assert.notNull(ospfRemAddressLessIndex, "ospfRemAddressLessIndex cannot be null");
        return findUnique(
                "SELECT o FROM OspfLink o WHERE o.node.id = ?1 AND o.ospfRemRouterId = ?2 " +
                "AND o.ospfRemIpAddr = ?3 AND o.ospfRemAddressLessIndex = ?4",
                nodeId, ospfRemRouterId, ospfRemIpAddr, ospfRemAddressLessIndex);
    }

    public List<OspfLink> findByNodeId(Integer nodeId) {
        Assert.notNull(nodeId, "nodeId cannot be null");
        return find("SELECT o FROM OspfLink o WHERE o.node.id = ?1", nodeId);
    }

    public void deleteByNodeIdOlderThen(Integer nodeId, Date now) {
        entityManager().createQuery(
                "DELETE FROM OspfLink o WHERE o.node.id = ?1 AND o.ospfLinkLastPollTime < ?2")
                .setParameter(1, nodeId)
                .setParameter(2, now)
                .executeUpdate();
    }

    public void deleteByNodeId(Integer nodeId) {
        entityManager().createQuery("DELETE FROM OspfLink o WHERE o.node.id = ?1")
                .setParameter(1, nodeId)
                .executeUpdate();
    }

    public void deleteAll() {
        entityManager().createQuery("DELETE FROM OspfLink").executeUpdate();
    }
}
