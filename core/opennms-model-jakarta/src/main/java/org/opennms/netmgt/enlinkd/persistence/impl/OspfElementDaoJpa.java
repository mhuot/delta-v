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
import java.util.List;

import org.opennms.core.daemon.common.AbstractDaoJpa;
import org.opennms.netmgt.enlinkd.persistence.api.OspfElementDao;
import org.opennms.netmgt.enlinkd.model.OspfElement;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of OspfElement data access.
 */
@Repository
@Transactional
public class OspfElementDaoJpa extends AbstractDaoJpa<OspfElement, Integer> implements OspfElementDao {

    public OspfElementDaoJpa() {
        super(OspfElement.class);
    }

    public OspfElement findByNodeId(Integer id) {
        return findUnique("SELECT r FROM OspfElement r WHERE r.node.id = ?1", id);
    }

    public OspfElement findByRouterId(InetAddress routerId) {
        return findUnique("SELECT r FROM OspfElement r WHERE r.ospfRouterId = ?1", routerId);
    }

    public List<OspfElement> findAllByRouterId(InetAddress routerId) {
        return find("SELECT r FROM OspfElement r WHERE r.ospfRouterId = ?1", routerId);
    }

    public List<OspfElement> findByRouterIdOfRelatedOspfLink(int nodeId) {
        return find(
                "SELECT r FROM OspfElement r WHERE r.ospfRouterId IN " +
                "(SELECT l.ospfRemRouterId FROM OspfLink l WHERE l.node.id = ?1)",
                nodeId);
    }

    public void deleteByNodeId(Integer nodeId) {
        entityManager().createQuery("DELETE FROM OspfElement r WHERE r.node.id = ?1")
                .setParameter(1, nodeId)
                .executeUpdate();
    }

    public void deleteAll() {
        entityManager().createQuery("DELETE FROM OspfElement").executeUpdate();
    }
}
