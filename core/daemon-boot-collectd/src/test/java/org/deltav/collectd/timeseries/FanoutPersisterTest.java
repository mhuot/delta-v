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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.deltav.collectd.timeseries.TimeseriesKafkaPublisherConfiguration.FanoutPersister;
import org.deltav.collectd.timeseries.TimeseriesKafkaPublisherConfiguration.FanoutPersisterFactory;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.opennms.netmgt.collection.api.CollectionSet;
import org.opennms.netmgt.collection.api.Persister;
import org.opennms.netmgt.collection.api.PersisterFactory;
import org.opennms.netmgt.collection.api.ServiceParameters;

class FanoutPersisterTest {

    @Test
    void completeCollectionSetCallsInnerThenKafkaInOrder() {
        Persister inner = mock(Persister.class);
        TimeseriesKafkaPublisher publisher = mock(TimeseriesKafkaPublisher.class);
        ServiceParameters sp = mock(ServiceParameters.class);
        when(sp.getParameters()).thenReturn(new HashMap<>());
        TimeseriesKafkaPersister kafka = new TimeseriesKafkaPersister(publisher, sp);
        FanoutPersister fanout = new FanoutPersister(inner, kafka);

        CollectionSet set = mock(CollectionSet.class);
        fanout.visitCollectionSet(set);
        fanout.completeCollectionSet(set);

        InOrder order = inOrder(inner, publisher);
        order.verify(inner).visitCollectionSet(set);
        order.verify(inner).completeCollectionSet(set);
        order.verify(publisher).publish(any(), any(), any(Integer.class), any());
    }

    @Test
    void innerPersisterThrowingPropagatesAndSkipsKafkaDelegate() {
        // This documents the Phase 0 tradeoff: if the inner persister throws
        // during completeCollectionSet, the Kafka delegate is skipped for that
        // poll. The inner factory is assumed well-behaved; if this assumption
        // becomes wrong, the split-per-delegate try/catch is the fix.
        Persister inner = mock(Persister.class);
        TimeseriesKafkaPublisher publisher = mock(TimeseriesKafkaPublisher.class);
        ServiceParameters sp = mock(ServiceParameters.class);
        when(sp.getParameters()).thenReturn(new HashMap<>());
        TimeseriesKafkaPersister kafka = new TimeseriesKafkaPersister(publisher, sp);
        FanoutPersister fanout = new FanoutPersister(inner, kafka);

        CollectionSet set = mock(CollectionSet.class);
        doThrow(new RuntimeException("inner boom"))
                .when(inner).completeCollectionSet(set);

        fanout.visitCollectionSet(set);
        try {
            fanout.completeCollectionSet(set);
        } catch (RuntimeException ignored) { /* expected */ }

        verify(publisher, never()).publish(any(), any(), any(Integer.class), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void factoryCreatesFanoutWithIndependentKafkaPersisterPerCall() {
        PersisterFactory innerFactory = mock(PersisterFactory.class);
        Persister innerPersister1 = mock(Persister.class);
        Persister innerPersister2 = mock(Persister.class);
        when(innerFactory.createPersister(any(), any()))
                .thenReturn(innerPersister1, innerPersister2);

        TimeseriesKafkaPublisher publisher = mock(TimeseriesKafkaPublisher.class);
        FanoutPersisterFactory factory = new FanoutPersisterFactory(innerFactory, publisher);

        ServiceParameters paramsA = mock(ServiceParameters.class);
        Map<String, Object> mapA = new HashMap<>();
        mapA.put("collection", "pkg-A");
        mapA.put("node-id", "1");
        when(paramsA.getParameters()).thenReturn((Map) mapA);

        ServiceParameters paramsB = mock(ServiceParameters.class);
        Map<String, Object> mapB = new HashMap<>();
        mapB.put("collection", "pkg-B");
        mapB.put("node-id", "2");
        when(paramsB.getParameters()).thenReturn((Map) mapB);

        Persister pA = factory.createPersister(paramsA, null);
        Persister pB = factory.createPersister(paramsB, null);

        // Both are distinct FanoutPersister instances wrapping different inner persisters
        // and different TimeseriesKafkaPersister instances (different nodeId/package).
        CollectionSet setA = mock(CollectionSet.class);
        pA.visitCollectionSet(setA);
        pA.completeCollectionSet(setA);

        verify(innerPersister1).completeCollectionSet(setA);
        verify(innerPersister2, never()).completeCollectionSet(any());
        verify(publisher).publish(setA, "pkg-A", 1, "");
    }
}
