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
package org.deltav.netmgt.syslogd.boot;

import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;

import org.opennms.netmgt.provision.LocationAwareDnsLookupClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local DNS lookup client for Spring Boot daemon containers.
 *
 * <p>Performs DNS resolution locally using {@link InetAddress} rather than
 * dispatching via Kafka RPC to a location-aware Minion. Location and systemId
 * parameters are ignored.</p>
 *
 * <p><strong>Known limitation:</strong> gives wrong answers for private IPs
 * from remote Minion networks. Deferred item: have Minion include resolved
 * hostname in Kafka Sink message envelope.</p>
 */
public class LocalDnsLookupClient implements LocationAwareDnsLookupClient {

    private static final Logger LOG = LoggerFactory.getLogger(LocalDnsLookupClient.class);

    @Override
    public CompletableFuture<String> lookup(String hostName, String location) {
        return lookup(hostName, location, null);
    }

    @Override
    public CompletableFuture<String> lookup(String hostName, String location, String systemId) {
        try {
            String address = InetAddress.getByName(hostName).getHostAddress();
            return CompletableFuture.completedFuture(address);
        } catch (Exception e) {
            LOG.debug("DNS lookup failed for {}: {}", hostName, e.getMessage());
            return CompletableFuture.completedFuture(hostName);
        }
    }

    @Override
    public CompletableFuture<String> reverseLookup(InetAddress ipAddress, String location) {
        return reverseLookup(ipAddress, location, null);
    }

    @Override
    public CompletableFuture<String> reverseLookup(InetAddress ipAddress, String location, String systemId) {
        String hostname = ipAddress.getCanonicalHostName();
        return CompletableFuture.completedFuture(hostname);
    }
}
