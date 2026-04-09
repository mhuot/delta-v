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
import java.util.Map;

import jakarta.persistence.Query;

import org.deltav.core.daemon.common.AbstractDaoJpa;
import org.opennms.netmgt.dao.api.AlarmDao;
import org.opennms.netmgt.model.HeatMapElement;
import org.opennms.netmgt.model.OnmsAlarm;
import org.opennms.netmgt.model.alarm.AlarmSummary;
import org.opennms.netmgt.model.alarm.SituationSummary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA implementation of {@link AlarmDao}.
 *
 * <p>Implements only the methods required by Alarmd's core processing path.
 * REST-oriented methods ({@link #getNodeAlarmSummaries()}, {@link #getSituationSummaries()},
 * {@link #getNodeAlarmSummariesIncludeAcknowledgedOnes}, {@link #getHeatMapItemsForEntity},
 * {@link #getAlarmsForEventParameters}) are not used by Alarmd and throw
 * {@link UnsupportedOperationException}.</p>
 *
 */
@Repository
@Transactional
public class AlarmDaoJpa extends AbstractDaoJpa<OnmsAlarm, Integer> implements AlarmDao {

    public AlarmDaoJpa() {
        super(OnmsAlarm.class);
    }

    @Override
    public OnmsAlarm findByReductionKey(String reductionKey) {
        return findUnique(
                "SELECT a FROM OnmsAlarm a WHERE a.reductionKey = ?1",
                reductionKey);
    }

    @Override
    public long getNumSituations() {
        Query query = entityManager().createNativeQuery(
                "SELECT COUNT(DISTINCT situation_id) FROM alarm_situations");
        Number result = (Number) query.getSingleResult();
        return result != null ? result.longValue() : 0L;
    }

    @Override
    public long getNumAlarmsLastHours(int hours) {
        if (hours <= 0) {
            return 0L;
        }
        Query query = entityManager().createNativeQuery(
                "SELECT COUNT(*) FROM alarms WHERE firsteventtime >= NOW() - (:hours * INTERVAL '1 hour')");
        query.setParameter("hours", hours);
        Number result = (Number) query.getSingleResult();
        return result != null ? result.longValue() : 0L;
    }

    // ---- REST-oriented methods — not used by Alarmd core ----

    @Override
    public List<AlarmSummary> getNodeAlarmSummaries() {
        throw new UnsupportedOperationException(
                "getNodeAlarmSummaries() is not used by Alarmd — this is a REST API method");
    }

    @Override
    public List<SituationSummary> getSituationSummaries() {
        throw new UnsupportedOperationException(
                "getSituationSummaries() is not used by Alarmd — this is a REST API method");
    }

    @Override
    public List<AlarmSummary> getNodeAlarmSummariesIncludeAcknowledgedOnes(List<Integer> nodeIds) {
        throw new UnsupportedOperationException(
                "getNodeAlarmSummariesIncludeAcknowledgedOnes() is not used by Alarmd — this is a REST API method");
    }

    @Override
    public List<HeatMapElement> getHeatMapItemsForEntity(String entityNameColumn, String entityIdColumn,
            boolean processAcknowledgedAlarms, String restrictionColumn, String restrictionValue,
            String... groupByColumns) {
        throw new UnsupportedOperationException(
                "getHeatMapItemsForEntity() is not used by Alarmd — this is a REST API method");
    }

    @Override
    public List<OnmsAlarm> getAlarmsForEventParameters(Map<String, String> eventParameters) {
        throw new UnsupportedOperationException(
                "getAlarmsForEventParameters() is not used by Alarmd — this is a REST API method");
    }
}
