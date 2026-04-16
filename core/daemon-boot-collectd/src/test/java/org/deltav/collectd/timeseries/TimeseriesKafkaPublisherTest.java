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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.Date;

import org.deltav.timeseries.proto.TimeseriesBatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.opennms.netmgt.collection.api.CollectionSet;
import org.opennms.netmgt.collection.api.CollectionStatus;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;

class TimeseriesKafkaPublisherTest {

    private StreamBridge streamBridge;
    private CollectionSetToProtobufTranslator translator;
    private MeterRegistry meterRegistry;
    private TimeseriesKafkaPublisher publisher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        streamBridge = mock(StreamBridge.class);
        translator = mock(CollectionSetToProtobufTranslator.class);
        meterRegistry = new SimpleMeterRegistry();
        publisher = new TimeseriesKafkaPublisher(streamBridge, translator, meterRegistry);
        when(streamBridge.send(any(String.class), any(Message.class))).thenReturn(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void happyPathSendsOneMessageWithCorrectBindingAndKey() {
        CollectionSet set = mock(CollectionSet.class);
        when(set.getStatus()).thenReturn(CollectionStatus.SUCCEEDED);
        when(set.getCollectionTimestamp()).thenReturn(new Date(1700000000000L));
        TimeseriesBatch batch = TimeseriesBatch.newBuilder()
                .setNodeId(42).setLocation("Site-A").setCollectionPackage("default")
                .setTimestampMs(1700000000000L)
                .addResources(org.deltav.timeseries.proto.Resource.newBuilder()
                        .setResourceId("node[42]").setType("node").build())
                .build();
        when(translator.translate(set, "default", 42, "Site-A")).thenReturn(batch);

        publisher.publish(set, "default", 42, "Site-A");

        ArgumentCaptor<Message<byte[]>> captor = ArgumentCaptor.forClass(Message.class);
        verify(streamBridge).send(eq("publishTimeseries-out-0"), captor.capture());
        Message<byte[]> sent = captor.getValue();
        assertThat(sent.getPayload()).isEqualTo(batch.toByteArray());
        assertThat(sent.getHeaders().get(KafkaHeaders.KEY)).isEqualTo("Site-A@42".getBytes());

        assertThat(meterRegistry.counter("deltav_timeseries_batches_published_total",
                "location", "Site-A", "producer", "collectd").count()).isEqualTo(1.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void emptyBatchSkippedWithoutSend() {
        CollectionSet set = mock(CollectionSet.class);
        when(set.getStatus()).thenReturn(CollectionStatus.SUCCEEDED);
        TimeseriesBatch empty = TimeseriesBatch.newBuilder()
                .setNodeId(42).setLocation("Site-A").build();
        when(translator.translate(set, "default", 42, "Site-A")).thenReturn(empty);

        publisher.publish(set, "default", 42, "Site-A");

        verify(streamBridge, never()).send(any(String.class), any(Message.class));
        assertThat(meterRegistry.counter("deltav_timeseries_batches_failed_total",
                "location", "Site-A", "producer", "collectd", "reason", "empty_batch").count())
                .isEqualTo(1.0);
    }
}
