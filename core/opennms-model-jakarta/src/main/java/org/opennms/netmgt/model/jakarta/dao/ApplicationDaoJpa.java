/*
 * Copyright (C) 1999-2024 The OpenNMS Group, Inc.
 * Copyright (C) 2026 BeaconStrategists, Inc. (Modifications)
 *
 * This file is part of OpenNMS(R) / Delta-V.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
