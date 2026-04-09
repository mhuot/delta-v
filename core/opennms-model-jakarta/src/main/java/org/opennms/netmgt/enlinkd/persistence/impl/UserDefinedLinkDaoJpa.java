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
package org.opennms.netmgt.enlinkd.persistence.impl;

import java.util.List;

import org.deltav.core.daemon.common.AbstractDaoJpa;
import org.opennms.netmgt.enlinkd.persistence.api.UserDefinedLinkDao;
import org.opennms.netmgt.enlinkd.model.UserDefinedLink;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of UserDefinedLink data access.
 */
@Repository
@Transactional
public class UserDefinedLinkDaoJpa extends AbstractDaoJpa<UserDefinedLink, Integer> implements UserDefinedLinkDao {

    public UserDefinedLinkDaoJpa() {
        super(UserDefinedLink.class);
    }

    public List<UserDefinedLink> getOutLinks(int nodeIdA) {
        return find("SELECT u FROM UserDefinedLink u WHERE u.nodeIdA = ?1", nodeIdA);
    }

    public List<UserDefinedLink> getInLinks(int nodeIdZ) {
        return find("SELECT u FROM UserDefinedLink u WHERE u.nodeIdZ = ?1", nodeIdZ);
    }

    public List<UserDefinedLink> getLinksWithLabel(String label) {
        return find("SELECT u FROM UserDefinedLink u WHERE u.linkLabel = ?1", label);
    }
}
