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

import java.util.Collection;
import java.util.List;

import org.deltav.core.daemon.common.AbstractDaoJpa;
import org.opennms.netmgt.dao.api.SnmpInterfaceDao;
import org.opennms.netmgt.model.OnmsSnmpInterface;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of {@link SnmpInterfaceDao}.
 *
 * <p>Implements only the methods required by Provisiond. All other methods
 * throw {@link UnsupportedOperationException}.</p>
 */
@Repository
@Transactional
public class SnmpInterfaceDaoJpa extends AbstractDaoJpa<OnmsSnmpInterface, Integer> implements SnmpInterfaceDao {

    public SnmpInterfaceDaoJpa() {
        super(OnmsSnmpInterface.class);
    }

    // ---- Methods used by Provisiond ----

    @Override
    public OnmsSnmpInterface findByNodeIdAndIfIndex(Integer nodeId, Integer ifIndex) {
        return findUnique(
                "from OnmsSnmpInterface si where si.node.id = ?1 and si.ifIndex = ?2",
                nodeId, ifIndex);
    }

    // ---- SnmpInterfaceDao methods — not used by Provisiond ----

    @Override
    public List<OnmsSnmpInterface> findByNodeId(Integer nodeId) {
        throw new UnsupportedOperationException(
                "SnmpInterfaceDaoJpa.findByNodeId not implemented — not required by Provisiond");
    }

    @Override
    public List<OnmsSnmpInterface> findByMacLinksOfNode(Integer nodeId) {
        throw new UnsupportedOperationException(
                "SnmpInterfaceDaoJpa.findByMacLinksOfNode not implemented — not required by Provisiond");
    }

    @Override
    public List<OnmsSnmpInterface> findBySnpaAddressOfRelatedIsIsLink(int nodeId) {
        throw new UnsupportedOperationException(
                "SnmpInterfaceDaoJpa.findBySnpaAddressOfRelatedIsIsLink not implemented — not required by Provisiond");
    }

    @Override
    public OnmsSnmpInterface findByForeignKeyAndIfIndex(String foreignSource, String foreignId, Integer ifIndex) {
        throw new UnsupportedOperationException(
                "SnmpInterfaceDaoJpa.findByForeignKeyAndIfIndex not implemented — not required by Provisiond");
    }

    @Override
    public OnmsSnmpInterface findByNodeIdAndDescription(Integer nodeId, String description) {
        throw new UnsupportedOperationException(
                "SnmpInterfaceDaoJpa.findByNodeIdAndDescription not implemented — not required by Provisiond");
    }

    @Override
    public void markHavingIngressFlows(Integer nodeId, Collection<Integer> ingressSnmpIfIndexes) {
        throw new UnsupportedOperationException(
                "SnmpInterfaceDaoJpa.markHavingIngressFlows not implemented — not required by Provisiond");
    }

    @Override
    public void markHavingEgressFlows(Integer nodeId, Collection<Integer> egressSnmpIfIndexes) {
        throw new UnsupportedOperationException(
                "SnmpInterfaceDaoJpa.markHavingEgressFlows not implemented — not required by Provisiond");
    }

    @Override
    public List<OnmsSnmpInterface> findAllHavingFlows(Integer nodeId) {
        throw new UnsupportedOperationException(
                "SnmpInterfaceDaoJpa.findAllHavingFlows not implemented — not required by Provisiond");
    }

    @Override
    public List<OnmsSnmpInterface> findAllHavingIngressFlows(Integer nodeId) {
        throw new UnsupportedOperationException(
                "SnmpInterfaceDaoJpa.findAllHavingIngressFlows not implemented — not required by Provisiond");
    }

    @Override
    public List<OnmsSnmpInterface> findAllHavingEgressFlows(Integer nodeId) {
        throw new UnsupportedOperationException(
                "SnmpInterfaceDaoJpa.findAllHavingEgressFlows not implemented — not required by Provisiond");
    }

    @Override
    public long getNumInterfacesWithFlows() {
        throw new UnsupportedOperationException(
                "SnmpInterfaceDaoJpa.getNumInterfacesWithFlows not implemented — not required by Provisiond");
    }
}
