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
package org.deltav.netmgt.telemetry.boot;

import org.opennms.core.ipc.sink.api.Message;
import org.opennms.core.ipc.sink.api.SinkModule;
import org.opennms.core.ipc.sink.common.AbstractMessageConsumerManager;
import org.opennms.core.ipc.sink.common.AbstractMessageDispatcherFactory;
import org.osgi.framework.BundleContext;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import com.codahale.metrics.MetricRegistry;

import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;

/**
 * In-process MessageDispatcherFactory for the Telemetryd standalone container.
 *
 * Dispatched messages are delivered directly to the {@link TelemetryMessageConsumerManager}
 * in the same JVM — no remote transport, no serialization.
 */
public class LocalMessageDispatcherFactory extends AbstractMessageDispatcherFactory<Void>
        implements InitializingBean, DisposableBean {

    private final AbstractMessageConsumerManager consumerManager;
    private final MetricRegistry metrics = new MetricRegistry();

    public LocalMessageDispatcherFactory(AbstractMessageConsumerManager consumerManager) {
        this.consumerManager = consumerManager;
    }

    @Override
    public <S extends Message, T extends Message> void dispatch(SinkModule<S, T> module, Void metadata, T message) {
        consumerManager.dispatch(module, message);
    }

    @Override
    public String getMetricDomain() {
        return LocalMessageDispatcherFactory.class.getPackage().getName();
    }

    @Override
    public BundleContext getBundleContext() {
        return null;
    }

    @Override
    public Tracer getTracer() {
        return GlobalTracer.get();
    }

    @Override
    public MetricRegistry getMetrics() {
        return metrics;
    }

    @Override
    public void afterPropertiesSet() {
        onInit();
    }

    @Override
    public void destroy() {
        onDestroy();
    }
}
