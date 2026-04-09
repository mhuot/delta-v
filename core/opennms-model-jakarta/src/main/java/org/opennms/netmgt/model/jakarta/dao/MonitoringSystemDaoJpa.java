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

import org.deltav.core.daemon.common.AbstractDaoJpa;
import org.opennms.netmgt.dao.api.MonitoringSystemDao;
import org.opennms.netmgt.model.OnmsMonitoringSystem;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of {@link MonitoringSystemDao}.
 *
 * <p>Provisiond only calls {@code get(systemId)} which is provided by
 * {@link AbstractDaoJpa}. Custom methods throw {@link UnsupportedOperationException}.</p>
 */
@Repository
@Transactional
public class MonitoringSystemDaoJpa extends AbstractDaoJpa<OnmsMonitoringSystem, String>
        implements MonitoringSystemDao {

    public MonitoringSystemDaoJpa() {
        super(OnmsMonitoringSystem.class);
    }

    // ---- MonitoringSystemDao methods — not used by Provisiond ----

    @Override
    public long getNumMonitoringSystems(String type) {
        throw new UnsupportedOperationException(
                "MonitoringSystemDaoJpa.getNumMonitoringSystems not implemented — not required by Provisiond");
    }

    @Override
    public OnmsMonitoringSystem getMainMonitoringSystem() {
        throw new UnsupportedOperationException(
                "MonitoringSystemDaoJpa.getMainMonitoringSystem not implemented — not required by Provisiond");
    }
}
