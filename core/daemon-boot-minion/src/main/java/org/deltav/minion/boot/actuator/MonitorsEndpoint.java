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
package org.deltav.minion.boot.actuator;

import java.util.Map;
import java.util.Set;

import org.deltav.minion.common.RpcModuleRegistry;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

/**
 * Custom actuator endpoint at {@code /actuator/monitors} listing all registered RPC modules.
 *
 * <p>Replaces the Karaf shell command {@code list-monitors}.</p>
 */
@Component
@Endpoint(id = "monitors")
public class MonitorsEndpoint {

    private final RpcModuleRegistry registry;

    public MonitorsEndpoint(RpcModuleRegistry registry) {
        this.registry = registry;
    }

    @ReadOperation
    public Map<String, Object> monitors() {
        Set<String> moduleIds = registry.getModuleIds();
        return Map.of(
            "count", moduleIds.size(),
            "modules", moduleIds
        );
    }
}
