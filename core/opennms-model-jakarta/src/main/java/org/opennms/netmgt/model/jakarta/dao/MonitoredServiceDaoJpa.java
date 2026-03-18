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
import java.util.Set;

import org.opennms.core.daemon.common.AbstractDaoJpa;
import org.opennms.netmgt.dao.api.MonitoredServiceDao;
import org.opennms.netmgt.model.OnmsApplication;
import org.opennms.netmgt.model.OnmsCriteria;
import org.opennms.netmgt.model.OnmsMonitoredService;
import org.opennms.netmgt.model.ServiceSelector;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of {@link MonitoredServiceDao}.
 *
 * <p>Implements only the methods required by Provisiond. All other methods
 * throw {@link UnsupportedOperationException}.</p>
 */
@Repository
@Transactional
public class MonitoredServiceDaoJpa extends AbstractDaoJpa<OnmsMonitoredService, Integer>
        implements MonitoredServiceDao {

    public MonitoredServiceDaoJpa() {
        super(OnmsMonitoredService.class);
    }

    // ---- Methods used by Provisiond ----

    @Override
    public OnmsMonitoredService get(Integer nodeId, InetAddress ipAddress, String svcName) {
        return findUnique(
                "from OnmsMonitoredService ms where ms.ipInterface.node.id = ?1 " +
                "and ms.ipInterface.ipAddress = ?2 and ms.serviceType.name = ?3",
                nodeId, ipAddress, svcName);
    }

    // ---- LegacyOnmsDao methods — not used by Provisiond ----

    @Override
    public List<OnmsMonitoredService> findMatching(OnmsCriteria criteria) {
        throw new UnsupportedOperationException(
                "MonitoredServiceDaoJpa.findMatching(OnmsCriteria) not implemented — not required by Provisiond");
    }

    @Override
    public int countMatching(OnmsCriteria onmsCrit) {
        throw new UnsupportedOperationException(
                "MonitoredServiceDaoJpa.countMatching(OnmsCriteria) not implemented — not required by Provisiond");
    }

    // ---- MonitoredServiceDao methods — not used by Provisiond ----

    @Override
    public OnmsMonitoredService get(Integer nodeId, InetAddress ipAddress, Integer serviceId) {
        throw new UnsupportedOperationException(
                "MonitoredServiceDaoJpa.get(nodeId, ipAddress, serviceId) not implemented — not required by Provisiond");
    }

    @Override
    public OnmsMonitoredService get(Integer nodeId, InetAddress ipAddr, Integer ifIndex, Integer serviceId) {
        throw new UnsupportedOperationException(
                "MonitoredServiceDaoJpa.get(nodeId, ipAddr, ifIndex, serviceId) not implemented — not required by Provisiond");
    }

    @Override
    public List<OnmsMonitoredService> findByType(String typeName) {
        throw new UnsupportedOperationException(
                "MonitoredServiceDaoJpa.findByType not implemented — not required by Provisiond");
    }

    @Override
    public List<OnmsMonitoredService> findMatchingServices(ServiceSelector serviceSelector) {
        throw new UnsupportedOperationException(
                "MonitoredServiceDaoJpa.findMatchingServices not implemented — not required by Provisiond");
    }

    @Override
    public List<OnmsMonitoredService> findAllServices() {
        throw new UnsupportedOperationException(
                "MonitoredServiceDaoJpa.findAllServices not implemented — not required by Provisiond");
    }

    @Override
    public Set<OnmsMonitoredService> findByApplication(OnmsApplication application) {
        throw new UnsupportedOperationException(
                "MonitoredServiceDaoJpa.findByApplication not implemented — not required by Provisiond");
    }

    @Override
    public OnmsMonitoredService getPrimaryService(Integer nodeId, String svcName) {
        throw new UnsupportedOperationException(
                "MonitoredServiceDaoJpa.getPrimaryService not implemented — not required by Provisiond");
    }

    @Override
    public List<OnmsMonitoredService> findByNode(int nodeId) {
        throw new UnsupportedOperationException(
                "MonitoredServiceDaoJpa.findByNode not implemented — not required by Provisiond");
    }
}
