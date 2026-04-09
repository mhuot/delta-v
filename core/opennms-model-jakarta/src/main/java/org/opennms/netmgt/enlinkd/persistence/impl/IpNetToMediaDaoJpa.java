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
import org.opennms.netmgt.enlinkd.persistence.api.IpNetToMediaDao;
import org.opennms.netmgt.enlinkd.model.IpNetToMedia;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of IpNetToMedia data access.
 *
 * <p>Note: IpNetToMedia uses {@code sourceNode} instead of {@code node}
 * for the node relationship.</p>
 */
@Repository
@Transactional
public class IpNetToMediaDaoJpa extends AbstractDaoJpa<IpNetToMedia, Integer> implements IpNetToMediaDao {

    public IpNetToMediaDaoJpa() {
        super(IpNetToMedia.class);
    }

    public List<IpNetToMedia> findBySourceNodeId(Integer id) {
        return find("SELECT m FROM IpNetToMedia m WHERE m.sourceNode.id = ?1", id);
    }

    public IpNetToMedia getByNetAndPhysAddress(InetAddress netAddress, String physAddress) {
        return findUnique(
                "SELECT m FROM IpNetToMedia m WHERE m.netAddress = ?1 AND m.physAddress = ?2",
                netAddress, physAddress);
    }

    public void deleteBySourceNodeIdOlderThen(Integer nodeId, Date now) {
        // Original uses find-then-delete pattern for cascade; replicate with bulk delete
        for (IpNetToMedia elem : find(
                "SELECT m FROM IpNetToMedia m WHERE m.sourceNode.id = ?1 AND m.lastPollTime < ?2",
                nodeId, now)) {
            delete(elem);
        }
    }

    public void deleteBySourceNodeId(Integer nodeId) {
        for (IpNetToMedia elem : find(
                "SELECT m FROM IpNetToMedia m WHERE m.sourceNode.id = ?1", nodeId)) {
            delete(elem);
        }
    }

    public List<IpNetToMedia> findByPhysAddress(String physAddress) {
        return find("SELECT m FROM IpNetToMedia m WHERE m.physAddress = ?1", physAddress);
    }

    public List<IpNetToMedia> findByNetAddress(InetAddress netAddress) {
        return find("SELECT m FROM IpNetToMedia m WHERE m.netAddress = ?1", netAddress);
    }

    public List<IpNetToMedia> findByMacLinksOfNode(Integer nodeId) {
        return find(
                "SELECT m FROM IpNetToMedia m WHERE m.physAddress IN " +
                "(SELECT l.macAddress FROM BridgeMacLink l WHERE l.node.id = ?1)",
                nodeId);
    }

    public void deleteAll() {
        entityManager().createQuery("DELETE FROM IpNetToMedia").executeUpdate();
    }
}
