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
package org.deltav.flows.enricher.parser;

import java.util.Objects;

import org.opennms.distributed.core.api.Identity;

/**
 * An {@link Identity} whose values are fixed at construction time from
 * configuration properties. The flow-enricher is a single-tenant Spring Boot
 * service; its location and system ID are set at startup and never change,
 * unlike Minion-side identities that are generated per-deployment.
 */
public class StaticIdentity implements Identity {

    private final String id;
    private final String location;
    private final String type;

    public StaticIdentity(String id, String location, String type) {
        this.id = Objects.requireNonNull(id, "id");
        this.location = Objects.requireNonNull(location, "location");
        this.type = Objects.requireNonNull(type, "type");
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getLocation() {
        return location;
    }

    @Override
    public String getType() {
        return type;
    }
}
