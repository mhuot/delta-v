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

import java.util.Date;
import java.util.List;

import org.deltav.timeseries.proto.Attribute;
import org.deltav.timeseries.proto.AttributeType;
import org.deltav.timeseries.proto.ProducerType;
import org.deltav.timeseries.proto.TimeseriesBatch;
import org.junit.jupiter.api.Test;
import org.opennms.netmgt.collection.api.CollectionAgent;
import org.opennms.netmgt.collection.api.CollectionAttribute;
import org.opennms.netmgt.collection.api.CollectionResource;
import org.opennms.netmgt.collection.api.CollectionSet;
import org.opennms.netmgt.collection.api.CollectionStatus;

class CollectionSetToProtobufTranslatorTest {

    private final CollectionSetToProtobufTranslator translator = new CollectionSetToProtobufTranslator();

    @Test
    void emptyCollectionSetProducesEmptyResourceList() {
        CollectionSet set = mockCollectionSet(CollectionStatus.SUCCEEDED, 1700000000000L);

        TimeseriesBatch batch = translator.translate(set, "default", 0, "");

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

    @Test
    void gauge32AttributeMapsToNumericDouble() {
        CollectionAttribute attr = numericAttribute("sysUpTime",
                org.opennms.netmgt.collection.api.AttributeType.GAUGE, 123456);
        CollectionSet set = oneResourceSet(7, "Default", "node", null, "mib2-system",
                List.of(attr), 1700000000000L);

        TimeseriesBatch batch = translator.translate(set, "default", 7, "Default");

        assertThat(batch.getResourcesCount()).isEqualTo(1);
        Attribute out = batch.getResources(0).getGroups(0).getAttributes(0);
        assertThat(out.getType()).isEqualTo(AttributeType.ATTRIBUTE_TYPE_GAUGE);
        assertThat(out.getNumeric()).isEqualTo(123456.0d);
    }

    @Test
    void gauge64AttributeMapsToNumericDouble() {
        CollectionAttribute attr = numericAttribute("ifHCInOctets",
                org.opennms.netmgt.collection.api.AttributeType.GAUGE, 12345678901234L);
        CollectionSet set = oneResourceSet(7, "Default", "interfaceSnmp", "eth0",
                "mib2-interfaces", List.of(attr), 1700000000000L);

        TimeseriesBatch batch = translator.translate(set, "default", 7, "Default");

        Attribute out = batch.getResources(0).getGroups(0).getAttributes(0);
        assertThat(out.getNumeric()).isEqualTo(12345678901234.0d);
    }

    @Test
    void counter64AttributeMapsToCounterType() {
        CollectionAttribute attr = numericAttribute("ifHCInOctets",
                org.opennms.netmgt.collection.api.AttributeType.COUNTER, 98765432109876L);
        CollectionSet set = oneResourceSet(7, "Default", "interfaceSnmp", "eth0",
                "mib2-interfaces", List.of(attr), 1700000000000L);

        TimeseriesBatch batch = translator.translate(set, "default", 7, "Default");

        Attribute out = batch.getResources(0).getGroups(0).getAttributes(0);
        assertThat(out.getType()).isEqualTo(AttributeType.ATTRIBUTE_TYPE_COUNTER);
        assertThat(out.getNumeric()).isEqualTo(98765432109876.0d);
    }

    @Test
    void negativeAndZeroNumericValuesAreCarriedUnchanged() {
        CollectionAttribute neg = numericAttribute("someNeg",
                org.opennms.netmgt.collection.api.AttributeType.GAUGE, -42);
        CollectionAttribute zero = numericAttribute("someZero",
                org.opennms.netmgt.collection.api.AttributeType.GAUGE, 0);
        CollectionSet set = oneResourceSet(7, "Default", "node", null, "edge-cases",
                List.of(neg, zero), 1700000000000L);

        TimeseriesBatch batch = translator.translate(set, "default", 7, "Default");

        assertThat(batch.getResources(0).getGroups(0).getAttributesList())
                .extracting(Attribute::getNumeric)
                .containsExactly(-42.0d, 0.0d);
    }

    static CollectionSet oneResourceSet(int nodeId, String location, String resourceType,
                                         String instance, String groupName,
                                         List<CollectionAttribute> attributes,
                                         long timestampMs) {
        CollectionResource resource = mock(CollectionResource.class);
        when(resource.getResourceTypeName()).thenReturn(resourceType);
        when(resource.getInstance()).thenReturn(instance);
        when(resource.getParent()).thenReturn(
                org.opennms.netmgt.model.ResourcePath.get("node[" + nodeId + "]"));

        org.opennms.netmgt.collection.api.AttributeGroup group =
                mock(org.opennms.netmgt.collection.api.AttributeGroup.class);
        when(group.getName()).thenReturn(groupName);

        CollectionSet set = mock(CollectionSet.class);
        when(set.getStatus()).thenReturn(CollectionStatus.SUCCEEDED);
        when(set.getCollectionTimestamp()).thenReturn(new Date(timestampMs));
        org.mockito.Mockito.doAnswer(inv -> {
            org.opennms.netmgt.collection.api.CollectionSetVisitor v = inv.getArgument(0);
            v.visitCollectionSet(set);
            v.visitResource(resource);
            v.visitGroup(group);
            attributes.forEach(a -> {
                when(a.getResource()).thenReturn(resource);
                v.visitAttribute(a);
                v.completeAttribute(a);
            });
            v.completeGroup(group);
            v.completeResource(resource);
            v.completeCollectionSet(set);
            return null;
        }).when(set).visit(org.mockito.ArgumentMatchers.any());
        return set;
    }

    static CollectionAttribute numericAttribute(String name,
                                                 org.opennms.netmgt.collection.api.AttributeType type,
                                                 Number value) {
        CollectionAttribute attr = mock(CollectionAttribute.class);
        when(attr.getName()).thenReturn(name);
        when(attr.getType()).thenReturn(type);
        when(attr.getNumericValue()).thenReturn(value);
        return attr;
    }

    static CollectionAttribute stringAttribute(String name, String value) {
        CollectionAttribute attr = mock(CollectionAttribute.class);
        when(attr.getName()).thenReturn(name);
        when(attr.getType()).thenReturn(org.opennms.netmgt.collection.api.AttributeType.STRING);
        when(attr.getStringValue()).thenReturn(value);
        return attr;
    }
}
