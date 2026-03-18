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

import org.opennms.core.daemon.common.AbstractDaoJpa;
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
