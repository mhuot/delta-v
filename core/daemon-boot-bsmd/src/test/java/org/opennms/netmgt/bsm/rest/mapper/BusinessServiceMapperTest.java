/*
 * Licensed to The OpenNMS Group, Inc (TOG) under one or more
 * contributor license agreements.  See the LICENSE.md file
 * distributed with this work for additional information
 * regarding copyright ownership.
 *
 * TOG licenses this file to You under the GNU Affero General
 * Public License Version 3 (the "License") or (at your option)
 * any later version.  You may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at:
 *
 *      https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */
package org.opennms.netmgt.bsm.rest.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.opennms.netmgt.bsm.rest.model.BusinessServiceDto;
import org.opennms.netmgt.bsm.rest.model.BusinessServiceStatusDto;
import org.opennms.netmgt.bsm.rest.model.EdgeDto;
import org.opennms.netmgt.bsm.rest.model.MapFunctionDto;
import org.opennms.netmgt.bsm.rest.model.ReduceFunctionDto;
import org.opennms.netmgt.bsm.service.model.BusinessService;
import org.opennms.netmgt.bsm.service.model.IpService;
import org.opennms.netmgt.bsm.service.model.Status;
import org.opennms.netmgt.bsm.service.model.edge.ChildEdge;
import org.opennms.netmgt.bsm.service.model.edge.EdgeVisitor;
import org.opennms.netmgt.bsm.service.model.functions.map.Identity;
import org.opennms.netmgt.bsm.service.model.functions.reduce.HighestSeverity;
import org.opennms.netmgt.bsm.service.model.graph.GraphVertex;

class BusinessServiceMapperTest {

    private final BusinessServiceMapper mapper = new BusinessServiceMapper();

    @Test
    void mapsBusinessServiceToDto() {
        var bs = mock(BusinessService.class);
        when(bs.getId()).thenReturn(1L);
        when(bs.getName()).thenReturn("Delta-V");
        when(bs.getAttributes()).thenReturn(Map.of("owner", "ops"));
        when(bs.getReduceFunction()).thenReturn(new HighestSeverity());
        when(bs.getEdges()).thenReturn(Collections.emptySet());

        BusinessServiceDto dto = mapper.toDto(bs);

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getName()).isEqualTo("Delta-V");
        assertThat(dto.getAttributes()).containsEntry("owner", "ops");
        assertThat(dto.getReduceFunction().getType()).isEqualTo("highestSeverity");
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapsChildEdgeToDto() {
        var childBs = mock(BusinessService.class);
        when(childBs.getId()).thenReturn(2L);

        var edge = mock(ChildEdge.class);
        when(edge.getId()).thenReturn(10L);
        when(edge.getChild()).thenReturn(childBs);
        when(edge.getMapFunction()).thenReturn(new Identity());
        when(edge.getWeight()).thenReturn(1);
        // Wire up accept() to dispatch to the visitor's visit(ChildEdge) method
        when(edge.accept(any(EdgeVisitor.class))).thenAnswer(invocation -> {
            EdgeVisitor<EdgeDto> visitor = invocation.getArgument(0);
            return visitor.visit(edge);
        });

        EdgeDto dto = mapper.edgeToDto(edge);

        assertThat(dto.getType()).isEqualTo("child");
        assertThat(dto.getChildId()).isEqualTo(2L);
        assertThat(dto.getMapFunction().getType()).isEqualTo("identity");
    }

    @Test
    void mapsStatusToDto() {
        var bs = mock(BusinessService.class);
        when(bs.getId()).thenReturn(1L);
        when(bs.getName()).thenReturn("Delta-V");

        var vertex = mock(GraphVertex.class);
        var ipService = mock(IpService.class);
        when(ipService.getNodeLabel()).thenReturn("postgresql");
        when(ipService.getIpAddress()).thenReturn("169.254.0.1");
        when(ipService.getServiceName()).thenReturn("PostgreSQL");
        when(vertex.getIpService()).thenReturn(ipService);
        when(vertex.getBusinessService()).thenReturn(null);
        when(vertex.getApplication()).thenReturn(null);
        when(vertex.getReductionKey()).thenReturn(null);

        BusinessServiceStatusDto dto = mapper.toStatusDto(bs, Status.WARNING, List.of(vertex));

        assertThat(dto.getOperationalStatus()).isEqualTo("warning");
        assertThat(dto.getRootCause()).containsExactly("IP service 'postgresql/169.254.0.1/PostgreSQL'");
    }

    @Test
    void mapsReduceFunctionFromDto() {
        var dto = new ReduceFunctionDto();
        dto.setType("highestSeverity");

        var fn = mapper.toReduceFunction(dto);

        assertThat(fn).isInstanceOf(HighestSeverity.class);
    }

    @Test
    void mapsMapFunctionFromDto() {
        var dto = new MapFunctionDto();
        dto.setType("identity");

        var fn = mapper.toMapFunction(dto);

        assertThat(fn).isInstanceOf(Identity.class);
    }
}
