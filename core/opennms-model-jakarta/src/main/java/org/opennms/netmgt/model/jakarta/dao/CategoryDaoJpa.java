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

import java.util.List;

import org.hibernate.criterion.Criterion;
import org.deltav.core.daemon.common.AbstractDaoJpa;
import org.opennms.netmgt.dao.api.CategoryDao;
import org.opennms.netmgt.model.OnmsCategory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of {@link CategoryDao}.
 *
 * <p>Implements only the methods required by Provisiond. All other methods
 * throw {@link UnsupportedOperationException}.</p>
 */
@Repository
@Transactional
public class CategoryDaoJpa extends AbstractDaoJpa<OnmsCategory, Integer> implements CategoryDao {

    public CategoryDaoJpa() {
        super(OnmsCategory.class);
    }

    // ---- Methods used by Provisiond ----

    @Override
    public OnmsCategory findByName(String name) {
        return findUnique("from OnmsCategory c where c.name = ?1", name);
    }

    @Override
    public OnmsCategory findByName(String name, boolean useCaching) {
        // No caching in JPA implementation — delegate to findByName
        return findByName(name);
    }

    // ---- CategoryDao methods — not used by Provisiond ----

    @Override
    public List<String> getAllCategoryNames() {
        throw new UnsupportedOperationException(
                "CategoryDaoJpa.getAllCategoryNames not implemented — not required by Provisiond");
    }

    @Override
    public List<Criterion> getCriterionForCategorySetsUnion(String[]... categories) {
        throw new UnsupportedOperationException(
                "CategoryDaoJpa.getCriterionForCategorySetsUnion not implemented — not required by Provisiond");
    }

    @Override
    public List<OnmsCategory> getCategoriesWithAuthorizedGroup(String groupName) {
        throw new UnsupportedOperationException(
                "CategoryDaoJpa.getCategoriesWithAuthorizedGroup not implemented — not required by Provisiond");
    }
}
