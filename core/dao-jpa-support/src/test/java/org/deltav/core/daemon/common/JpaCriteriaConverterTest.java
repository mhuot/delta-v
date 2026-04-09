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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import jakarta.persistence.Persistence;
import jakarta.persistence.Table;
import jakarta.persistence.TypedQuery;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opennms.core.criteria.Criteria;
import org.opennms.core.criteria.CriteriaBuilder;
import org.opennms.core.criteria.restrictions.Restrictions;

class JpaCriteriaConverterTest {

    @Entity
    @Table(name = "test_items")
    public static class TestItem {
        @Id
        private Integer id;
        private String name;
        private Integer priority;
        private String status;

        public TestItem() {}

        public TestItem(Integer id, String name, Integer priority, String status) {
            this.id = id;
            this.name = name;
            this.priority = priority;
            this.status = status;
        }

        public Integer getId() { return id; }
        public String getName() { return name; }
        public Integer getPriority() { return priority; }
        public String getStatus() { return status; }
    }

    private static EntityManagerFactory emf;
    private static EntityManager em;
    private JpaCriteriaConverter converter;

    @BeforeAll
    static void initDatabase() {
        emf = Persistence.createEntityManagerFactory("test-criteria");
        em = emf.createEntityManager();

        em.getTransaction().begin();
        em.persist(new TestItem(1, "Alpha", 1, "ACTIVE"));
        em.persist(new TestItem(2, "Beta", 2, "ACTIVE"));
        em.persist(new TestItem(3, "Gamma", 3, "INACTIVE"));
        em.persist(new TestItem(4, "Delta", 1, "INACTIVE"));
        em.persist(new TestItem(5, "Epsilon", 5, "ACTIVE"));
        em.getTransaction().commit();
    }

    @AfterAll
    static void closeDatabase() {
        if (em != null) em.close();
        if (emf != null) emf.close();
    }

    @BeforeEach
    void setUp() {
        converter = new JpaCriteriaConverter(em);
    }

    @Test
    void findAll() {
        Criteria criteria = new Criteria(TestItem.class);
        TypedQuery<TestItem> query = converter.convert(criteria, TestItem.class);
        List<TestItem> results = query.getResultList();
        assertThat(results).hasSize(5);
    }

    @Test
    void eqRestriction() {
        CriteriaBuilder cb = new CriteriaBuilder(TestItem.class);
        cb.eq("status", "ACTIVE");
        TypedQuery<TestItem> query = converter.convert(cb.toCriteria(), TestItem.class);
        List<TestItem> results = query.getResultList();
        assertThat(results).hasSize(3);
        assertThat(results).allMatch(i -> "ACTIVE".equals(i.getStatus()));
    }

    @Test
    void neRestriction() {
        CriteriaBuilder cb = new CriteriaBuilder(TestItem.class);
        cb.ne("status", "ACTIVE");
        TypedQuery<TestItem> query = converter.convert(cb.toCriteria(), TestItem.class);
        List<TestItem> results = query.getResultList();
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(i -> "INACTIVE".equals(i.getStatus()));
    }

    @Test
    void gtRestriction() {
        CriteriaBuilder cb = new CriteriaBuilder(TestItem.class);
        cb.gt("priority", 2);
        TypedQuery<TestItem> query = converter.convert(cb.toCriteria(), TestItem.class);
        List<TestItem> results = query.getResultList();
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(i -> i.getPriority() > 2);
    }

    @Test
    void geRestriction() {
        CriteriaBuilder cb = new CriteriaBuilder(TestItem.class);
        cb.ge("priority", 3);
        TypedQuery<TestItem> query = converter.convert(cb.toCriteria(), TestItem.class);
        List<TestItem> results = query.getResultList();
        assertThat(results).hasSize(2);
    }

