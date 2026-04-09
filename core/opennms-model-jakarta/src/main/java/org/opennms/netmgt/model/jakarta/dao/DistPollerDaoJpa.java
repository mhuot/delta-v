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
import org.opennms.netmgt.dao.api.DistPollerDao;
import org.opennms.netmgt.model.OnmsDistPoller;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of {@link DistPollerDao}.
 *
 * <p>{@link #whoami()} returns the {@link OnmsDistPoller} representing the local OpenNMS system.
 * {@link OnmsDistPoller} uses {@code @DiscriminatorValue("OpenNMS")}, so any JPQL query against
 * {@code OnmsDistPoller} is already filtered to rows whose {@code type} column equals
 * {@code "OpenNMS"}. This mirrors the logic in {@code DistPollerDaoHibernate}.</p>
 */
@Repository
@Transactional
public class DistPollerDaoJpa extends AbstractDaoJpa<OnmsDistPoller, String> implements DistPollerDao {

    public DistPollerDaoJpa() {
        super(OnmsDistPoller.class);
    }

    /**
     * Returns the local OpenNMS monitoring system identity.
     *
     * <p>There is normally exactly one {@link OnmsDistPoller} row — the local system.
     * Returns the first result, or {@code null} if the table has not been initialised yet.</p>
     *
     * @return the local {@link OnmsDistPoller}, or {@code null} if not yet provisioned
     */
    @Override
    public OnmsDistPoller whoami() {
        return findUnique("SELECT d FROM OnmsDistPoller d");
    }
}
