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
package org.deltav.flows.enricher.enrichment;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Classifies IPv4/IPv6 addresses as PRIVATE (RFC1918, loopback, link-local,
 * site-local) or PUBLIC for flow locality enrichment.
 *
 * <p>Stateless and thread-safe.
 */
public class FlowLocalityCalculator {

    public enum Locality {
        UNKNOWN,
        PRIVATE,
        PUBLIC
    }

    public Locality classify(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return Locality.UNKNOWN;
        }
        final InetAddress addr;
        try {
            addr = InetAddress.getByName(ipAddress);
        } catch (UnknownHostException e) {
            return Locality.UNKNOWN;
        }
        if (addr.isLoopbackAddress() || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()) {
            return Locality.PRIVATE;
        }
        // InetAddress.isSiteLocalAddress() covers 10/8 and 192.168/16 but only
        // covers 172.16/12 partially in some JDK versions; check explicitly.
        byte[] bytes = addr.getAddress();
        if (bytes.length == 4
                && (bytes[0] & 0xFF) == 172
                && (bytes[1] & 0xFF) >= 16
                && (bytes[1] & 0xFF) <= 31) {
            return Locality.PRIVATE;
        }
        return Locality.PUBLIC;
    }

    public Locality flowLocality(String srcAddress, String dstAddress) {
        Locality src = classify(srcAddress);
        Locality dst = classify(dstAddress);
        if (src == Locality.UNKNOWN || dst == Locality.UNKNOWN) {
            return Locality.UNKNOWN;
        }
        if (src == Locality.PUBLIC || dst == Locality.PUBLIC) {
            return Locality.PUBLIC;
        }
        return Locality.PRIVATE;
    }
}