    @Test
    void ltRestriction() {
        CriteriaBuilder cb = new CriteriaBuilder(TestItem.class);
        cb.lt("priority", 3);
        TypedQuery<TestItem> query = converter.convert(cb.toCriteria(), TestItem.class);
        List<TestItem> results = query.getResultList();
        assertThat(results).hasSize(3);
    }

    @Test
    void leRestriction() {
        CriteriaBuilder cb = new CriteriaBuilder(TestItem.class);
        cb.le("priority", 1);
        TypedQuery<TestItem> query = converter.convert(cb.toCriteria(), TestItem.class);
        List<TestItem> results = query.getResultList();
        assertThat(results).hasSize(2);
    }

    @Test
    void likeRestriction() {
        CriteriaBuilder cb = new CriteriaBuilder(TestItem.class);
        cb.like("name", "%lpha");
        TypedQuery<TestItem> query = converter.convert(cb.toCriteria(), TestItem.class);
        List<TestItem> results = query.getResultList();
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Alpha");
    }

    @Test
    void ilikeRestriction() {
        CriteriaBuilder cb = new CriteriaBuilder(TestItem.class);
        cb.ilike("name", "%ALPHA%");
        TypedQuery<TestItem> query = converter.convert(cb.toCriteria(), TestItem.class);
        List<TestItem> results = query.getResultList();
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Alpha");
    }

    @Test
    void inRestriction() {
        CriteriaBuilder cb = new CriteriaBuilder(TestItem.class);
        cb.in("name", Arrays.asList("Alpha", "Delta"));
        TypedQuery<TestItem> query = converter.convert(cb.toCriteria(), TestItem.class);
        List<TestItem> results = query.getResultList();
        assertThat(results).hasSize(2);
    }

    @Test
    void emptyInRestrictionReturnsNothing() {
        CriteriaBuilder cb = new CriteriaBuilder(TestItem.class);
        cb.in("name", List.of());
        TypedQuery<TestItem> query = converter.convert(cb.toCriteria(), TestItem.class);
        List<TestItem> results = query.getResultList();
        assertThat(results).isEmpty();
    }

    @Test
    void betweenRestriction() {
        CriteriaBuilder cb = new CriteriaBuilder(TestItem.class);
        cb.between("priority", 2, 4);
        TypedQuery<TestItem> query = converter.convert(cb.toCriteria(), TestItem.class);
        List<TestItem> results = query.getResultList();
        assertThat(results).hasSize(2);
    }

    @Test
    void isNullRestriction() {
        CriteriaBuilder cb = new CriteriaBuilder(TestItem.class);
        cb.isNull("status");
        TypedQuery<TestItem> query = converter.convert(cb.toCriteria(), TestItem.class);
        List<TestItem> results = query.getResultList();
        assertThat(results).isEmpty();
    }

    @Test
    void isNotNullRestriction() {
        CriteriaBuilder cb = new CriteriaBuilder(TestItem.class);
        cb.isNotNull("status");
        TypedQuery<TestItem> query = converter.convert(cb.toCriteria(), TestItem.class);
        List<TestItem> results = query.getResultList();
        assertThat(results).hasSize(5);
    }

    @Test
    void allRestrictionConjunction() {
        Criteria criteria = new Criteria(TestItem.class);
        criteria.addRestriction(Restrictions.all(
                Restrictions.eq("status", "ACTIVE"),
                Restrictions.gt("priority", 1)
        ));
        TypedQuery<TestItem> query = converter.convert(criteria, TestItem.class);
        List<TestItem> results = query.getResultList();
        assertThat(results).hasSize(2); // Beta(2,ACTIVE), Epsilon(5,ACTIVE)
    }

    @Test
    void anyRestrictionDisjunction() {
        Criteria criteria = new Criteria(TestItem.class);
        criteria.addRestriction(Restrictions.any(
                Restrictions.eq("name", "Alpha"),
                Restrictions.eq("name", "Gamma")
        ));
        TypedQuery<TestItem> query = converter.convert(criteria, TestItem.class);
        List<TestItem> results = query.getResultList();
        assertThat(results).hasSize(2);
    }

