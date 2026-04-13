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

import java.net.InetAddress;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.opennms.netmgt.dnsresolver.api.DnsResolver;

/**
 * A {@link DnsResolver} that returns an empty {@link Optional} for every
 * lookup. The flow-enricher's downstream enrichment code performs its own
 * node lookups against the ipinterface table, so parser-level DNS resolution
 * is unnecessary here.
 */
public class NoOpDnsResolver implements DnsResolver {

    @Override
    public CompletableFuture<Optional<InetAddress>> lookup(String hostname) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<Optional<String>> reverseLookup(InetAddress inetAddress) {
        return CompletableFuture.completedFuture(Optional.empty());
    }
}
