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

import org.opennms.distributed.core.api.MinionIdentity;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

/**
 * Contributes Minion identity information to the {@code /actuator/info} endpoint.
 *
 * <p>Replaces the Karaf shell command {@code opennms:id}.</p>
 */
@Component
public class MinionInfoContributor implements InfoContributor {

    private final MinionIdentity identity;

    public MinionInfoContributor(MinionIdentity identity) {
        this.identity = identity;
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("minion", Map.of(
            "id", identity.getId(),
            "location", identity.getLocation(),
            "type", identity.getType()
        ));
    }
}
