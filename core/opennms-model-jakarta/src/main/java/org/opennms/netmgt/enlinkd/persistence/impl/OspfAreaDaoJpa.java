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

import org.opennms.core.daemon.common.AbstractDaoJpa;
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
