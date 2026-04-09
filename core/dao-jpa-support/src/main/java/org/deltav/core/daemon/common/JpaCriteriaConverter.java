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
package org.deltav.core.daemon.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.opennms.core.criteria.AbstractCriteriaVisitor;
import org.opennms.core.criteria.Alias;
import org.opennms.core.criteria.Criteria;
import org.opennms.core.criteria.Criteria.LockType;
import org.opennms.core.criteria.Fetch;
import org.opennms.core.criteria.Order;
import org.opennms.core.criteria.restrictions.AllRestriction;
import org.opennms.core.criteria.restrictions.AnyRestriction;
import org.opennms.core.criteria.restrictions.BaseRestrictionVisitor;
import org.opennms.core.criteria.restrictions.BetweenRestriction;
import org.opennms.core.criteria.restrictions.EqPropertyRestriction;
import org.opennms.core.criteria.restrictions.EqRestriction;
import org.opennms.core.criteria.restrictions.GeRestriction;
import org.opennms.core.criteria.restrictions.GtRestriction;
import org.opennms.core.criteria.restrictions.IlikeRestriction;
import org.opennms.core.criteria.restrictions.InRestriction;
import org.opennms.core.criteria.restrictions.IplikeRestriction;
import org.opennms.core.criteria.restrictions.LeRestriction;
import org.opennms.core.criteria.restrictions.LikeRestriction;
import org.opennms.core.criteria.restrictions.LtRestriction;
import org.opennms.core.criteria.restrictions.NeRestriction;
import org.opennms.core.criteria.restrictions.NotNullRestriction;
import org.opennms.core.criteria.restrictions.NotRestriction;
import org.opennms.core.criteria.restrictions.NullRestriction;
import org.opennms.core.criteria.restrictions.RegExpRestriction;
import org.opennms.core.criteria.restrictions.Restriction;
import org.opennms.core.criteria.restrictions.RestrictionVisitor;
import org.opennms.core.criteria.restrictions.SqlRestriction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Translates OpenNMS {@link Criteria} into JPA {@link CriteriaQuery} objects.
 *
 * <p>This is the Jakarta/Hibernate 7 replacement for {@code HibernateCriteriaConverter},
 * which targeted the removed {@code org.hibernate.criterion.DetachedCriteria} API.</p>
 *
 * <p>Usage:
 * <pre>
 *   JpaCriteriaConverter converter = new JpaCriteriaConverter(entityManager);
 *   TypedQuery&lt;T&gt; query = converter.convert(criteria, entityClass);
 *   List&lt;T&gt; results = query.getResultList();
 * </pre>
 */
public class JpaCriteriaConverter {

    private static final Logger LOG = LoggerFactory.getLogger(JpaCriteriaConverter.class);

    private final EntityManager entityManager;

    public JpaCriteriaConverter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Convert an OpenNMS Criteria into a ready-to-execute TypedQuery for entity selection.
     */
    public <T> TypedQuery<T> convert(Criteria criteria, Class<T> entityClass) {
        JpaCriteriaVisitor<T> visitor = new JpaCriteriaVisitor<>(entityManager, entityClass, false);
        criteria.visit(visitor);
        return visitor.buildQuery();
    }

    /**
     * Convert an OpenNMS Criteria into a ready-to-execute TypedQuery for counting.
     */
    public TypedQuery<Long> convertForCount(Criteria criteria, Class<?> entityClass) {
        JpaCountCriteriaVisitor visitor = new JpaCountCriteriaVisitor(entityManager, entityClass);
        criteria.visit(visitor);
        return visitor.buildCountQuery();
    }

    // ---- Visitor for entity selection queries ----

    static class JpaCriteriaVisitor<T> extends AbstractCriteriaVisitor {

        final EntityManager em;
        final Class<T> entityClass;
        final CriteriaBuilder cb;
        final CriteriaQuery<T> cq;
        final Root<T> root;

