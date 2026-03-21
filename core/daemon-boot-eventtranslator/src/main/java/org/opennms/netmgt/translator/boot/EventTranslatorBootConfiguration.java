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
package org.opennms.netmgt.translator.boot;

import javax.sql.DataSource;

import org.opennms.core.daemon.common.DaemonSmartLifecycle;
import org.opennms.core.db.DataSourceFactory;
import org.opennms.netmgt.config.EventTranslatorConfigFactory;
import org.opennms.netmgt.events.api.EventIpcManager;
import org.opennms.netmgt.translator.EventTranslator;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot configuration for EventTranslator.
 *
 * <p>Event enrichment (alarm-data, severity) is handled at the transport layer
 * by {@code KafkaEventTransportConfiguration} which wires {@code EventConfEnrichmentService}
 * into the {@code KafkaEventForwarder}. No per-daemon wrapper is needed.</p>
 */
@Configuration
public class EventTranslatorBootConfiguration {

    @Bean
    public EventTranslatorConfigFactory eventTranslatorConfig(DataSource dataSource) throws Exception {
        DataSourceFactory.setInstance(dataSource);
        EventTranslatorConfigFactory.init();
        return (EventTranslatorConfigFactory) EventTranslatorConfigFactory.getInstance();
    }

    @Bean
    public EventTranslator eventTranslator(
            EventIpcManager eventIpcManager,
            EventTranslatorConfigFactory config,
            DataSource dataSource) {
        var translator = new EventTranslator();
        translator.setEventManager(eventIpcManager);
        translator.setConfig(config);
        translator.setDataSource(dataSource);
        return translator;
    }

    @Bean
    public SmartLifecycle eventTranslatorLifecycle(EventTranslator eventTranslator) {
        return new DaemonSmartLifecycle(eventTranslator);
    }
}
