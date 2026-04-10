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

/**
 * Classifies a flow into a named application (for example {@code "HTTPS"} or
 * {@code "DNS"}) from its transport ports and IP protocol. Returns the string
 * {@code "unknown"} when no classification rule matches.
 *
 * <p>Implementations should be stateless and thread-safe.
 */
public interface ApplicationClassifier {

    /**
     * @param dstPort  the destination port as seen in the flow record
     * @param srcPort  the source port as seen in the flow record
     * @param protocol the IP protocol number (TCP=6, UDP=17, etc.)
     * @return the application name, or {@code "unknown"} if no rule matched
     */
    String classify(int dstPort, int srcPort, int protocol);
}
