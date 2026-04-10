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
package org.deltav.flows.enricher.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.opennms.netmgt.flows.api.Flow;
import org.opennms.netmgt.flows.api.FlowSource;
import org.opennms.netmgt.flows.processing.ProcessingOptions;

class CapturingPipelineTest {

    @Test
    void capturesFlowsAndSourcePassedToProcess() {
        CapturingPipeline pipeline = new CapturingPipeline();
        Flow flow1 = mock(Flow.class);
        Flow flow2 = mock(Flow.class);
        FlowSource source = new FlowSource("Default", "192.0.2.10", null);

        pipeline.process(List.of(flow1, flow2), source, ProcessingOptions.builder().build());

        assertThat(pipeline.getCapturedFlows()).containsExactly(flow1, flow2);
        assertThat(pipeline.getCapturedSource()).isSameAs(source);
    }

    @Test
    void capturedFlowsIsEmptyListBeforeProcessIsCalled() {
        CapturingPipeline pipeline = new CapturingPipeline();

        assertThat(pipeline.getCapturedFlows()).isEmpty();
        assertThat(pipeline.getCapturedSource()).isNull();
    }

    @Test
    void secondProcessCallReplacesFirstCaptures() {
        CapturingPipeline pipeline = new CapturingPipeline();
        Flow flow1 = mock(Flow.class);
        Flow flow2 = mock(Flow.class);
        FlowSource source1 = new FlowSource("LocA", "192.0.2.10", null);
        FlowSource source2 = new FlowSource("LocB", "192.0.2.20", null);

        pipeline.process(List.of(flow1), source1, ProcessingOptions.builder().build());
        pipeline.process(List.of(flow2), source2, ProcessingOptions.builder().build());

        assertThat(pipeline.getCapturedFlows()).containsExactly(flow2);
        assertThat(pipeline.getCapturedSource()).isSameAs(source2);
    }
}
