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
package org.deltav.flows.enricher.protocol;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Map;

import org.opennms.netmgt.telemetry.config.api.AdapterDefinition;

/**
 * Test helper that builds minimal {@link AdapterDefinition} mocks for the
 * per-protocol processor tests. Horizon's
 * {@code AbstractFlowAdapter} constructor reads {@code getFullName()} (for
 * metric registry key construction) and {@code getPackages()} (for
 * {@code ProcessingOptions} wiring); both are stubbed with sensible defaults
 * here. The other {@code AdapterDefinition} methods
 * ({@code getName()}, {@code getClassName()}, {@code getParameterMap()}) are
 * also stubbed so that any future call site picks up a non-null value.
 */
public final class TestAdapterDefinitions {

    private TestAdapterDefinitions() {
    }

    /**
     * Builds a minimal {@link AdapterDefinition} mock suitable for
     * instantiating any of horizon's flow adapters in a unit test. The
     * returned mock has no packages (empty list) so
     * {@code ProcessingOptions} filtering is disabled.
     *
     * @param name short adapter name used for both {@code getName()} and
     *             {@code getFullName()}; also used as the metric registry
     *             prefix by {@code AbstractFlowAdapter}
     * @return a Mockito mock of {@code AdapterDefinition}
     */
    public static AdapterDefinition testAdapterDefinition(String name) {
        AdapterDefinition def = mock(AdapterDefinition.class);
        when(def.getName()).thenReturn(name);
        when(def.getFullName()).thenReturn(name);
        when(def.getClassName()).thenReturn("org.deltav.flows.enricher.protocol.TestAdapter");
        when(def.getParameterMap()).thenReturn(Map.of());
        when(def.getPackages()).thenReturn(Collections.emptyList());
        return def;
    }
}
