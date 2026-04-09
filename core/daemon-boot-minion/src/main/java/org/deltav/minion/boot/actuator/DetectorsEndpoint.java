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

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.opennms.netmgt.provision.detector.registry.api.ServiceDetectorRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

/**
 * Custom actuator endpoint at {@code /actuator/detectors} listing all registered service detectors.
 *
 * <p>Replaces the Karaf shell command {@code list-detectors}. The registry is optional
 * since the detector subsystem may be disabled via {@code opennms.minion.detector.enabled=false}.</p>
 */
@Component
@Endpoint(id = "detectors")
public class DetectorsEndpoint {

    private final ServiceDetectorRegistry registry;

    @Autowired(required = false)
    public DetectorsEndpoint(ServiceDetectorRegistry registry) {
        this.registry = registry;
    }

    @ReadOperation
    public Map<String, Object> detectors() {
        if (registry == null) {
            return Map.of(
                "count", 0,
                "serviceNames", Collections.emptySet()
            );
        }
        Set<String> serviceNames = registry.getServiceNames();
        return Map.of(
            "count", serviceNames.size(),
            "serviceNames", serviceNames
        );
    }
}
