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
package org.deltav.core.daemon.registry;

import java.util.List;

import org.opennms.netmgt.collectd.SnmpCollector;
import org.opennms.netmgt.collection.api.ServiceCollectorRegistry;
import org.opennms.netmgt.snmp.proxy.LocationAwareSnmpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers SnmpCollector as the sole Delta-V service collector.
 * Import this configuration from any daemon boot config that needs a
 * {@link ServiceCollectorRegistry}.
 */
@Configuration
public class CollectorRegistryConfiguration {

    @Bean
    public SnmpCollector snmpCollector(LocationAwareSnmpClient snmpClient) {
        SnmpCollector collector = new SnmpCollector();
        collector.setLocationAwareSnmpClient(snmpClient);
        return collector;
    }

    @Bean
    public ServiceCollectorRegistry serviceCollectorRegistry(SnmpCollector snmpCollector) {
        return new LocalServiceCollectorRegistry(List.of(snmpCollector));
    }
}
