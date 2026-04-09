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
package org.deltav.minion.common;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.opennms.core.rpc.api.RpcModule;
import org.springframework.stereotype.Component;

/**
 * Collects all {@link RpcModule} beans and indexes them by module ID.
 *
 * <p>In the Karaf/OSGi world, {@code RpcModule} implementations were
 * discovered dynamically via {@code <reference-list>} with bind/unbind
 * callbacks. In Spring Boot, all {@code RpcModule} beans are injected
 * as a {@code List} and indexed once at construction time.</p>
 */
@Component
public class RpcModuleRegistry {

    private final Map<String, RpcModule<?, ?>> modules;

    @SuppressWarnings("rawtypes")
    public RpcModuleRegistry(List<RpcModule> modules) {
        Map<String, RpcModule<?, ?>> map = new HashMap<>();
        for (RpcModule module : modules) {
            map.put(module.getId(), module);
        }
        this.modules = map;
    }

    public Optional<RpcModule<?, ?>> getModule(String id) {
        return Optional.ofNullable(modules.get(id));
    }

    public Set<String> getModuleIds() {
        return Collections.unmodifiableSet(modules.keySet());
    }
}
