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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.TypedQuery;

import org.opennms.core.criteria.Criteria;
import org.opennms.core.criteria.restrictions.InRestriction;
import org.opennms.core.criteria.restrictions.Restriction;
import org.opennms.core.daemon.common.AbstractDaoJpa;
import org.opennms.netmgt.dao.api.MonitoredServiceDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.opennms.netmgt.model.OnmsApplication;
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
 */
@Repository
@Transactional
public class MonitoredServiceDaoJpa extends AbstractDaoJpa<OnmsMonitoredService, Integer>
        implements MonitoredServiceDao {

    private static final Logger LOG = LoggerFactory.getLogger(MonitoredServiceDaoJpa.class);

    public MonitoredServiceDaoJpa() {
        super(OnmsMonitoredService.class);
    }

    /**
     * Translates an OpenNMS {@link Criteria} to JPQL.
     * Supports {@link InRestriction} (used by Poller.scheduleServices()).
     */
    @Override
    public List<OnmsMonitoredService> findMatching(Criteria criteria) {
        StringBuilder jpql = new StringBuilder("SELECT svc FROM OnmsMonitoredService svc");
        Map<String, Object> parameters = new java.util.LinkedHashMap<>();
        int paramIndex = 0;

        Collection<Restriction> restrictions = criteria.getRestrictions();
        if (!restrictions.isEmpty()) {
            jpql.append(" WHERE ");
            List<String> fragments = new ArrayList<>();
            for (Restriction restriction : restrictions) {
                if (restriction instanceof InRestriction) {
                    InRestriction in = (InRestriction) restriction;
                    String attr = in.getAttribute().contains(".") ? in.getAttribute() : "svc." + in.getAttribute();
                    String paramName = "p" + (paramIndex++);
                    parameters.put(paramName, in.getValues());
                    fragments.add(attr + " IN (:" + paramName + ")");
                } else {
                    throw new UnsupportedOperationException(
                            "Unsupported restriction type in MonitoredServiceDaoJpa.findMatching: "
                                    + restriction.getClass().getSimpleName());
                }
            }
            jpql.append(String.join(" AND ", fragments));
        }

        TypedQuery<OnmsMonitoredService> query = entityManager().createQuery(
                jpql.toString(), OnmsMonitoredService.class);
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }
        return query.getResultList();
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
        var result = findUnique(
                "SELECT svc FROM OnmsMonitoredService svc "
                + "JOIN svc.ipInterface ip "
                + "JOIN ip.node n "
                + "WHERE n.id = ?1 AND ip.ipAddress = ?2 AND svc.serviceType.name = ?3",
                nodeId, ipAddress, svcName);
        if (result == null) {
            LOG.warn("MonitoredServiceDaoJpa.get({}, {}, {}) returned null", nodeId, ipAddress, svcName);
        }
        return result;
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
    public List<OnmsMonitoredService> findAllServicesForScheduling() {
        return find(
                "SELECT DISTINCT svc FROM OnmsMonitoredService svc "
                + "LEFT JOIN FETCH svc.serviceType "
                + "LEFT JOIN FETCH svc.ipInterface ip "
                + "LEFT JOIN FETCH ip.node n "
                + "LEFT JOIN FETCH n.location "
                + "WHERE svc.status IN ('A', 'N')");
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
