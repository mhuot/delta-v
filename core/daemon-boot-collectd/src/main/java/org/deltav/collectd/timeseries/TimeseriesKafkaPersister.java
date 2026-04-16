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

import java.util.Map;

import org.opennms.netmgt.collection.api.AttributeGroup;
import org.opennms.netmgt.collection.api.CollectionAttribute;
import org.opennms.netmgt.collection.api.CollectionResource;
import org.opennms.netmgt.collection.api.CollectionSet;
import org.opennms.netmgt.collection.api.Persister;
import org.opennms.netmgt.collection.api.ServiceParameters;

/**
 * Thin horizon Persister adapter that hands every CollectionSet to the
 * shared TimeseriesKafkaPublisher once, at completeCollectionSet(). All
 * per-attribute visit/persistNumericAttribute/persistStringAttribute
 * callbacks are no-ops — the translator does its own walk.
 *
 * <p>nodeId and location are extracted from ServiceParameters at
 * construction time: keys {@code node-id} (parsed as int, falls back to 0
 * on parse failure) and {@code location} (defaults to empty string).
 * Collectd is responsible for populating these keys; when it doesn't,
 * the produced batch carries the defaults and consumers will fall back
 * to the deltav-node-context GlobalKTable lookup for identity.</p>
 */
public class TimeseriesKafkaPersister implements Persister {

    private final TimeseriesKafkaPublisher publisher;
    private final String collectionPackage;
    private final int nodeId;
    private final String location;
    private CollectionSet capturedSet;

    public TimeseriesKafkaPersister(TimeseriesKafkaPublisher publisher,
                                     ServiceParameters serviceParameters) {
        this.publisher = publisher;
        Map<String, Object> params = serviceParameters.getParameters();
        this.collectionPackage = asString(params.get("collection"), "default");
        this.nodeId = parseIntOrZero(asString(params.get("node-id"), ""));
        this.location = asString(params.get("location"), "");
    }

    private static String asString(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    private static int parseIntOrZero(String value) {
        if (value == null || value.isEmpty()) return 0;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public void visitCollectionSet(CollectionSet set) {
        this.capturedSet = set;
    }

    @Override
    public void visitResource(CollectionResource resource) { /* no-op */ }

    @Override
    public void visitGroup(AttributeGroup group) { /* no-op */ }

    @Override
    public void visitAttribute(CollectionAttribute attribute) { /* no-op */ }

    @Override
    public void completeAttribute(CollectionAttribute attribute) { /* no-op */ }

    @Override
    public void completeGroup(AttributeGroup group) { /* no-op */ }

    @Override
    public void completeResource(CollectionResource resource) { /* no-op */ }

    @Override
    public void completeCollectionSet(CollectionSet set) {
        if (capturedSet != null) {
            publisher.publish(capturedSet, collectionPackage, nodeId, location);
        }
        capturedSet = null;
    }

    @Override
    public void persistNumericAttribute(CollectionAttribute attribute) { /* no-op */ }

    @Override
    public void persistStringAttribute(CollectionAttribute attribute) { /* no-op */ }
}
