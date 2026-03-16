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
package org.opennms.netmgt.alarmd.boot;

import org.opennms.core.daemon.common.EventConfEnrichmentService;
import org.opennms.netmgt.alarmd.AlarmPersister;
import org.opennms.netmgt.alarmd.AlarmPersisterImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configures event-conf enrichment for the Alarmd Spring Boot daemon.
 *
 * <p>Wraps {@link AlarmPersisterImpl} with an enrichment decorator that delegates to
 * {@link EventConfEnrichmentService} (defined in daemon-common) to apply alarm-data
 * and severity from the event configuration before the event reaches Alarmd's
 * sanity check.</p>
 */
@Configuration
public class EventEnrichmentConfiguration {

    /**
     * Wraps {@link AlarmPersisterImpl} with an enrichment step that looks up
     * alarm-data from the event configuration when the incoming event does not
     * carry it. This is the primary {@link AlarmPersister} bean; the plain
     * {@code AlarmPersisterImpl} bean defined in {@link AlarmdConfiguration}
     * is injected as the delegate.
     */
    @Bean
    @Primary
    public AlarmPersister enrichingAlarmPersister(
            AlarmPersisterImpl delegate,
            EventConfEnrichmentService enrichmentService) {
        return event -> {
            enrichmentService.enrichEvent(event);
            return delegate.persist(event);
        };
    }
}
