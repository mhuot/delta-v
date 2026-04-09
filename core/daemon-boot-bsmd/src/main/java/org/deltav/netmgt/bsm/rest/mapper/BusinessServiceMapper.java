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

import java.util.List;
import java.util.stream.Collectors;

import org.deltav.netmgt.bsm.rest.model.BusinessServiceDto;
import org.deltav.netmgt.bsm.rest.model.BusinessServiceStatusDto;
import org.deltav.netmgt.bsm.rest.model.EdgeDto;
import org.deltav.netmgt.bsm.rest.model.MapFunctionDto;
import org.deltav.netmgt.bsm.rest.model.ReduceFunctionDto;
import org.opennms.netmgt.bsm.service.model.BusinessService;
import org.opennms.netmgt.bsm.service.model.Status;
import org.opennms.netmgt.bsm.service.model.edge.ApplicationEdge;
import org.opennms.netmgt.bsm.service.model.edge.ChildEdge;
import org.opennms.netmgt.bsm.service.model.edge.Edge;
import org.opennms.netmgt.bsm.service.model.edge.EdgeVisitor;
import org.opennms.netmgt.bsm.service.model.edge.IpServiceEdge;
import org.opennms.netmgt.bsm.service.model.edge.ReductionKeyEdge;
import org.opennms.netmgt.bsm.service.model.functions.map.Decrease;
import org.opennms.netmgt.bsm.service.model.functions.map.Identity;
import org.opennms.netmgt.bsm.service.model.functions.map.Ignore;
import org.opennms.netmgt.bsm.service.model.functions.map.Increase;
import org.opennms.netmgt.bsm.service.model.functions.map.MapFunction;
import org.opennms.netmgt.bsm.service.model.functions.map.SetTo;
import org.opennms.netmgt.bsm.service.model.functions.reduce.ExponentialPropagation;
import org.opennms.netmgt.bsm.service.model.functions.reduce.HighestSeverity;
import org.opennms.netmgt.bsm.service.model.functions.reduce.HighestSeverityAbove;
import org.opennms.netmgt.bsm.service.model.functions.reduce.ReductionFunction;
import org.opennms.netmgt.bsm.service.model.functions.reduce.Threshold;
import org.opennms.netmgt.bsm.service.model.graph.GraphVertex;
import org.springframework.stereotype.Component;

@Component
public class BusinessServiceMapper {

    public BusinessServiceDto toDto(BusinessService bs) {
        var dto = new BusinessServiceDto();
        dto.setId(bs.getId());
        dto.setName(bs.getName());
        dto.setAttributes(bs.getAttributes());
        dto.setReduceFunction(toReduceFunctionDto(bs.getReduceFunction()));
        dto.setEdges(bs.getEdges().stream()
                .map(this::edgeToDto)
                .collect(Collectors.toList()));
        return dto;
    }

    public EdgeDto edgeToDto(Edge edge) {
        return edge.accept(new EdgeVisitor<EdgeDto>() {
            @Override
            public EdgeDto visit(IpServiceEdge e) {
                var dto = baseEdgeDto(e);
                dto.setType("ipService");
                dto.setIpServiceId(e.getIpService().getId());
                dto.setFriendlyName(e.getFriendlyName());
                return dto;
            }

            @Override
            public EdgeDto visit(ReductionKeyEdge e) {
                var dto = baseEdgeDto(e);
                dto.setType("reductionKey");
                dto.setReductionKey(e.getReductionKey());
                dto.setFriendlyName(e.getFriendlyName());
                return dto;
            }

            @Override
            public EdgeDto visit(ChildEdge e) {
                var dto = baseEdgeDto(e);
                dto.setType("child");
                dto.setChildId(e.getChild().getId());
                return dto;
            }

            @Override
            public EdgeDto visit(ApplicationEdge e) {
                var dto = baseEdgeDto(e);
                dto.setType("application");
                dto.setApplicationId(e.getApplication().getId());
                return dto;
            }
        });
    }

    private EdgeDto baseEdgeDto(Edge edge) {
        var dto = new EdgeDto();
        dto.setId(edge.getId());
        dto.setMapFunction(toMapFunctionDto(edge.getMapFunction()));
        dto.setWeight(edge.getWeight());
        return dto;
    }

