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
import org.opennms.netmgt.dao.api.HwEntityDao;
import org.opennms.netmgt.model.OnmsHwEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of {@link HwEntityDao}.
 *
 * <p>Implements only the methods required by Provisiond. All other methods
 * throw {@link UnsupportedOperationException}.</p>
 */
@Repository
@Transactional
public class HwEntityDaoJpa extends AbstractDaoJpa<OnmsHwEntity, Integer> implements HwEntityDao {

    public HwEntityDaoJpa() {
        super(OnmsHwEntity.class);
    }

    // ---- Methods used by Provisiond ----

    @Override
    public OnmsHwEntity findRootByNodeId(Integer nodeId) {
        return findUnique(
                "from OnmsHwEntity e where e.node.id = ?1 and e.parent is null",
                nodeId);
    }

    // ---- HwEntityDao methods — not used by Provisiond ----

    @Override
    public OnmsHwEntity findRootEntityByNodeId(Integer nodeId) {
        throw new UnsupportedOperationException(
                "HwEntityDaoJpa.findRootEntityByNodeId not implemented — not required by Provisiond");
    }

    @Override
    public OnmsHwEntity findEntityByIndex(Integer nodeId, Integer entPhysicalIndex) {
        throw new UnsupportedOperationException(
                "HwEntityDaoJpa.findEntityByIndex not implemented — not required by Provisiond");
    }

    @Override
    public OnmsHwEntity findEntityByName(Integer nodeId, String entPhysicalName) {
        throw new UnsupportedOperationException(
                "HwEntityDaoJpa.findEntityByName not implemented — not required by Provisiond");
    }

    @Override
    public String getAttributeValue(Integer nodeId, Integer entPhysicalIndex, String attributeName) {
        throw new UnsupportedOperationException(
                "HwEntityDaoJpa.getAttributeValue(nodeId, index, name) not implemented — not required by Provisiond");
    }

    @Override
    public String getAttributeValue(Integer nodeId, String nameSource, String attributeName) {
        throw new UnsupportedOperationException(
                "HwEntityDaoJpa.getAttributeValue(nodeId, nameSource, name) not implemented — not required by Provisiond");
    }
}
