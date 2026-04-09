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
import java.util.List;

import org.deltav.core.daemon.common.AbstractDaoJpa;
import org.opennms.netmgt.dao.api.ApplicationDao;
import org.opennms.netmgt.dao.api.ApplicationStatus;
import org.opennms.netmgt.dao.api.MonitoredServiceStatusEntity;
import org.opennms.netmgt.dao.api.ServicePerspective;
import org.opennms.netmgt.model.OnmsApplication;
import org.opennms.netmgt.model.monitoringLocations.OnmsMonitoringLocation;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of {@link ApplicationDao}.
 *
 * <p>The {@link #findByName(String)} method uses HQL. The status and alarm
 * methods ({@link #getApplicationStatus()}, {@link #getAlarmStatus()},
 * {@link #getPerspectiveLocationsForService(int, InetAddress, String)},
 * {@link #getServicePerspectives()}) return empty lists as they serve the
 * REST API layer, not daemon use.</p>
 */
@Repository
@Transactional
public class ApplicationDaoJpa extends AbstractDaoJpa<OnmsApplication, Integer>
        implements ApplicationDao {

    public ApplicationDaoJpa() {
        super(OnmsApplication.class);
    }

    // ---- ApplicationDao methods ----

    @Override
    public OnmsApplication findByName(String label) {
        return findUnique(
                "SELECT app FROM OnmsApplication app WHERE app.name = ?1",
                label);
    }

    @Override
    public List<ApplicationStatus> getApplicationStatus() {
        // Application status calculation is for REST API, not daemon use.
        return Collections.emptyList();
    }

    @Override
    public List<ApplicationStatus> getApplicationStatus(List<OnmsApplication> applications) {
        // Application status calculation is for REST API, not daemon use.
        return Collections.emptyList();
    }

    @Override
    public List<MonitoredServiceStatusEntity> getAlarmStatus() {
        // Alarm status retrieval is for REST API, not daemon use.
        return Collections.emptyList();
    }

    @Override
    public List<MonitoredServiceStatusEntity> getAlarmStatus(List<OnmsApplication> applications) {
        // Alarm status retrieval is for REST API, not daemon use.
        return Collections.emptyList();
    }

    @Override
    public List<OnmsMonitoringLocation> getPerspectiveLocationsForService(int nodeId, InetAddress ipAddress,
            String serviceName) {
        // Perspective location lookup is for REST API, not daemon use.
        return Collections.emptyList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ServicePerspective> getServicePerspectives() {
        return (List<ServicePerspective>) entityManager().createQuery(
                "SELECT DISTINCT new org.opennms.netmgt.dao.api.ServicePerspective(service, perspectiveLocation) " +
                "FROM OnmsApplication AS application " +
                "INNER JOIN application.monitoredServices AS service " +
                "INNER JOIN application.perspectiveLocations AS perspectiveLocation")
                .getResultList();
    }
}
