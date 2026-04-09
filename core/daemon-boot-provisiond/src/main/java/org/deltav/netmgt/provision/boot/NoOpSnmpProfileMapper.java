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
package org.deltav.netmgt.provision.boot;

import java.net.InetAddress;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.opennms.netmgt.snmp.SnmpAgentConfig;
import org.opennms.netmgt.snmp.SnmpProfileMapper;

/**
 * A no-op implementation of {@link SnmpProfileMapper} that always returns
 * empty results. Used in standalone daemon containers where SNMP profile
 * mapping is not needed (profiles are resolved on core).
 */
public class NoOpSnmpProfileMapper implements SnmpProfileMapper {

    private static final CompletableFuture<Optional<SnmpAgentConfig>> EMPTY =
            CompletableFuture.completedFuture(Optional.empty());

    @Override
    public CompletableFuture<Optional<SnmpAgentConfig>> getAgentConfigFromProfiles(
            InetAddress inetAddress, String location, String oid, boolean metaDataInterpolation) {
        return EMPTY;
    }

    @Override
    public CompletableFuture<Optional<SnmpAgentConfig>> getAgentConfigFromProfiles(
            InetAddress inetAddress, String location, boolean metaDataInterpolation) {
        return EMPTY;
    }

    @Override
    public CompletableFuture<Optional<SnmpAgentConfig>> fitProfile(
            String label, InetAddress inetAddress, String location, String oid) {
        return EMPTY;
    }
}