        final Map<String, From<?, ?>> aliasMap = new HashMap<>();
        final List<Predicate> predicates = new ArrayList<>();
        final Set<jakarta.persistence.criteria.Order> orders = new LinkedHashSet<>();

        boolean distinct = false;
        Integer limit;
        Integer offset;
        LockModeType lockMode;
        final boolean skipOrders;

        JpaCriteriaVisitor(EntityManager em, Class<T> entityClass, boolean skipOrders) {
            this.em = em;
            this.entityClass = entityClass;
            this.cb = em.getCriteriaBuilder();
            this.cq = cb.createQuery(entityClass);
            this.root = cq.from(entityClass);
            this.skipOrders = skipOrders;
        }

        @Override
        public void visitOrder(Order order) {
            if (skipOrders) return;

            Path<?> path = resolvePath(order.getAttribute());
            if (order.asc()) {
                orders.add(cb.asc(path));
            } else {
                orders.add(cb.desc(path));
            }
        }

        @Override
        public void visitAlias(Alias alias) {
            JoinType joinType = mapJoinType(alias.getType());
            From<?, ?> parent = resolveParent(alias.getAssociationPath());
            String attribute = leafAttribute(alias.getAssociationPath());

            Join<?, ?> join = parent.join(attribute, joinType);

            if (alias.hasJoinCondition()) {
                JpaRestrictionVisitor rv = new JpaRestrictionVisitor(cb, root, aliasMap);
                alias.getJoinCondition().visit(rv);
                List<Predicate> joinPredicates = rv.getPredicates();
                if (!joinPredicates.isEmpty()) {
                    join.on(joinPredicates.toArray(new Predicate[0]));
                }
            }

            aliasMap.put(alias.getAlias(), join);
        }

        @Override
        public void visitFetch(Fetch fetch) {
            jakarta.persistence.criteria.FetchParent<?, ?> fetchParent = root;
            switch (fetch.getFetchType()) {
                case EAGER:
                    // If there's already an alias/join for this path, JPA may complain about
                    // duplicate joins. Use the root fetch which becomes a JOIN FETCH in SQL.
                    fetchParent.fetch(fetch.getAttribute(), JoinType.LEFT);
                    break;
                case LAZY:
                case DEFAULT:
                    // JPA doesn't have a direct "select fetch" mode — lazy is the default
                    break;
            }
        }

        @Override
        public void visitLockType(LockType lock) {
            if (lock == null) return;
            lockMode = mapLockMode(lock);
        }

        @Override
        public void visitRestriction(Restriction restriction) {
            JpaRestrictionVisitor rv = new JpaRestrictionVisitor(cb, root, aliasMap);
            restriction.visit(rv);
            predicates.addAll(rv.getPredicates());
        }

        @Override
        public void visitDistinct(boolean distinct) {
            this.distinct = distinct;
        }

        @Override
        public void visitLimit(Integer limit) {
            this.limit = limit;
        }

        @Override
        public void visitOffset(Integer offset) {
            this.offset = offset;
        }

        TypedQuery<T> buildQuery() {
            if (!predicates.isEmpty()) {
                cq.where(predicates.toArray(new Predicate[0]));
            }

            if (distinct) {
                cq.distinct(true);
            }

            if (!orders.isEmpty()) {
                cq.orderBy(new ArrayList<>(orders));
            }

            TypedQuery<T> query = em.createQuery(cq);

            if (limit != null) {
                query.setMaxResults(limit);
            }
            if (offset != null) {
                query.setFirstResult(offset);
            }
            if (lockMode != null) {
                query.setLockMode(lockMode);
            }

            return query;
        }

        // ---- Path resolution helpers ----

        Path<?> resolvePath(String attributePath) {
            String[] parts = attributePath.split("\\.");

            // Check if first part is an alias
            From<?, ?> from = aliasMap.get(parts[0]);
            int startIdx;
            if (from != null) {
                startIdx = 1;
            } else {
                from = root;
                startIdx = 0;
            }

            Path<?> path = from;
            for (int i = startIdx; i < parts.length; i++) {
                path = path.get(parts[i]);
            }
            return path;
        }

