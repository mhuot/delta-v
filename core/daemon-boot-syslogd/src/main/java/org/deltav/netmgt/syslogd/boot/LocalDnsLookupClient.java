package org.opennms.netmgt.syslogd.boot;

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
