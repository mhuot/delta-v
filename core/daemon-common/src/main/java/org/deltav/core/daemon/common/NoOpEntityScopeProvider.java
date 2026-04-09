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

import java.net.InetAddress;

import org.opennms.core.mate.api.EmptyScope;
import org.opennms.core.mate.api.EntityScopeProvider;
import org.opennms.core.mate.api.Scope;

/**
 * No-op {@link EntityScopeProvider} for Spring Boot daemon containers.
 *
 * <p>Returns empty scopes for all entity types. MATE variable interpolation
 * (e.g., {@code ${scv:credentials:apikey}}) is disabled.</p>
 *
 * <p>Provided as a {@code @ConditionalOnMissingBean} default by
 * {@link DaemonProvisioningConfiguration}. Daemons that need real MATE
 * support (Provisiond, Thresholding, Pollerd, etc.) can override by
 * defining their own {@code EntityScopeProvider} bean.</p>
 */
public class NoOpEntityScopeProvider implements EntityScopeProvider {

    private static final Scope EMPTY = EmptyScope.EMPTY;

    @Override public Scope getScopeForScv() { return EMPTY; }
    @Override public Scope getScopeForEnv() { return EMPTY; }
    @Override public Scope getScopeForNode(Integer nodeId) { return EMPTY; }
    @Override public Scope getScopeForInterface(Integer nodeId, String ipAddress) { return EMPTY; }
    @Override public Scope getScopeForInterfaceByIfIndex(Integer nodeId, int ifIndex) { return EMPTY; }
    @Override public Scope getScopeForService(Integer nodeId, InetAddress ipAddress, String serviceName) { return EMPTY; }
}
