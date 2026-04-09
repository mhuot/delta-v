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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.TypedQuery;

import org.opennms.core.criteria.Alias;
import org.opennms.core.criteria.Criteria;
import org.opennms.core.criteria.restrictions.AnyRestriction;
import org.opennms.core.criteria.restrictions.AttributeRestriction;
import org.opennms.core.criteria.restrictions.AttributeValueRestriction;
import org.opennms.core.criteria.restrictions.EqRestriction;
import org.opennms.core.criteria.restrictions.NeRestriction;
import org.opennms.core.criteria.restrictions.NullRestriction;
import org.opennms.core.criteria.restrictions.Restriction;
import org.deltav.core.daemon.common.AbstractDaoJpa;
import org.opennms.netmgt.dao.api.OutageDao;
import org.opennms.netmgt.model.HeatMapElement;
import org.opennms.netmgt.model.OnmsMonitoredService;
import org.opennms.netmgt.model.OnmsOutage;
import org.opennms.netmgt.model.ServiceSelector;
import org.opennms.netmgt.model.monitoringLocations.OnmsMonitoringLocation;
import org.opennms.netmgt.model.outage.CurrentOutageDetails;
import org.opennms.netmgt.model.outage.OutageSummary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of {@link OutageDao}.
 *
 * <p>Implements only the methods required by Pollerd's core processing path
 * (via QueryManagerDaoImpl): {@link #currentOutageForService},
 * {@link #findMatching(Criteria)}, and the standard CRUD operations inherited
 * from {@link AbstractDaoJpa}.</p>
 *
 * <p>REST-oriented and legacy methods not used by Pollerd throw
 * {@link UnsupportedOperationException}.</p>
 */
@Repository
@Transactional(readOnly = false)
public class OutageDaoJpa extends AbstractDaoJpa<OnmsOutage, Integer> implements OutageDao {

    public OutageDaoJpa() {
        super(OnmsOutage.class);
    }

    @Override
    @Transactional
    public void saveOrUpdate(OnmsOutage entity) {
        super.saveOrUpdate(entity);
    }

    @Override
    @Transactional
    public void update(OnmsOutage entity) {
        super.update(entity);
    }

    /**
     * Saves an outage, merging any detached entity references first.
     *
     * <p>PerspectivePollerd's event handlers load {@code OnmsMonitoredService} and
     * {@code OnmsMonitoringLocation} in separate transactions from where
     * {@code outageDao.save()} runs. These entities are detached by the time
     * {@code save()} is called. JPA's {@code persist()} rejects detached references,
     * so we merge them into the current persistence context first.</p>
     */
    @Override
    @Transactional
    public Integer save(OnmsOutage entity) {
        if (entity.getMonitoredService() != null) {
            entity.setMonitoredService(entityManager().merge(entity.getMonitoredService()));
        }
        if (entity.getPerspective() != null) {
            entity.setPerspective(entityManager().merge(entity.getPerspective()));
        }
        return super.save(entity);
    }

    @Override
    public OnmsOutage currentOutageForService(OnmsMonitoredService service) {
        return findUnique(
                "SELECT o FROM OnmsOutage o "
                + "WHERE o.monitoredService = ?1 "
                + "AND o.ifRegainedService IS NULL "
                + "AND o.perspective IS NULL",
                service);
    }

    /**
     * Translates an OpenNMS {@link Criteria} object into a JPQL query.
     *
     * <p>Supports the restriction types used by QueryManagerDaoImpl:
     * {@link EqRestriction}, {@link NullRestriction}, {@link NeRestriction},
     * and {@link AnyRestriction} (OR clause). Aliases are translated to
     * JPQL JOINs.</p>
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<OnmsOutage> findMatching(Criteria criteria) {
        StringBuilder jpql = new StringBuilder("SELECT o FROM OnmsOutage o");
        List<Object> parameters = new ArrayList<>();
        Map<String, String> aliasMap = new HashMap<>();

        // Process aliases as JOINs
        for (Alias alias : criteria.getAliases()) {
            String joinType = mapJoinType(alias.getType());
            jpql.append(" ").append(joinType).append(" o.").append(alias.getAssociationPath())
                .append(" ").append(alias.getAlias());
            aliasMap.put(alias.getAlias(), alias.getAssociationPath());
        }

        // Process restrictions as WHERE clauses
        Collection<Restriction> restrictions = criteria.getRestrictions();
        if (!restrictions.isEmpty()) {
            jpql.append(" WHERE ");
            List<String> whereFragments = new ArrayList<>();
            for (Restriction restriction : restrictions) {
                String fragment = buildRestrictionFragment(restriction, parameters);
                if (fragment != null) {
                    whereFragments.add(fragment);
                }
            }
            jpql.append(String.join(" AND ", whereFragments));
        }

        TypedQuery<OnmsOutage> query = entityManager().createQuery(jpql.toString(), OnmsOutage.class);
        for (int i = 0; i < parameters.size(); i++) {
            query.setParameter(i + 1, parameters.get(i));
        }

        if (criteria.getLimit() != null) {
            query.setMaxResults(criteria.getLimit());
        }
        if (criteria.getOffset() != null) {
            query.setFirstResult(criteria.getOffset());
        }

        return query.getResultList();
    }

    private String buildRestrictionFragment(Restriction restriction, List<Object> parameters) {
        if (restriction instanceof NullRestriction) {
            NullRestriction nr = (NullRestriction) restriction;
            return resolveAttribute(nr.getAttribute()) + " IS NULL";
        } else if (restriction instanceof EqRestriction) {
            EqRestriction er = (EqRestriction) restriction;
            parameters.add(er.getValue());
            return resolveAttribute(er.getAttribute()) + " = ?" + parameters.size();
        } else if (restriction instanceof NeRestriction) {
            NeRestriction nr = (NeRestriction) restriction;
            parameters.add(nr.getValue());
            return resolveAttribute(nr.getAttribute()) + " <> ?" + parameters.size();
        } else if (restriction instanceof AnyRestriction) {
            AnyRestriction any = (AnyRestriction) restriction;
            List<String> orFragments = new ArrayList<>();
            for (Restriction inner : any.getRestrictions()) {
                String fragment = buildRestrictionFragment(inner, parameters);
                if (fragment != null) {
                    orFragments.add(fragment);
                }
            }
            if (orFragments.isEmpty()) {
                return null;
            }
            return "(" + String.join(" OR ", orFragments) + ")";
        }
        throw new UnsupportedOperationException(
                "Unsupported restriction type in OutageDaoJpa.findMatching: " + restriction.getClass().getSimpleName());
    }

    /**
     * Resolves a Criteria attribute path to a JPQL path.
     * Attributes without a dot prefix are assumed to be on the root entity "o".
     */
    private String resolveAttribute(String attribute) {
        if (attribute.contains(".")) {
            // Already qualified (e.g., "monitoredService.status" or alias-prefixed)
            return attribute;
        }
        return "o." + attribute;
    }

    private String mapJoinType(Alias.JoinType joinType) {
        switch (joinType) {
            case LEFT_JOIN: return "LEFT JOIN";
            case INNER_JOIN: return "JOIN";
            case FULL_JOIN: return "FULL JOIN";
            default: return "JOIN";
        }
    }

    // ---- OutageDao methods not used by Pollerd ----

    @Override
    public Integer currentOutageCount() {
        throw new UnsupportedOperationException(
                "currentOutageCount() is not used by Pollerd");
    }

    @Override
    public Collection<OnmsOutage> currentOutages() {
        throw new UnsupportedOperationException(
                "currentOutages() is not used by Pollerd");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<Integer, Set<OnmsOutage>> currentOutagesByServiceId() {
        List<OnmsOutage> outages = entityManager().createQuery(
                "SELECT o FROM OnmsOutage o WHERE o.ifRegainedService IS NULL AND o.perspective IS NULL")
                .getResultList();
        Map<Integer, Set<OnmsOutage>> result = new java.util.HashMap<>();
        for (OnmsOutage outage : outages) {
            if (outage.getMonitoredService() != null) {
                result.computeIfAbsent(outage.getMonitoredService().getId(),
                        k -> new java.util.HashSet<>()).add(outage);
            }
        }
        return result;
    }

    @Override
    public OnmsOutage currentOutageForServiceFromPerspective(OnmsMonitoredService service, OnmsMonitoringLocation perspective) {
        return findUnique(
                "FROM OnmsOutage o WHERE o.monitoredService = ?1 AND o.perspective = ?2 AND o.ifRegainedService IS NULL",
                service, perspective);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Collection<OnmsOutage> currentOutagesForServiceFromPerspectivePoller(OnmsMonitoredService service) {
        return find(
                "FROM OnmsOutage o WHERE o.monitoredService = ?1 AND o.perspective IS NOT NULL AND o.ifRegainedService IS NULL",
                service);
    }

    @Override
    public Collection<CurrentOutageDetails> newestCurrentOutages(List<String> services) {
        throw new UnsupportedOperationException(
                "newestCurrentOutages() is not used by Pollerd");
    }

    @Override
    public Collection<OnmsOutage> matchingCurrentOutages(ServiceSelector selector) {
        throw new UnsupportedOperationException(
                "matchingCurrentOutages() is not used by Pollerd");
    }

    @Override
    public Collection<OnmsOutage> findAll(Integer offset, Integer limit) {
        throw new UnsupportedOperationException(
                "findAll(offset, limit) is not used by Pollerd");
    }

    @Override
    public int countOutagesByNode() {
        throw new UnsupportedOperationException(
                "countOutagesByNode() is not used by Pollerd");
    }

    @Override
    public List<OutageSummary> getNodeOutageSummaries(int rows) {
        throw new UnsupportedOperationException(
                "getNodeOutageSummaries() is not used by Pollerd");
    }

    @Override
    public List<HeatMapElement> getHeatMapItemsForEntity(String entityNameColumn, String entityIdColumn,
            String restrictionColumn, String restrictionValue, String... groupByColumns) {
        throw new UnsupportedOperationException(
                "getHeatMapItemsForEntity() is not used by Pollerd");
    }

    @Override
    public Collection<OnmsOutage> getStatusChangesForApplicationIdBetween(Date startDate, Date endDate, Integer applicationId) {
        throw new UnsupportedOperationException(
                "getStatusChangesForApplicationIdBetween() is not used by Pollerd");
    }
}
