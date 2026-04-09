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
package org.deltav.netmgt.translator.boot;

import java.io.File;
import java.io.IOException;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;

import org.deltav.core.daemon.common.SpringServiceDaemonSmartLifecycle;
import org.opennms.netmgt.config.EventTranslatorConfig;
import org.opennms.netmgt.config.EventTranslatorConfigFactory;
import org.opennms.netmgt.config.translator.EventTranslatorConfiguration;
import org.opennms.netmgt.events.api.EventIpcManager;
import org.opennms.netmgt.translator.EventTranslator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot configuration for EventTranslator.
 *
 * <p>Loads {@code translator-configuration.xml} via Jackson XmlMapper and passes
 * the deserialized model to {@link EventTranslatorConfigFactory}, which contains
 * the translation engine (spec matching, SQL value resolution, event cloning).</p>
 *
 * <p>Event enrichment (alarm-data, severity) is handled at the transport layer
 * by {@code KafkaEventTransportConfiguration} which wires {@code EventConfEnrichmentService}
 * into the {@code KafkaEventForwarder}. No per-daemon wrapper is needed.</p>
 */
@Configuration
public class EventTranslatorBootConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(EventTranslatorBootConfiguration.class);

    private static final XmlMapper XML_MAPPER;
    static {
        // defaultUseWrapper(false) is required: Jackson's JaxbAnnotationModule
        // mishandles @XmlElementWrapper when nested types share attribute names
        // (e.g., Assignment.name + Value.name). Without this, Jackson conflates
        // parent and child attributes, producing nulls and wrong values.
        XML_MAPPER = XmlMapper.builder()
                .defaultUseWrapper(false)
                .build();
        XML_MAPPER.registerModule(new JaxbAnnotationModule());
        XML_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Bean
    public EventTranslatorConfig eventTranslatorConfig(
            @Value("${opennms.home}") String opennmsHome,
            DataSource dataSource) throws IOException {
        var configFile = new File(opennmsHome, "etc/translator-configuration.xml");
        if (!configFile.exists()) {
            throw new IOException("translator-configuration.xml not found at " + configFile);
        }
        LOG.info("Loading event translator configuration from {}", configFile);
        var config = XML_MAPPER.readValue(configFile, EventTranslatorConfiguration.class);
        var factory = new EventTranslatorConfigFactory(config, dataSource);
        EventTranslatorConfigFactory.setInstance(factory);
        return factory;
    }

    @Bean
    public EventTranslator eventTranslator(
            EventIpcManager eventIpcManager,
            EventTranslatorConfig config,
            DataSource dataSource) {
        var translator = new EventTranslator();
        translator.setEventManager(eventIpcManager);
        translator.setConfig(config);
        translator.setDataSource(dataSource);
        return translator;
    }

    @Bean
    public SmartLifecycle eventTranslatorLifecycle(EventTranslator eventTranslator) {
        return new SpringServiceDaemonSmartLifecycle(eventTranslator);
    }
}
