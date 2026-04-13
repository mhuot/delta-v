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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class NoOpDnsResolverTest {

    @Test
    void lookupAlwaysReturnsEmptyOptional() throws Exception {
        NoOpDnsResolver resolver = new NoOpDnsResolver();
        Optional<InetAddress> result = resolver.lookup("www.example.com").get();
        assertThat(result).isEmpty();
    }

    @Test
    void reverseLookupAlwaysReturnsEmptyOptional() throws Exception {
        NoOpDnsResolver resolver = new NoOpDnsResolver();
        Optional<String> result = resolver.reverseLookup(InetAddress.getByName("10.0.0.1")).get();
        assertThat(result).isEmpty();
    }
}
