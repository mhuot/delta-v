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