        From<?, ?> resolveParent(String associationPath) {
            int dot = associationPath.lastIndexOf('.');
            if (dot < 0) {
                return root;
            }
            String parentPath = associationPath.substring(0, dot);
            // The parent might itself be an alias
            From<?, ?> aliased = aliasMap.get(parentPath);
            if (aliased != null) {
                return aliased;
            }
            // Otherwise navigate from root
            return root;
        }

        static String leafAttribute(String path) {
            int dot = path.lastIndexOf('.');
            return dot < 0 ? path : path.substring(dot + 1);
        }

        static JoinType mapJoinType(Alias.JoinType type) {
            if (type == null) return JoinType.INNER;
            return switch (type) {
                case LEFT_JOIN -> JoinType.LEFT;
                case FULL_JOIN -> JoinType.LEFT; // JPA doesn't support FULL — approximate with LEFT
                case INNER_JOIN -> JoinType.INNER;
            };
        }

        static LockModeType mapLockMode(LockType lock) {
            return switch (lock) {
                case NONE -> LockModeType.NONE;
                case READ, OPTIMISTIC -> LockModeType.OPTIMISTIC;
                case OPTIMISTIC_FORCE_INCREMENT -> LockModeType.OPTIMISTIC_FORCE_INCREMENT;
                case PESSIMISTIC_READ -> LockModeType.PESSIMISTIC_READ;
                case PESSIMISTIC_WRITE, WRITE, UPGRADE_NOWAIT -> LockModeType.PESSIMISTIC_WRITE;
                case PESSIMISTIC_FORCE_INCREMENT -> LockModeType.PESSIMISTIC_FORCE_INCREMENT;
            };
        }
    }

    // ---- Visitor for count queries (skips orders) ----

    static class JpaCountCriteriaVisitor extends JpaCriteriaVisitor<Object> {

        private final CriteriaQuery<Long> countQuery;
        private final Root<?> countRoot;

        @SuppressWarnings("unchecked")
        JpaCountCriteriaVisitor(EntityManager em, Class<?> entityClass) {
            super(em, (Class<Object>) entityClass, true);
            this.countQuery = cb.createQuery(Long.class);
            this.countRoot = countQuery.from(entityClass);
        }

        TypedQuery<Long> buildCountQuery() {
            if (!predicates.isEmpty()) {
                countQuery.where(predicates.toArray(new Predicate[0]));
            }

            if (distinct) {
                countQuery.select(cb.countDistinct(countRoot));
            } else {
                countQuery.select(cb.count(countRoot));
            }

            TypedQuery<Long> query = em.createQuery(countQuery);

            if (lockMode != null) {
                query.setLockMode(lockMode);
            }

            return query;
        }

        @Override
        public void visitRestriction(Restriction restriction) {
            // For count queries, resolve paths against the countRoot
            JpaRestrictionVisitor rv = new JpaRestrictionVisitor(cb, countRoot, aliasMap);
            restriction.visit(rv);
            predicates.addAll(rv.getPredicates());
        }

        @Override
        public void visitAlias(Alias alias) {
            JoinType joinType = mapJoinType(alias.getType());
            // Resolve alias against countRoot for count queries
            From<?, ?> parent = aliasMap.containsKey(alias.getAssociationPath().split("\\.")[0])
                    ? resolveParent(alias.getAssociationPath())
                    : countRoot;
            String attribute = leafAttribute(alias.getAssociationPath());

            String assocPath = alias.getAssociationPath();
            int dot = assocPath.lastIndexOf('.');
            if (dot >= 0) {
                String parentAlias = assocPath.substring(0, dot);
                From<?, ?> parentFrom = aliasMap.get(parentAlias);
                if (parentFrom != null) {
                    parent = parentFrom;
                }
            }

            Join<?, ?> join = parent.join(attribute, joinType);
            aliasMap.put(alias.getAlias(), join);
        }
    }

