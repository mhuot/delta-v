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
package org.deltav.flows.enricher;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.sql.DataSource;

import com.codahale.metrics.MetricRegistry;

import org.deltav.flows.enricher.classification.ApplicationClassifier;
import org.deltav.flows.enricher.classification.PortBasedApplicationClassifier;
import org.deltav.flows.enricher.enrichment.FlowLocalityCalculator;
import org.deltav.flows.enricher.enrichment.InterfaceMarkingCache;
import org.deltav.flows.enricher.enrichment.JdbcNodeInfoLookup;
import org.deltav.flows.enricher.mapping.FlowToDocumentMapper;
import org.deltav.flows.enricher.protocol.IpfixMessageProcessor;
import org.deltav.flows.enricher.protocol.Netflow5MessageProcessor;
import org.deltav.flows.enricher.protocol.Netflow9MessageProcessor;
import org.deltav.flows.enricher.protocol.ProtocolMessageProcessor;
import org.deltav.flows.enricher.protocol.SFlowMessageProcessor;
import org.deltav.flows.enricher.protocol.SimpleAdapterDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Bean wiring for the flow-enricher service. Beans are constructed with
 * constructor injection rather than field {@code @Autowired} per the
 * delta-v project convention.
 */
@Configuration
public class FlowEnricherConfiguration {

    @Bean
    JdbcTemplate flowEnricherJdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    JdbcNodeInfoLookup jdbcNodeInfoLookup(
            JdbcTemplate flowEnricherJdbcTemplate,
            @Value("${deltav.flows.node-lookup.cache-ttl:5m}") Duration cacheTtl) {
        return new JdbcNodeInfoLookup(flowEnricherJdbcTemplate, cacheTtl);
    }

    @Bean
    FlowLocalityCalculator flowLocalityCalculator() {
        return new FlowLocalityCalculator();
    }

    @Bean
    InterfaceMarkingCache interfaceMarkingCache(
            JdbcTemplate flowEnricherJdbcTemplate,
            @Value("${deltav.flows.interface-marking.cache-ttl:24h}") Duration cacheTtl) {
        return new InterfaceMarkingCache(flowEnricherJdbcTemplate, cacheTtl);
    }

    @Bean
    SinkMessageDeserializer sinkMessageDeserializer() {
        return new SinkMessageDeserializer();
    }

    @Bean
    ApplicationClassifier applicationClassifier() {
        return new PortBasedApplicationClassifier();
    }

    @Bean
    FlowToDocumentMapper flowToDocumentMapper() {
        return new FlowToDocumentMapper();
    }

    /**
     * A process-local Dropwizard {@link MetricRegistry} shared across the
     * four horizon flow adapters. Phase 1.5 does not publish these metrics
     * anywhere; the registry exists solely because horizon's
     * {@code AbstractFlowAdapter} constructor requires one for timer/meter
     * registration. A later phase can wire this to an actuator endpoint.
     */
    @Bean
    MetricRegistry flowEnricherMetricRegistry() {
        return new MetricRegistry();
    }

    @Bean
    Netflow5MessageProcessor netflow5Processor(MetricRegistry flowEnricherMetricRegistry) {
        return new Netflow5MessageProcessor(
                new SimpleAdapterDefinition("Netflow-5"), flowEnricherMetricRegistry);
    }

    @Bean
    Netflow9MessageProcessor netflow9Processor(MetricRegistry flowEnricherMetricRegistry) {
        return new Netflow9MessageProcessor(
                new SimpleAdapterDefinition("Netflow-9"), flowEnricherMetricRegistry);
    }

    @Bean
    IpfixMessageProcessor ipfixProcessor(MetricRegistry flowEnricherMetricRegistry) {
        return new IpfixMessageProcessor(
                new SimpleAdapterDefinition("IPFIX"), flowEnricherMetricRegistry);
    }

    @Bean
    SFlowMessageProcessor sflowProcessor(MetricRegistry flowEnricherMetricRegistry) {
        return new SFlowMessageProcessor(
                new SimpleAdapterDefinition("SFlow"), flowEnricherMetricRegistry);
    }

    @Bean
    FlowEnrichmentFunction flowEnrichmentFunction(
            SinkMessageDeserializer deserializer,
            JdbcNodeInfoLookup nodeInfoLookup,
            FlowLocalityCalculator localityCalculator,
            InterfaceMarkingCache interfaceMarkingCache,
            ApplicationClassifier applicationClassifier,
            FlowToDocumentMapper flowToDocumentMapper,
            Netflow5MessageProcessor netflow5Processor,
            Netflow9MessageProcessor netflow9Processor,
            IpfixMessageProcessor ipfixProcessor,
            SFlowMessageProcessor sflowProcessor) {

        Map<String, ProtocolMessageProcessor> dispatchMap = Map.of(
                "Telemetry-Netflow-5", netflow5Processor,
                "Telemetry-Netflow-9", netflow9Processor,
                "Telemetry-IPFIX",     ipfixProcessor,
                "Telemetry-SFlow",     sflowProcessor);

        return new FlowEnrichmentFunction(
                deserializer,
                nodeInfoLookup,
                localityCalculator,
                interfaceMarkingCache,
                applicationClassifier,
                flowToDocumentMapper,
                dispatchMap);
    }

    /**
     * Spring Cloud Stream function binding. The bean name {@code enrichFlows}
     * matches the {@code spring.cloud.function.definition} value in
     * application.yml; the suffixes {@code -in-0} / {@code -out-0} are
     * generated automatically by Spring Cloud Function.
     *
     * <p>The splitter signature {@code Function<Message<byte[]>, List<byte[]>>}
     * emits zero or more output records per input record; an empty list
     * discards the message entirely. The {@code Message<byte[]>} envelope
     * carries the Kafka {@code kafka_receivedTopic} header, which
     * {@link FlowEnrichmentFunction} uses to extract the module ID for
     * protocol dispatch.
     */
    @Bean
    Function<Message<byte[]>, List<byte[]>> enrichFlows(FlowEnrichmentFunction enrichmentFunction) {
        return enrichmentFunction::processMessage;
    }

    /**
     * Periodically purges TTL-expired entries from the InterfaceMarkingCache
     * to bound memory growth.
     */
    @Bean
    InterfaceMarkingCacheCleaner interfaceMarkingCacheCleaner(InterfaceMarkingCache cache) {
        return new InterfaceMarkingCacheCleaner(cache);
    }

    /**
     * Trivial bean wrapper that owns the {@link Scheduled} hook. Defining the
     * scheduled method on the {@code @Configuration} class itself is awkward
     * because Spring proxies it; an explicit holder bean keeps the wiring
     * straightforward and easy to mock in tests.
     */
    public static class InterfaceMarkingCacheCleaner {
        private final InterfaceMarkingCache cache;

        InterfaceMarkingCacheCleaner(InterfaceMarkingCache cache) {
            this.cache = cache;
        }

        @Scheduled(fixedRateString = "${deltav.flows.interface-marking.clean-interval-ms:3600000}")
        public void clean() {
            cache.cleanExpired();
        }
    }
}
