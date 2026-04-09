/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
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
package org.deltav.core.daemon.common;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AbstractDaoJpaTest {

    // Minimal concrete subclass for testing non-EntityManager methods
    static class TestDao extends AbstractDaoJpa<String, Integer> {
        TestDao() {
            super(String.class);
        }
    }

    @Test
    void findMatchingRequiresEntityManager() {
        var dao = new TestDao();
        // Without a Spring context, entityManager is null — the converter constructor
        // receives null and NPEs. This confirms findMatching() now delegates to
        // JpaCriteriaConverter instead of throwing UnsupportedOperationException.
        assertThatThrownBy(() -> dao.findMatching(new org.opennms.core.criteria.Criteria(String.class)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void countMatchingRequiresEntityManager() {
        var dao = new TestDao();
        assertThatThrownBy(() -> dao.countMatching(new org.opennms.core.criteria.Criteria(String.class)))
                .isInstanceOf(NullPointerException.class);
    }
}
