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
package org.deltav.netmgt.bsm.rest.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.deltav.netmgt.bsm.rest.model.BusinessServiceDto;
import org.deltav.netmgt.bsm.rest.model.BusinessServiceStatusDto;
import org.deltav.netmgt.bsm.rest.model.EdgeDto;
import org.deltav.netmgt.bsm.rest.model.MapFunctionDto;
import org.deltav.netmgt.bsm.rest.model.ReduceFunctionDto;
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
