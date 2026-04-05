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
package org.opennms.core.daemon.registry;

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
