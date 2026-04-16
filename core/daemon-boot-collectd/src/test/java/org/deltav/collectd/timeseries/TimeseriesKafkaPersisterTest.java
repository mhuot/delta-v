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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.opennms.netmgt.collection.api.CollectionSet;
import org.opennms.netmgt.collection.api.ServiceParameters;

class TimeseriesKafkaPersisterTest {

    @Test
    void completeCollectionSetInvokesPublisherWithPackageAndServiceParamsIdentity() {
        TimeseriesKafkaPublisher publisher = mock(TimeseriesKafkaPublisher.class);
        ServiceParameters sp = mock(ServiceParameters.class);
        Map<String, Object> params = new HashMap<>();
        params.put("collection", "critical-infra");
        params.put("node-id", "42");
        params.put("location", "Site-A");
        when(sp.getParameters()).thenReturn((Map) params);
        TimeseriesKafkaPersister persister = new TimeseriesKafkaPersister(publisher, sp);

        CollectionSet set = mock(CollectionSet.class);
        persister.visitCollectionSet(set);
        persister.completeCollectionSet(set);

        verify(publisher).publish(eq(set), eq("critical-infra"), eq(42), eq("Site-A"));
    }

    @Test
    void packageDefaultsToDefaultWhenServiceParameterMissing() {
        TimeseriesKafkaPublisher publisher = mock(TimeseriesKafkaPublisher.class);
        ServiceParameters sp = mock(ServiceParameters.class);
        when(sp.getParameters()).thenReturn(new HashMap<>());
        TimeseriesKafkaPersister persister = new TimeseriesKafkaPersister(publisher, sp);

        CollectionSet set = mock(CollectionSet.class);
        persister.visitCollectionSet(set);
        persister.completeCollectionSet(set);

        verify(publisher).publish(eq(set), eq("default"), eq(0), eq(""));
    }

    @Test
    void nodeIdAndLocationMissingFromParamsFallBackToZeroAndEmpty() {
        TimeseriesKafkaPublisher publisher = mock(TimeseriesKafkaPublisher.class);
        ServiceParameters sp = mock(ServiceParameters.class);
        Map<String, Object> params = new HashMap<>();
        params.put("collection", "default");
        when(sp.getParameters()).thenReturn((Map) params);
        TimeseriesKafkaPersister persister = new TimeseriesKafkaPersister(publisher, sp);

        CollectionSet set = mock(CollectionSet.class);
        persister.visitCollectionSet(set);
        persister.completeCollectionSet(set);

        verify(publisher).publish(eq(set), eq("default"), eq(0), eq(""));
    }

    @Test
    void malformedNodeIdParsesToZeroFallback() {
        TimeseriesKafkaPublisher publisher = mock(TimeseriesKafkaPublisher.class);
        ServiceParameters sp = mock(ServiceParameters.class);
        Map<String, Object> params = new HashMap<>();
        params.put("collection", "default");
        params.put("node-id", "not-a-number");
        params.put("location", "Default");
        when(sp.getParameters()).thenReturn((Map) params);
        TimeseriesKafkaPersister persister = new TimeseriesKafkaPersister(publisher, sp);

        CollectionSet set = mock(CollectionSet.class);
        persister.visitCollectionSet(set);
        persister.completeCollectionSet(set);

        verify(publisher).publish(eq(set), eq("default"), eq(0), eq("Default"));
    }

    @Test
    void visitResourceVisitGroupVisitAttributeAreNoOps() {
        TimeseriesKafkaPublisher publisher = mock(TimeseriesKafkaPublisher.class);
        ServiceParameters sp = mock(ServiceParameters.class);
        when(sp.getParameters()).thenReturn(new HashMap<>());
        TimeseriesKafkaPersister persister = new TimeseriesKafkaPersister(publisher, sp);

        persister.visitResource(mock(org.opennms.netmgt.collection.api.CollectionResource.class));
        persister.visitGroup(mock(org.opennms.netmgt.collection.api.AttributeGroup.class));
        persister.visitAttribute(mock(org.opennms.netmgt.collection.api.CollectionAttribute.class));
        persister.completeAttribute(mock(org.opennms.netmgt.collection.api.CollectionAttribute.class));
        persister.completeGroup(mock(org.opennms.netmgt.collection.api.AttributeGroup.class));
        persister.completeResource(mock(org.opennms.netmgt.collection.api.CollectionResource.class));

        verifyNoInteractions(publisher);
    }

    @Test
    void persistNumericAttributeAndPersistStringAttributeAreNoOps() {
        TimeseriesKafkaPublisher publisher = mock(TimeseriesKafkaPublisher.class);
        ServiceParameters sp = mock(ServiceParameters.class);
        when(sp.getParameters()).thenReturn(new HashMap<>());
        TimeseriesKafkaPersister persister = new TimeseriesKafkaPersister(publisher, sp);

        persister.persistNumericAttribute(mock(org.opennms.netmgt.collection.api.CollectionAttribute.class));
        persister.persistStringAttribute(mock(org.opennms.netmgt.collection.api.CollectionAttribute.class));

        verifyNoInteractions(publisher);
    }
}
