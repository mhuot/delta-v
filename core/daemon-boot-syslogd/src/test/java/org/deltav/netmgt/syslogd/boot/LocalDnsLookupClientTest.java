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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;

import org.junit.jupiter.api.Test;

class LocalDnsLookupClientTest {

    private final LocalDnsLookupClient client = new LocalDnsLookupClient();

    @Test
    void lookupResolvesHostname() throws Exception {
        String result = client.lookup("localhost", "Default").get();
        assertThat(result).isNotNull();
    }

    @Test
    void lookupWithSystemIdDelegates() throws Exception {
        String result = client.lookup("localhost", "Default", "sys-1").get();
        assertThat(result).isNotNull();
    }

    @Test
    void reverseLookupResolvesAddress() throws Exception {
        InetAddress addr = InetAddress.getByName("127.0.0.1");
        String result = client.reverseLookup(addr, "Default").get();
        assertThat(result).isNotNull();
    }

    @Test
    void reverseLookupWithSystemIdDelegates() throws Exception {
        InetAddress addr = InetAddress.getByName("127.0.0.1");
        String result = client.reverseLookup(addr, "Default", "sys-1").get();
        assertThat(result).isNotNull();
    }
}
