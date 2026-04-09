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
package org.deltav.minion.boot;

import org.opennms.netmgt.snmp.proxy.common.SnmpProxyRpcModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the SNMP proxy RPC module (module ID "SNMP").
 *
 * <p>Executes SNMP GET/SET/WALK requests locally on the Minion using
 * the current SnmpStrategy. Replaces the Karaf blueprint that registered
 * {@code SnmpProxyRpcModule.INSTANCE} into the OSGi service registry.</p>
 */
@Configuration
@ConditionalOnProperty(name = "opennms.minion.snmp.enabled", havingValue = "true", matchIfMissing = true)
public class SnmpProxyConfiguration {

    @Bean
    public SnmpProxyRpcModule snmpProxyRpcModule() {
        return SnmpProxyRpcModule.INSTANCE;
    }
}
