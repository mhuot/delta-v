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
import java.util.Map;

import jakarta.persistence.Query;

import org.opennms.core.daemon.common.AbstractDaoJpa;
import org.opennms.netmgt.dao.api.AlarmDao;
import org.opennms.netmgt.model.HeatMapElement;
import org.opennms.netmgt.model.OnmsAlarm;
import org.opennms.netmgt.model.OnmsCriteria;
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
 * <p>The deprecated {@link #findMatching(OnmsCriteria)} and {@link #countMatching(OnmsCriteria)}
 * methods from {@link org.opennms.netmgt.dao.api.LegacyOnmsDao} also throw
 * {@link UnsupportedOperationException} — Alarmd does not use them.</p>
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

    // ---- LegacyOnmsDao methods — not used by Alarmd ----

    @Override
    public List<OnmsAlarm> findMatching(OnmsCriteria criteria) {
        throw new UnsupportedOperationException(
                "findMatching(OnmsCriteria) is not supported in AlarmDaoJpa — use HQL queries");
    }

    @Override
    public int countMatching(OnmsCriteria onmsCrit) {
        throw new UnsupportedOperationException(
                "countMatching(OnmsCriteria) is not supported in AlarmDaoJpa — use HQL queries");
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
