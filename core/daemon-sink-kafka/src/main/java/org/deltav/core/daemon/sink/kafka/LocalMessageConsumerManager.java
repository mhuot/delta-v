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
package org.deltav.core.daemon.sink.kafka;

import org.opennms.core.ipc.sink.api.Message;
import org.opennms.core.ipc.sink.api.SinkModule;
import org.opennms.core.ipc.sink.common.AbstractMessageConsumerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-process MessageConsumerManager for Spring Boot daemon containers.
 *
 * <p>When a consumer registers (e.g., TrapSinkConsumer), this manager notifies
 * the {@link KafkaSinkBridge} with the registered module so it can start
 * consuming from the Kafka Sink topic.</p>
 */
public class LocalMessageConsumerManager extends AbstractMessageConsumerManager {

    private static final Logger LOG = LoggerFactory.getLogger(LocalMessageConsumerManager.class);

    private KafkaSinkBridge kafkaSinkBridge;

    public void setKafkaSinkBridge(KafkaSinkBridge kafkaSinkBridge) {
        this.kafkaSinkBridge = kafkaSinkBridge;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void startConsumingForModule(SinkModule<?, Message> module) {
        LOG.info("Local sink consumer started for module: {}", module.getId());
        if (kafkaSinkBridge != null) {
            kafkaSinkBridge.setModule(module);
        }
    }

    @Override
    protected void stopConsumingForModule(SinkModule<?, Message> module) {
        LOG.info("Local sink consumer stopped for module: {}", module.getId());
    }
}
