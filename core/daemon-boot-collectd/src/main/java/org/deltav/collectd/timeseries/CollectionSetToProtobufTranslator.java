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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.deltav.timeseries.proto.Attribute;
import org.deltav.timeseries.proto.AttributeGroup;
import org.deltav.timeseries.proto.AttributeType;
import org.deltav.timeseries.proto.ProducerType;
import org.deltav.timeseries.proto.Resource;
import org.deltav.timeseries.proto.TimeseriesBatch;
import org.opennms.netmgt.collection.api.CollectionAgent;
import org.opennms.netmgt.collection.api.CollectionAttribute;
import org.opennms.netmgt.collection.api.CollectionResource;
import org.opennms.netmgt.collection.api.CollectionSet;
import org.opennms.netmgt.collection.api.CollectionSetVisitor;
import org.opennms.netmgt.collection.api.CollectionStatus;

/**
 * Pure function that walks a horizon CollectionSet visitor tree and emits a
 * TimeseriesBatch protobuf. No side effects, no Spring state, no logging.
 * Callers translate per poll; concurrent calls are safe because each call
 * uses a fresh VisitorState instance.
 *
 * <p>nodeId and location are captured from the first CollectionAgent encountered
 * during the visitor walk (via reflection, because CollectionResource does not
 * expose getAgent() on its public API in all horizon versions). For empty
 * CollectionSets where no resources are visited, nodeId defaults to 0 and
 * location defaults to "".
 */
public class CollectionSetToProtobufTranslator {

    public TimeseriesBatch translate(CollectionSet set, String collectionPackage) {
        if (set == null || set.getStatus() == CollectionStatus.FAILED) {
            return TimeseriesBatch.newBuilder()
                    .setProducer(ProducerType.PRODUCER_COLLECTD)
                    .build();
        }

        VisitorState state = new VisitorState();
        set.visit(state);

        long timestampMs = set.getCollectionTimestamp() != null
                ? set.getCollectionTimestamp().getTime()
                : System.currentTimeMillis();

        TimeseriesBatch.Builder batch = TimeseriesBatch.newBuilder()
                .setTimestampMs(timestampMs)
                .setNodeId(state.nodeId)
                .setLocation(state.location != null ? state.location : "")
                .setCollectionPackage(collectionPackage != null ? collectionPackage : "")
                .setProducer(ProducerType.PRODUCER_COLLECTD);

        for (ResourceAccumulator r : state.resources.values()) {
            Resource.Builder resBuilder = Resource.newBuilder()
                    .setResourceId(r.resourceId)
                    .setType(r.type)
                    .setInstance(r.instance);
            for (AttributeGroupAccumulator g : r.groups.values()) {
                if (g.attributes.isEmpty()) {
                    continue;
                }
                AttributeGroup.Builder groupBuilder = AttributeGroup.newBuilder().setName(g.name);
                g.attributes.forEach(groupBuilder::addAttributes);
                resBuilder.addGroups(groupBuilder);
            }
            if (resBuilder.getGroupsCount() > 0) {
                batch.addResources(resBuilder);
            }
        }

        return batch.build();
    }

    private static class VisitorState implements CollectionSetVisitor {
        int nodeId;
        String location;
        final Map<String, ResourceAccumulator> resources = new LinkedHashMap<>();
        ResourceAccumulator currentResource;
        AttributeGroupAccumulator currentGroup;

        @Override
        public void visitCollectionSet(CollectionSet set) {
            // no-op: the outer translate() reads timestamp/status directly from the set
        }

        @Override
        public void visitResource(CollectionResource resource) {
            CollectionAgent agent = resourceAgent(resource);
            if (agent != null) {
                nodeId = agent.getNodeId();
                location = agent.getLocationName();
            }
            String parent = String.valueOf(resource.getParent());
            String typeName = resource.getResourceTypeName();
            String inst = resource.getInstance();
            String resourceId = (inst != null && !inst.isEmpty())
                    ? parent + "." + typeName + "[" + inst + "]"
                    : parent + "." + typeName;
            currentResource = resources.computeIfAbsent(resourceId,
                    id -> new ResourceAccumulator(id, typeName, inst != null ? inst : ""));
        }

        @Override
        public void visitGroup(org.opennms.netmgt.collection.api.AttributeGroup group) {
            if (currentResource == null) {
                return;
            }
            currentGroup = currentResource.groups.computeIfAbsent(group.getName(),
                    AttributeGroupAccumulator::new);
        }

        @Override
        public void visitAttribute(CollectionAttribute attribute) {
            if (currentGroup == null) {
                return;
            }
            currentGroup.attributes.add(attributeToProto(attribute));
        }

        @Override
        public void completeAttribute(CollectionAttribute attribute) {
            // no-op
        }

        @Override
        public void completeGroup(org.opennms.netmgt.collection.api.AttributeGroup group) {
            currentGroup = null;
        }

        @Override
        public void completeResource(CollectionResource resource) {
            currentResource = null;
        }

        @Override
        public void completeCollectionSet(CollectionSet set) {
            // no-op
        }

        private static CollectionAgent resourceAgent(CollectionResource resource) {
            // horizon CollectionResource does not expose getAgent() on the public
            // interface in all versions. Try reflectively; if absent, return null
            // and let the caller keep nodeId/location at their defaults.
            try {
                java.lang.reflect.Method m = resource.getClass().getMethod("getAgent");
                Object val = m.invoke(resource);
                return val instanceof CollectionAgent ? (CollectionAgent) val : null;
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }
    }

    private static Attribute attributeToProto(CollectionAttribute attribute) {
        Attribute.Builder builder = Attribute.newBuilder().setName(attribute.getName());
        org.opennms.netmgt.collection.api.AttributeType t = attribute.getType();
        if (t == org.opennms.netmgt.collection.api.AttributeType.STRING) {
            String v = attribute.getStringValue();
            builder.setText(v != null ? v : "").setType(AttributeType.ATTRIBUTE_TYPE_STRING);
        } else if (t == org.opennms.netmgt.collection.api.AttributeType.COUNTER) {
            Number n = attribute.getNumericValue();
            builder.setNumeric(n != null ? n.doubleValue() : 0.0d)
                   .setType(AttributeType.ATTRIBUTE_TYPE_COUNTER);
        } else {
            Number n = attribute.getNumericValue();
            builder.setNumeric(n != null ? n.doubleValue() : 0.0d)
                   .setType(AttributeType.ATTRIBUTE_TYPE_GAUGE);
        }
        return builder.build();
    }

    private static class ResourceAccumulator {
        final String resourceId;
        final String type;
        final String instance;
        final Map<String, AttributeGroupAccumulator> groups = new LinkedHashMap<>();

        ResourceAccumulator(String resourceId, String type, String instance) {
            this.resourceId = resourceId;
            this.type = type;
            this.instance = instance;
        }
    }

    private static class AttributeGroupAccumulator {
        final String name;
        final List<Attribute> attributes = new ArrayList<>();

        AttributeGroupAccumulator(String name) {
            this.name = name;
        }
    }
}