    public BusinessServiceStatusDto toStatusDto(BusinessService bs, Status status,
                                                 List<GraphVertex> rootCauseVertices) {
        var dto = new BusinessServiceStatusDto();
        dto.setId(bs.getId());
        dto.setName(bs.getName());
        dto.setOperationalStatus(status.name().toLowerCase());
        dto.setRootCause(rootCauseVertices.stream()
                .map(this::formatRootCauseVertex)
                .collect(Collectors.toList()));
        return dto;
    }

    private String formatRootCauseVertex(GraphVertex vertex) {
        if (vertex.getBusinessService() != null) {
            return "business service '" + vertex.getBusinessService().getName() + "'";
        } else if (vertex.getIpService() != null) {
            var svc = vertex.getIpService();
            return "IP service '" + svc.getNodeLabel() + "/" + svc.getIpAddress() + "/" + svc.getServiceName() + "'";
        } else if (vertex.getApplication() != null) {
            return "application '" + vertex.getApplication().getApplicationName() + "'";
        } else if (vertex.getReductionKey() != null) {
            return "reduction key '" + vertex.getReductionKey() + "'";
        }
        return "unknown vertex";
    }

    // --- DTO to Domain conversion ---

    public ReductionFunction toReduceFunction(ReduceFunctionDto dto) {
        return switch (dto.getType()) {
            case "highestSeverity" -> new HighestSeverity();
            case "highestSeverityAbove" -> {
                if (dto.getThreshold() == null) {
                    throw new IllegalArgumentException("threshold is required for reduce function type 'highestSeverityAbove'");
                }
                var fn = new HighestSeverityAbove();
                fn.setThreshold(Status.get(dto.getThreshold()));
                yield fn;
            }
            case "threshold" -> {
                if (dto.getThresholdValue() == null) {
                    throw new IllegalArgumentException("thresholdValue is required for reduce function type 'threshold'");
                }
                var fn = new Threshold();
                fn.setThreshold(dto.getThresholdValue());
                yield fn;
            }
            case "exponentialPropagation" -> {
                if (dto.getBase() == null) {
                    throw new IllegalArgumentException("base is required for reduce function type 'exponentialPropagation'");
                }
                var fn = new ExponentialPropagation();
                fn.setBase(dto.getBase());
                yield fn;
            }
            default -> throw new IllegalArgumentException("Unknown reduce function type: " + dto.getType());
        };
    }

    public MapFunction toMapFunction(MapFunctionDto dto) {
        return switch (dto.getType()) {
            case "identity" -> new Identity();
            case "ignore" -> new Ignore();
            case "increase" -> new Increase();
            case "decrease" -> new Decrease();
            case "setTo" -> {
                var fn = new SetTo();
                fn.setStatus(Status.of(dto.getSeverity()));
                yield fn;
            }
            default -> throw new IllegalArgumentException("Unknown map function type: " + dto.getType());
        };
    }

    // --- Domain to DTO conversion for functions ---

    private ReduceFunctionDto toReduceFunctionDto(ReductionFunction fn) {
        var dto = new ReduceFunctionDto();
        if (fn instanceof HighestSeverity) {
            dto.setType("highestSeverity");
        } else if (fn instanceof HighestSeverityAbove hsa) {
            dto.setType("highestSeverityAbove");
            dto.setThreshold(hsa.getThreshold().getId());
        } else if (fn instanceof Threshold t) {
            dto.setType("threshold");
            dto.setThresholdValue(t.getThreshold());
        } else if (fn instanceof ExponentialPropagation ep) {
            dto.setType("exponentialPropagation");
            dto.setBase(ep.getBase());
        }
        return dto;
    }

    private MapFunctionDto toMapFunctionDto(MapFunction fn) {
        var dto = new MapFunctionDto();
        if (fn instanceof Identity) {
            dto.setType("identity");
        } else if (fn instanceof Ignore) {
            dto.setType("ignore");
        } else if (fn instanceof Increase) {
            dto.setType("increase");
        } else if (fn instanceof Decrease) {
            dto.setType("decrease");
        } else if (fn instanceof SetTo setTo) {
            dto.setType("setTo");
            dto.setSeverity(setTo.getStatus().name().toLowerCase());
        }
        return dto;
    }
}
