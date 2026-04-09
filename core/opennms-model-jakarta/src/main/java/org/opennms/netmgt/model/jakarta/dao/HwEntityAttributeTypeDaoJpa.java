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
import org.opennms.netmgt.dao.api.HwEntityAttributeTypeDao;
import org.opennms.netmgt.model.HwEntityAttributeType;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of {@link HwEntityAttributeTypeDao}.
 *
 * <p>Implements only the methods required by Provisiond.</p>
 */
@Repository
@Transactional
public class HwEntityAttributeTypeDaoJpa extends AbstractDaoJpa<HwEntityAttributeType, Integer>
        implements HwEntityAttributeTypeDao {

    public HwEntityAttributeTypeDaoJpa() {
        super(HwEntityAttributeType.class);
    }

    // ---- Methods used by Provisiond ----

    @Override
    public HwEntityAttributeType findTypeByName(String name) {
        return findUnique("from HwEntityAttributeType t where t.name = ?1", name);
    }

    @Override
    public HwEntityAttributeType findTypeByOid(String oid) {
        return findUnique("from HwEntityAttributeType t where t.oid = ?1", oid);
    }
}
