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
package org.deltav.core.event.forwarder.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.opennms.core.tsid.TsidFactory;
import org.opennms.netmgt.eventd.processor.TsidAssigner;
import org.opennms.netmgt.eventd.router.EventClassifier;

/**
 * Blueprint factory for creating {@link KafkaEventForwarder} instances.
 *
 * <p>Aries Blueprint 1.10.3 can't match constructor/factory-method args when
 * parameters include interfaces with multiple implementations (EventProcessor),
 * generic types (KafkaProducer), or cross-bundle types loaded from different
 * OSGi classloaders. This factory takes only String args and creates all
 * internal objects directly, bypassing Blueprint type matching.</p>
 */
public class KafkaEventForwarderFactory {

    private KafkaEventForwarderFactory() {
        // static factory — prevent instantiation
    }

    /**
     * Creates a fully-configured {@link KafkaEventForwarder} with no-op event
     * expansion (for daemon containers where eventconf is unavailable).
     *
     * @param bootstrapServers Kafka broker addresses
     * @param topicName        Kafka topic for fault events
     * @return configured KafkaEventForwarder; caller should set IPC topic via setter
     */
    public static KafkaEventForwarder create(String bootstrapServers, String topicName) {
        KafkaProducer<Long, byte[]> kafkaProducer = KafkaProducerFactory.create(bootstrapServers);

        return new KafkaEventForwarder(
                new NoOpEventProcessor(),
                new TsidAssigner(new TsidFactory(0L)),
                new EventClassifier(),
                kafkaProducer,
                topicName
        );
    }
}
