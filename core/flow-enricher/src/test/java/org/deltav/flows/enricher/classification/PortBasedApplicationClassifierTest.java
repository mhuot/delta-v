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
package org.deltav.flows.enricher.classification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PortBasedApplicationClassifierTest {

    private static final int TCP = 6;
    private static final int UDP = 17;

    private final PortBasedApplicationClassifier classifier = new PortBasedApplicationClassifier();

    @Test
    void classifiesHttpsByDstPort() {
        assertThat(classifier.classify(443, 51234, TCP)).isEqualTo("HTTPS");
    }

    @Test
    void classifiesHttpsBySrcPortForReturnFlow() {
        assertThat(classifier.classify(51234, 443, TCP)).isEqualTo("HTTPS");
    }

    @Test
    void dstPortTakesPriorityWhenBothPortsAreWellKnown() {
        // dst=443 (HTTPS), src=80 (HTTP) — dst wins.
        assertThat(classifier.classify(443, 80, TCP)).isEqualTo("HTTPS");
    }

    @Test
    void classifiesDnsOverUdp() {
        assertThat(classifier.classify(53, 51234, UDP)).isEqualTo("DNS");
    }

    @Test
    void classifiesSsh() {
        assertThat(classifier.classify(22, 49152, TCP)).isEqualTo("SSH");
    }

    @Test
    void classifiesHttp() {
        assertThat(classifier.classify(80, 49152, TCP)).isEqualTo("HTTP");
    }

    @Test
    void classifiesSmtpOn25And587() {
        assertThat(classifier.classify(25, 49152, TCP)).isEqualTo("SMTP");
        assertThat(classifier.classify(587, 49152, TCP)).isEqualTo("SMTP");
    }

    @Test
    void classifiesDatabasePorts() {
        assertThat(classifier.classify(5432, 49152, TCP)).isEqualTo("PostgreSQL");
        assertThat(classifier.classify(3306, 49152, TCP)).isEqualTo("MySQL");
        assertThat(classifier.classify(6379, 49152, TCP)).isEqualTo("Redis");
    }

    @Test
    void classifiesKafka() {
        assertThat(classifier.classify(9092, 49152, TCP)).isEqualTo("Kafka");
    }

    @Test
    void classifiesElasticsearchAndMongo() {
        assertThat(classifier.classify(9200, 49152, TCP)).isEqualTo("Elasticsearch");
        assertThat(classifier.classify(27017, 49152, TCP)).isEqualTo("MongoDB");
    }

    @Test
    void returnsUnknownWhenNeitherPortIsWellKnown() {
        assertThat(classifier.classify(49152, 51234, TCP)).isEqualTo("unknown");
    }

    @Test
    void classifiesSnmpAndSnmpTrap() {
        assertThat(classifier.classify(161, 49152, UDP)).isEqualTo("SNMP");
        assertThat(classifier.classify(162, 49152, UDP)).isEqualTo("SNMP-trap");
    }
}
