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
