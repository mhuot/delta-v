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
package org.opennms.netmgt.model.jakarta.dao;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;

import org.opennms.core.daemon.common.AbstractDaoJpa;
import org.opennms.core.utils.InetAddressUtils;
import org.opennms.netmgt.dao.api.IpInterfaceDao;
import org.opennms.netmgt.model.OnmsIpInterface;
import org.opennms.netmgt.model.OnmsNode;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of {@link IpInterfaceDao}.
 *
 * <p>Implements only the methods required by Provisiond. All other methods
 * throw {@link UnsupportedOperationException}.</p>
 */
@Repository
@Transactional
public class IpInterfaceDaoJpa extends AbstractDaoJpa<OnmsIpInterface, Integer> implements IpInterfaceDao {

    public IpInterfaceDaoJpa() {
        super(OnmsIpInterface.class);
    }

    // ---- Methods used by Provisiond ----

    @Override
    public OnmsIpInterface findByNodeIdAndIpAddress(Integer nodeId, String ipAddress) {
        return findUnique(
                "from OnmsIpInterface iface where iface.node.id = ?1 and iface.ipAddress = ?2",
                nodeId, InetAddressUtils.addr(ipAddress));
    }

    // ---- IpInterfaceDao methods — not used by Provisiond ----

    @Override
    public OnmsIpInterface get(OnmsNode node, String ipAddress) {
        throw new UnsupportedOperationException(
                "IpInterfaceDaoJpa.get(OnmsNode, String) not implemented — not required by Provisiond");
    }

    @Override
    public OnmsIpInterface findByForeignKeyAndIpAddress(String foreignSource, String foreignId, String ipAddress) {
        throw new UnsupportedOperationException(
                "IpInterfaceDaoJpa.findByForeignKeyAndIpAddress not implemented — not required by Provisiond");
    }

    @Override
    public List<OnmsIpInterface> findByIpAddress(String ipAddress) {
        return find("from OnmsIpInterface ipInterface where ipInterface.ipAddress = ?1",
                InetAddressUtils.addr(ipAddress));
    }

    @Override
    public List<OnmsIpInterface> findByNodeId(Integer nodeId) {
        throw new UnsupportedOperationException(
                "IpInterfaceDaoJpa.findByNodeId not implemented — not required by Provisiond");
    }

    @Override
    public List<OnmsIpInterface> findByMacLinksOfNode(Integer nodeId) {
        throw new UnsupportedOperationException(
                "IpInterfaceDaoJpa.findByMacLinksOfNode not implemented — not required by Provisiond");
    }

    @Override
    public List<OnmsIpInterface> findByServiceType(String svcName) {
        return entityManager().createQuery(
                "select distinct ipInterface from OnmsIpInterface as ipInterface " +
                "join ipInterface.monitoredServices as monSvc " +
                "where monSvc.serviceType.name = :svcName", OnmsIpInterface.class)
                .setParameter("svcName", svcName)
                .getResultList();
    }

    @Override
    public List<OnmsIpInterface> findHierarchyByServiceType(String svcName) {
        return entityManager().createQuery(
                "select distinct ipInterface from OnmsIpInterface as ipInterface " +
                "left join fetch ipInterface.node as node " +
                "left join fetch node.assetRecord " +
                "left join fetch ipInterface.node.snmpInterfaces as snmpIf " +
                "left join fetch snmpIf.ipInterfaces " +
                "join ipInterface.monitoredServices as monSvc " +
                "where monSvc.serviceType.name = :svcName", OnmsIpInterface.class)
                .setParameter("svcName", svcName)
                .getResultList();
    }

    @Override
    public Map<InetAddress, Integer> getInterfacesForNodes() {
        throw new UnsupportedOperationException(
                "IpInterfaceDaoJpa.getInterfacesForNodes not implemented — not required by Provisiond");
    }

    @Override
    public OnmsIpInterface findPrimaryInterfaceByNodeId(Integer nodeId) {
        throw new UnsupportedOperationException(
                "IpInterfaceDaoJpa.findPrimaryInterfaceByNodeId not implemented — not required by Provisiond");
    }

    @Override
    public List<OnmsIpInterface> findInterfacesWithMetadata(String context, String key, String value,
            boolean matchEnumeration) {
        throw new UnsupportedOperationException(
                "IpInterfaceDaoJpa.findInterfacesWithMetadata not implemented — not required by Provisiond");
    }

    @Override
    public List<OnmsIpInterface> findByIpAddressAndLocation(String address, String location) {
        throw new UnsupportedOperationException(
                "IpInterfaceDaoJpa.findByIpAddressAndLocation not implemented — not required by Provisiond");
    }
}
