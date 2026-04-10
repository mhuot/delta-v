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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.opennms.netmgt.telemetry.config.api.AdapterDefinition;
import org.opennms.netmgt.telemetry.config.api.PackageDefinition;

/**
 * Minimal production implementation of horizon's {@link AdapterDefinition}
 * interface for use by the four Phase 1.5 protocol processors. Returns the
 * supplied name for both {@link #getName()} and {@link #getFullName()} (the
 * adapter constructor uses {@code fullName} for metric-registry keys), and
 * empty collections for everything else.
 *
 * <p>We don't extend horizon's existing {@code AdapterDefinition}
 * implementations because they typically depend on XML-config loading we
 * don't want in flow-enricher. The horizon flow adapters only consult
 * {@code getFullName()} (metric key) and {@code getPackages()} (filter-rule
 * dispatch, disabled here by returning an empty list) from their constructor,
 * so this skeleton is sufficient.
 *
 * <p>The {@link AdapterDefinition} interface extends
 * {@code TelemetryBeanDefinition}, which contributes
 * {@link #getName()}, {@link #getClassName()}, and {@link #getParameterMap()}.
 * All methods are implemented here.
 */
public class SimpleAdapterDefinition implements AdapterDefinition {

    private final String name;

    public SimpleAdapterDefinition(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getFullName() {
        return name;
    }

    @Override
    public String getClassName() {
        return "";
    }

    @Override
    public Map<String, String> getParameterMap() {
        return Collections.emptyMap();
    }

    @Override
    public List<? extends PackageDefinition> getPackages() {
        return Collections.emptyList();
    }
}
