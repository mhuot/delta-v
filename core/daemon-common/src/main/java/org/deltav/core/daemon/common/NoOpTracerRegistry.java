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
package org.deltav.core.daemon.common;

import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import org.opennms.core.tracing.api.TracerRegistry;

/**
 * No-op TracerRegistry for Spring Boot daemon containers.
 * Returns the GlobalTracer (which defaults to NoopTracer).
 * Satisfies KafkaRpcClientFactory's @Autowired TracerRegistry.
 */
public class NoOpTracerRegistry implements TracerRegistry {

    @Override
    public Tracer getTracer() {
        return GlobalTracer.get();
    }

    @Override
    public void init(String serviceName) {
        // No-op — Spring Boot daemon containers don't use distributed tracing
    }
}
