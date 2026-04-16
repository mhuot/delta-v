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
package org.deltav.collectd.timeseries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.deltav.timeseries.proto.ProducerType;
import org.deltav.timeseries.proto.TimeseriesBatch;
import org.junit.jupiter.api.Test;
import org.opennms.netmgt.collection.api.CollectionAgent;
import org.opennms.netmgt.collection.api.CollectionResource;
import org.opennms.netmgt.collection.api.CollectionSet;
import org.opennms.netmgt.collection.api.CollectionStatus;

class CollectionSetToProtobufTranslatorTest {

    private final CollectionSetToProtobufTranslator translator = new CollectionSetToProtobufTranslator();

    /**
     * Option B: CollectionSet has no getAgent() — nodeId and location can only be captured
     * from the visitor callbacks when resources are visited. For an empty CollectionSet the
     * visitor walk produces no resource callbacks, so nodeId stays 0 and location stays "".
     * The translator reads timestamp, status, and package from the set directly.
     */
    @Test
    void emptyCollectionSetProducesEmptyResourceList() {
        CollectionSet set = mockCollectionSet(CollectionStatus.SUCCEEDED, 1700000000000L);

        TimeseriesBatch batch = translator.translate(set, "default");

        assertThat(batch.getNodeId()).isEqualTo(0);
        assertThat(batch.getLocation()).isEqualTo("");
        assertThat(batch.getCollectionPackage()).isEqualTo("default");
        assertThat(batch.getTimestampMs()).isEqualTo(1700000000000L);
        assertThat(batch.getProducer()).isEqualTo(ProducerType.PRODUCER_COLLECTD);
        assertThat(batch.getResourcesList()).isEmpty();
    }

    // Helper factories used by all tests in this class.
    // Keep them here (not in a separate fixture class) so test intent stays
    // one-file-readable; pure boilerplate should not leak into production code.

    static CollectionSet mockCollectionSet(CollectionStatus status, long timestampMs) {
        CollectionSet set = mock(CollectionSet.class);
        when(set.getStatus()).thenReturn(status);
        when(set.getCollectionTimestamp()).thenReturn(new Date(timestampMs));
        return set;
    }

    static CollectionAgent mockAgent(int nodeId, String location) {
        CollectionAgent agent = mock(CollectionAgent.class);
        when(agent.getNodeId()).thenReturn(nodeId);
        when(agent.getLocationName()).thenReturn(location);
        return agent;
    }
}