    @Test
    void notRestriction() {
        Criteria criteria = new Criteria(TestItem.class);
        criteria.addRestriction(Restrictions.not(Restrictions.eq("status", "ACTIVE")));
        TypedQuery<TestItem> query = converter.convert(criteria, TestItem.class);
        List<TestItem> results = query.getResultList();
        assertThat(results).hasSize(2);
    }

    @Test
    void orderByAscending() {
        CriteriaBuilder cb = new CriteriaBuilder(TestItem.class);
        cb.orderBy("name");
        TypedQuery<TestItem> query = converter.convert(cb.toCriteria(), TestItem.class);
        List<TestItem> results = query.getResultList();
        assertThat(results).hasSize(5);
        assertThat(results.get(0).getName()).isEqualTo("Alpha");
        assertThat(results.get(4).getName()).isEqualTo("Gamma");
    }

    @Test
    void orderByDescending() {
        CriteriaBuilder cb = new CriteriaBuilder(TestItem.class);
        cb.orderBy("priority", false);
        TypedQuery<TestItem> query = converter.convert(cb.toCriteria(), TestItem.class);
        List<TestItem> results = query.getResultList();
        assertThat(results.get(0).getPriority()).isEqualTo(5);
    }

    @Test
    void limitAndOffset() {
        CriteriaBuilder cb = new CriteriaBuilder(TestItem.class);
        cb.orderBy("id");
        cb.limit(2);
        cb.offset(1);
        TypedQuery<TestItem> query = converter.convert(cb.toCriteria(), TestItem.class);
        List<TestItem> results = query.getResultList();
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getId()).isEqualTo(2);
        assertThat(results.get(1).getId()).isEqualTo(3);
    }

    @Test
    void distinct() {
        CriteriaBuilder cb = new CriteriaBuilder(TestItem.class);
        cb.distinct();
        TypedQuery<TestItem> query = converter.convert(cb.toCriteria(), TestItem.class);
        List<TestItem> results = query.getResultList();
        assertThat(results).hasSize(5);
    }

    @Test
    void countAll() {
        Criteria criteria = new Criteria(TestItem.class);
        TypedQuery<Long> query = converter.convertForCount(criteria, TestItem.class);
        Long count = query.getSingleResult();
        assertThat(count).isEqualTo(5L);
    }

    @Test
    void countWithRestriction() {
        CriteriaBuilder cb = new CriteriaBuilder(TestItem.class);
        cb.eq("status", "ACTIVE");
        TypedQuery<Long> query = converter.convertForCount(cb.toCriteria(), TestItem.class);
        Long count = query.getSingleResult();
        assertThat(count).isEqualTo(3L);
    }

    @Test
    void countDistinct() {
        CriteriaBuilder cb = new CriteriaBuilder(TestItem.class);
        cb.distinct();
        TypedQuery<Long> query = converter.convertForCount(cb.toCriteria(), TestItem.class);
        Long count = query.getSingleResult();
        assertThat(count).isEqualTo(5L);
    }

    @Test
    void multipleRestrictions() {
        CriteriaBuilder cb = new CriteriaBuilder(TestItem.class);
        cb.eq("status", "ACTIVE");
        cb.gt("priority", 1);
        cb.orderBy("name");
        TypedQuery<TestItem> query = converter.convert(cb.toCriteria(), TestItem.class);
        List<TestItem> results = query.getResultList();
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getName()).isEqualTo("Beta");
        assertThat(results.get(1).getName()).isEqualTo("Epsilon");
    }

    @Test
    void sqlRestrictionDoesNotThrow() {
        Criteria criteria = new Criteria(TestItem.class);
        criteria.addRestriction(new org.opennms.core.criteria.restrictions.SqlRestriction("1=1"));
        assertThatCode(() -> converter.convert(criteria, TestItem.class).getResultList())
                .doesNotThrowAnyException();
    }
}
