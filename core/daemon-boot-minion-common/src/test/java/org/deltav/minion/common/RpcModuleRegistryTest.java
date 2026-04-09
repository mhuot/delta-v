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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.opennms.core.rpc.echo.EchoRpcModule;

class RpcModuleRegistryTest {

    @Test
    void registersModulesById() {
        EchoRpcModule echo = new EchoRpcModule();
        RpcModuleRegistry registry = new RpcModuleRegistry(List.of(echo));

        assertThat(registry.getModule("Echo")).isPresent();
        assertThat(registry.getModule("Echo").get()).isSameAs(echo);
        assertThat(registry.getModule("NonExistent")).isEmpty();
    }

    @Test
    void getModuleIdsReturnsAllRegistered() {
        EchoRpcModule echo = new EchoRpcModule();
        RpcModuleRegistry registry = new RpcModuleRegistry(List.of(echo));
        assertThat(registry.getModuleIds()).containsExactly("Echo");
    }

    @Test
    void emptyListProducesEmptyRegistry() {
        RpcModuleRegistry registry = new RpcModuleRegistry(List.of());
        assertThat(registry.getModuleIds()).isEmpty();
    }
}
