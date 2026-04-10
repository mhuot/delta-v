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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FlowLocalityCalculatorTest {

    private final FlowLocalityCalculator calculator = new FlowLocalityCalculator();

    @Test
    void rfc1918AddressesArePrivate() {
        assertThat(calculator.classify("10.0.0.1")).isEqualTo(FlowLocalityCalculator.Locality.PRIVATE);
        assertThat(calculator.classify("172.16.5.1")).isEqualTo(FlowLocalityCalculator.Locality.PRIVATE);
        assertThat(calculator.classify("172.31.255.254")).isEqualTo(FlowLocalityCalculator.Locality.PRIVATE);
        assertThat(calculator.classify("192.168.1.1")).isEqualTo(FlowLocalityCalculator.Locality.PRIVATE);
    }

    @Test
    void loopbackIsPrivate() {
        assertThat(calculator.classify("127.0.0.1")).isEqualTo(FlowLocalityCalculator.Locality.PRIVATE);
    }

    @Test
    void publicAddressesArePublic() {
        assertThat(calculator.classify("8.8.8.8")).isEqualTo(FlowLocalityCalculator.Locality.PUBLIC);
        assertThat(calculator.classify("1.1.1.1")).isEqualTo(FlowLocalityCalculator.Locality.PUBLIC);
        // Just outside the 172.16.0.0/12 range — must be PUBLIC, not PRIVATE
        assertThat(calculator.classify("172.32.0.1")).isEqualTo(FlowLocalityCalculator.Locality.PUBLIC);
    }

    @Test
    void nullOrEmptyOrInvalidIsUnknown() {
        assertThat(calculator.classify(null)).isEqualTo(FlowLocalityCalculator.Locality.UNKNOWN);
        assertThat(calculator.classify("")).isEqualTo(FlowLocalityCalculator.Locality.UNKNOWN);
        assertThat(calculator.classify("not-an-ip")).isEqualTo(FlowLocalityCalculator.Locality.UNKNOWN);
    }

    @Test
    void flowLocalityIsPublicIfEitherEndpointIsPublic() {
        assertThat(calculator.flowLocality("10.0.0.1", "8.8.8.8"))
                .isEqualTo(FlowLocalityCalculator.Locality.PUBLIC);
        assertThat(calculator.flowLocality("8.8.8.8", "10.0.0.1"))
                .isEqualTo(FlowLocalityCalculator.Locality.PUBLIC);
    }

    @Test
    void flowLocalityIsPrivateWhenBothEndpointsArePrivate() {
        assertThat(calculator.flowLocality("10.0.0.1", "192.168.1.1"))
                .isEqualTo(FlowLocalityCalculator.Locality.PRIVATE);
    }

    @Test
    void flowLocalityIsUnknownIfEitherEndpointIsUnknown() {
        assertThat(calculator.flowLocality(null, "10.0.0.1"))
                .isEqualTo(FlowLocalityCalculator.Locality.UNKNOWN);
        assertThat(calculator.flowLocality("10.0.0.1", "garbage"))
                .isEqualTo(FlowLocalityCalculator.Locality.UNKNOWN);
    }
}
