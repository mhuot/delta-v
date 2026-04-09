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

import org.opennms.core.rpc.echo.EchoRpcModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Echo RPC module (module ID "Echo").
 *
 * <p>Always enabled (no conditional) because Echo is required for
 * Minion health checks from the core instance.</p>
 */
@Configuration
public class EchoRpcConfiguration {

    @Bean
    public EchoRpcModule echoRpcModule() {
        return new EchoRpcModule();
    }
}
