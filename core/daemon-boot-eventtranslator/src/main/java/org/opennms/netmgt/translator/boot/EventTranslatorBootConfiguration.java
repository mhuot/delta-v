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
import org.opennms.core.daemon.common.EventConfEnrichmentService;
import org.opennms.core.db.DataSourceFactory;
import org.opennms.netmgt.config.EventTranslatorConfigFactory;
import org.opennms.netmgt.events.api.EventIpcManager;
import org.opennms.netmgt.translator.EventTranslator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventTranslatorBootConfiguration {

    @Bean
    public EventTranslatorConfigFactory eventTranslatorConfig(DataSource dataSource) throws Exception {
        String opennmsHome = System.getProperty("opennms.home",
                System.getenv().getOrDefault("OPENNMS_HOME", "/opt/sentinel"));
        System.setProperty("opennms.home", opennmsHome);

        DataSourceFactory.setInstance(dataSource);
        EventTranslatorConfigFactory.init();
        return (EventTranslatorConfigFactory) EventTranslatorConfigFactory.getInstance();
    }

    @Bean
    public EventTranslator eventTranslator(
            @Qualifier("eventIpcManager") EventIpcManager eventIpcManager,
            EventTranslatorConfigFactory config,
            DataSource dataSource,
            EventConfEnrichmentService enrichmentService) {
        EventIpcManager enrichingManager =
                new EventIpcManagerEnrichingWrapper(eventIpcManager, enrichmentService);
        var translator = new EventTranslator();
        translator.setEventManager(enrichingManager);
        translator.setConfig(config);
        translator.setDataSource(dataSource);
        return translator;
    }

    @Bean
    public SmartLifecycle eventTranslatorLifecycle(EventTranslator eventTranslator) {
        return new DaemonSmartLifecycle(eventTranslator);
    }
}
