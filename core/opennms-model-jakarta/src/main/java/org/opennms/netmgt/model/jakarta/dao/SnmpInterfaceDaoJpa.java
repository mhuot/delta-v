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

import java.util.Collection;
import java.util.List;

import org.opennms.core.daemon.common.AbstractDaoJpa;
import org.opennms.netmgt.dao.api.SnmpInterfaceDao;
import org.opennms.netmgt.model.OnmsCriteria;
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

    // ---- LegacyOnmsDao methods — not used by Provisiond ----

    @Override
    public List<OnmsSnmpInterface> findMatching(OnmsCriteria criteria) {
        throw new UnsupportedOperationException(
                "SnmpInterfaceDaoJpa.findMatching(OnmsCriteria) not implemented — not required by Provisiond");
    }

    @Override
    public int countMatching(OnmsCriteria onmsCrit) {
        throw new UnsupportedOperationException(
                "SnmpInterfaceDaoJpa.countMatching(OnmsCriteria) not implemented — not required by Provisiond");
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