    // ---- Restriction visitor: translates Restriction tree → JPA Predicates ----

    static class JpaRestrictionVisitor extends BaseRestrictionVisitor implements RestrictionVisitor {

        private final CriteriaBuilder cb;
        private final Root<?> root;
        private final Map<String, From<?, ?>> aliasMap;
        private final List<Predicate> predicateStack = new ArrayList<>();

        JpaRestrictionVisitor(CriteriaBuilder cb, Root<?> root, Map<String, From<?, ?>> aliasMap) {
            this.cb = cb;
            this.root = root;
            this.aliasMap = aliasMap;
        }

        List<Predicate> getPredicates() {
            return predicateStack;
        }

        // ---- Simple comparisons ----

        @Override
        public void visitNull(NullRestriction restriction) {
            predicateStack.add(cb.isNull(resolvePath(restriction.getAttribute())));
        }

        @Override
        public void visitNotNull(NotNullRestriction restriction) {
            predicateStack.add(cb.isNotNull(resolvePath(restriction.getAttribute())));
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void visitEq(EqRestriction restriction) {
            Path path = resolvePath(restriction.getAttribute());
            predicateStack.add(cb.equal(path, coerceValue(restriction.getValue())));
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void visitEqProperty(EqPropertyRestriction restriction) {
            Path left = resolvePath(restriction.getAttribute());
            Path right = resolvePath(restriction.getValue().toString());
            predicateStack.add(cb.equal(left, right));
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void visitNe(NeRestriction restriction) {
            Path path = resolvePath(restriction.getAttribute());
            predicateStack.add(cb.notEqual(path, coerceValue(restriction.getValue())));
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void visitGt(GtRestriction restriction) {
            // Don't use .as(Comparable.class) — Hibernate 7 SQM rejects casts to unmapped types.
            // The Path already carries its concrete type from the entity metamodel.
            Expression path = resolvePath(restriction.getAttribute());
            predicateStack.add(cb.greaterThan(path, (Comparable) restriction.getValue()));
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void visitGe(GeRestriction restriction) {
            Expression path = resolvePath(restriction.getAttribute());
            predicateStack.add(cb.greaterThanOrEqualTo(path, (Comparable) restriction.getValue()));
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void visitLt(LtRestriction restriction) {
            Expression path = resolvePath(restriction.getAttribute());
            predicateStack.add(cb.lessThan(path, (Comparable) restriction.getValue()));
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void visitLe(LeRestriction restriction) {
            Expression path = resolvePath(restriction.getAttribute());
            predicateStack.add(cb.lessThanOrEqualTo(path, (Comparable) restriction.getValue()));
        }

        // ---- String matching ----

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void visitLike(LikeRestriction restriction) {
            Expression<String> path = (Expression<String>) (Expression) resolvePath(restriction.getAttribute());
            predicateStack.add(cb.like(path, restriction.getValue().toString()));
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void visitIlike(IlikeRestriction restriction) {
            Expression<String> path = (Expression<String>) (Expression) resolvePath(restriction.getAttribute());
            String pattern = restriction.getValue().toString().toLowerCase();
            predicateStack.add(cb.like(cb.lower(path), pattern));
        }

        // ---- Collection operations ----

        @Override
        public void visitIn(InRestriction restriction) {
            Collection<?> values = restriction.getValues();
            if (values == null || values.isEmpty()) {
                // Empty IN → always false
                predicateStack.add(cb.disjunction());
            } else {
                predicateStack.add(resolvePath(restriction.getAttribute()).in(values));
            }
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void visitBetween(BetweenRestriction restriction) {
            Expression path = resolvePath(restriction.getAttribute());
            predicateStack.add(cb.between(path,
                    (Comparable) restriction.getBegin(),
                    (Comparable) restriction.getEnd()));
        }

        // ---- Logical operators ----

        @Override
        public void visitAllComplete(AllRestriction restriction) {
            int restrictionSize = restriction.getRestrictions().size();
            int stackSize = predicateStack.size();
            if (stackSize < restrictionSize) {
                throw new IllegalStateException(
                        "AllRestriction with " + restrictionSize + " entries, but only " + stackSize + " predicates");
            }
            List<Predicate> subList = predicateStack.subList(stackSize - restrictionSize, stackSize);
            Predicate conjunction = cb.and(subList.toArray(new Predicate[0]));
            subList.clear();
            predicateStack.add(conjunction);
        }

        @Override
        public void visitAnyComplete(AnyRestriction restriction) {
            int restrictionSize = restriction.getRestrictions().size();
            int stackSize = predicateStack.size();
            if (stackSize < restrictionSize) {
                throw new IllegalStateException(
                        "AnyRestriction with " + restrictionSize + " entries, but only " + stackSize + " predicates");
            }
            List<Predicate> subList = predicateStack.subList(stackSize - restrictionSize, stackSize);
            Predicate disjunction = cb.or(subList.toArray(new Predicate[0]));
            subList.clear();
            predicateStack.add(disjunction);
        }

        @Override
        public void visitNotComplete(NotRestriction restriction) {
            if (predicateStack.isEmpty()) {
                throw new IllegalStateException("NotRestriction called, but no predicates to negate");
            }
            Predicate last = predicateStack.remove(predicateStack.size() - 1);
            predicateStack.add(cb.not(last));
        }

        // ---- Regex (PostgreSQL ~ operator via native SQL fragment) ----

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void visitRegExp(RegExpRestriction restriction) {
            // PostgreSQL regex: column ~ 'pattern'
            Expression<String> path = (Expression<String>) (Expression) resolvePath(restriction.getAttribute());
            Expression<Boolean> regexpMatch = cb.function(
                    "textregexeq", Boolean.class, path, cb.literal(restriction.getValue().toString()));
            predicateStack.add(cb.isTrue(regexpMatch));
        }

        // ---- SQL restriction (native SQL fragment) ----

        @Override
        public void visitSql(SqlRestriction restriction) {
            LOG.warn("SqlRestriction encountered — native SQL fragments are not directly " +
                    "translatable to JPA Criteria API. Falling back to always-true. " +
                    "SQL: {}", restriction.getAttribute());
            // SqlRestriction embeds raw SQL with {alias} placeholders. The JPA Criteria API
            // has no equivalent. Log a warning and fall through as a no-op.
            // Callers that need raw SQL should use native queries directly.
            predicateStack.add(cb.conjunction());
        }

        // ---- IPLIKE (PostgreSQL iplike() function) ----

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void visitIplike(IplikeRestriction restriction) {
            String attribute = restriction.getAttribute();
            if (attribute == null || attribute.isEmpty()) {
                attribute = "ipAddr";
            }
            Expression<String> path = (Expression<String>) (Expression) resolvePath(attribute);
            Expression<Boolean> iplikeResult = cb.function(
                    "iplike", Boolean.class, path, cb.literal(restriction.getValue().toString()));
            predicateStack.add(cb.isTrue(iplikeResult));
        }

        // ---- Value coercion ----

        private Object coerceValue(Object value) {
            // Hibernate 7 cannot auto-convert Character → String when binding parameters.
            // Legacy OpenNMS code passes char values (e.g., PrimaryType.getCharCode() → 'P')
            // for columns that are now mapped as String via AttributeConverter.
            if (value instanceof Character) {
                return value.toString();
            }
            return value;
        }

        // ---- Path resolution ----

        private Path<?> resolvePath(String attributePath) {
            String[] parts = attributePath.split("\\.");

            From<?, ?> from = aliasMap.get(parts[0]);
            int startIdx;
            if (from != null) {
                startIdx = 1;
            } else {
                from = root;
                startIdx = 0;
            }

            Path<?> path = from;
            for (int i = startIdx; i < parts.length; i++) {
                path = path.get(parts[i]);
            }
            return path;
        }
    }
}
