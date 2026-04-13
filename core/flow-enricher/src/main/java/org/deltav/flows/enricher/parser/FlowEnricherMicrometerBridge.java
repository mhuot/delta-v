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
package org.deltav.flows.enricher.parser;

import com.codahale.metrics.MetricRegistry;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.dropwizard.DropwizardConfig;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;

/**
 * Publishes the shared Dropwizard {@link MetricRegistry} (used by horizon's
 * flow adapters and parsers for their internal timers/meters) into Spring
 * Boot's Micrometer registry, so all parser and adapter metrics are
 * scrapable at {@code /actuator/prometheus}.
 */
public class FlowEnricherMicrometerBridge extends DropwizardMeterRegistry {

    public FlowEnricherMicrometerBridge(MetricRegistry dropwizardRegistry, Clock clock) {
        super(CONFIG, dropwizardRegistry, HierarchicalNameMapper.DEFAULT, clock);
    }

    @Override
    protected Double nullGaugeValue() {
        return Double.NaN;
    }

    private static final DropwizardConfig CONFIG = new DropwizardConfig() {
        @Override
        public String prefix() {
            return "flow_enricher";
        }

        @Override
        public String get(String key) {
            return null;
        }
    };
}
