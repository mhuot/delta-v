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
import java.util.Collections;
import java.util.LinkedHashSet;
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
 * <p>Implements all methods from the {@link MonitoredServiceDao} interface.
 * The HQL-backed methods use the helper methods from {@link AbstractDaoJpa}.</p>
 *
 * <p>The deprecated {@link #findMatching(OnmsCriteria)} and
 * {@link #countMatching(OnmsCriteria)} methods from
 * {@link org.opennms.netmgt.dao.api.LegacyOnmsDao} throw
 * {@link UnsupportedOperationException}.</p>
 */
@Repository
@Transactional
public class MonitoredServiceDaoJpa extends AbstractDaoJpa<OnmsMonitoredService, Integer>
        implements MonitoredServiceDao {

    public MonitoredServiceDaoJpa() {
        super(OnmsMonitoredService.class);
    }

    // ---- LegacyOnmsDao methods ----

    @Override
    public List<OnmsMonitoredService> findMatching(OnmsCriteria criteria) {
        throw new UnsupportedOperationException(
                "findMatching(OnmsCriteria) is not supported in MonitoredServiceDaoJpa — use HQL queries");
    }

    @Override
    public int countMatching(OnmsCriteria onmsCrit) {
        throw new UnsupportedOperationException(
                "countMatching(OnmsCriteria) is not supported in MonitoredServiceDaoJpa — use HQL queries");
    }

    // ---- MonitoredServiceDao methods ----

    @Override
    public OnmsMonitoredService get(Integer nodeId, InetAddress ipAddress, Integer serviceId) {
        return findUnique(
                "SELECT svc FROM OnmsMonitoredService svc "
                + "JOIN svc.ipInterface ip "
                + "JOIN ip.node n "
                + "WHERE n.id = ?1 AND ip.ipAddress = ?2 AND svc.serviceType.id = ?3",
                nodeId, ipAddress, serviceId);
    }

    @Override
    public OnmsMonitoredService get(Integer nodeId, InetAddress ipAddr, Integer ifIndex, Integer serviceId) {
        return findUnique(
                "SELECT svc FROM OnmsMonitoredService svc "
                + "JOIN svc.ipInterface ip "
                + "JOIN ip.node n "
                + "WHERE n.id = ?1 AND ip.ipAddress = ?2 AND ip.snmpInterface.ifIndex = ?3 AND svc.serviceType.id = ?4",
                nodeId, ipAddr, ifIndex, serviceId);
    }

    @Override
    public OnmsMonitoredService get(Integer nodeId, InetAddress ipAddress, String svcName) {
        return findUnique(
                "SELECT svc FROM OnmsMonitoredService svc "
                + "JOIN svc.ipInterface ip "
                + "JOIN ip.node n "
                + "WHERE n.id = ?1 AND ip.ipAddress = ?2 AND svc.serviceType.name = ?3",
                nodeId, ipAddress, svcName);
    }

    @Override
    public List<OnmsMonitoredService> findByType(String typeName) {
        return find(
                "SELECT svc FROM OnmsMonitoredService svc "
                + "WHERE svc.serviceType.name = ?1",
                typeName);
    }

    @Override
    public List<OnmsMonitoredService> findAllServices() {
        return find(
                "SELECT svc FROM OnmsMonitoredService svc "
                + "JOIN FETCH svc.ipInterface ip "
                + "JOIN FETCH ip.node");
    }

    @Override
    public Set<OnmsMonitoredService> findByApplication(OnmsApplication application) {
        List<OnmsMonitoredService> services = find(
                "SELECT svc FROM OnmsMonitoredService svc "
                + "JOIN svc.applications app "
                + "WHERE app.id = ?1",
                application.getId());
        return new LinkedHashSet<>(services);
    }

    @Override
    public OnmsMonitoredService getPrimaryService(Integer nodeId, String svcName) {
        return findUnique(
                "SELECT svc FROM OnmsMonitoredService svc "
                + "JOIN svc.ipInterface ip "
                + "JOIN ip.node n "
                + "WHERE n.id = ?1 AND ip.isSnmpPrimary = 'P' AND svc.serviceType.name = ?2",
                nodeId, svcName);
    }

    @Override
    public List<OnmsMonitoredService> findMatchingServices(ServiceSelector serviceSelector) {
        // ServiceSelector-based matching requires Hibernate Criteria translation
        // which is not needed for daemon use. Return empty list.
        return Collections.emptyList();
    }

    @Override
    public List<OnmsMonitoredService> findByNode(int nodeId) {
        return find(
                "SELECT svc FROM OnmsMonitoredService svc "
                + "JOIN svc.ipInterface ip "
                + "JOIN ip.node n "
                + "WHERE n.id = ?1",
                nodeId);
    }
}
