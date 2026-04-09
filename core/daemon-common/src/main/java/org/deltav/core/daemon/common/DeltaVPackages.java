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

/**
 * Centralized package name constants for Spring Boot component scanning.
 * Prevents typos across the 14 daemon-boot modules.
 */
public final class DeltaVPackages {

    /** Delta-V shared daemon infrastructure */
    public static final String DAEMON_COMMON = "org.deltav.core.daemon.common";

    /** Delta-V Kafka sink bridge */
    public static final String DAEMON_SINK_KAFKA = "org.deltav.core.daemon.sink.kafka";

    /** model-jakarta JPA DAOs (stays org.opennms — horizon-derived) */
    public static final String MODEL_JAKARTA_DAO = "org.opennms.netmgt.model.jakarta.dao";

    private DeltaVPackages() {}
}
