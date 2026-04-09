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
package org.deltav.netmgt.bsm.rest;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.deltav.netmgt.bsm.rest.mapper.BusinessServiceMapper;
import org.deltav.netmgt.bsm.rest.model.BusinessServiceDto;
import org.deltav.netmgt.bsm.rest.model.BusinessServiceStatusDto;
import org.deltav.netmgt.bsm.rest.model.EdgeDto;
import org.opennms.netmgt.bsm.service.BusinessServiceManager;
import org.opennms.netmgt.bsm.service.BusinessServiceStateMachine;
import org.opennms.netmgt.bsm.service.model.BusinessService;
import org.opennms.netmgt.bsm.service.model.Status;
import org.opennms.netmgt.bsm.service.model.graph.GraphVertex;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v3/business-services")
public class BsmdRestController {

    private final BusinessServiceManager manager;
    private final BusinessServiceStateMachine stateMachine;
    private final BusinessServiceMapper mapper;
    private final TransactionTemplate transactionTemplate;

    public BsmdRestController(BusinessServiceManager manager,
                               BusinessServiceStateMachine stateMachine,
                               BusinessServiceMapper mapper,
                               TransactionTemplate transactionTemplate) {
        this.manager = manager;
        this.stateMachine = stateMachine;
        this.mapper = mapper;
        this.transactionTemplate = transactionTemplate;
    }

    @GetMapping
    public List<BusinessServiceDto> listAll() {
        return transactionTemplate.execute(status ->
            manager.getAllBusinessServices().stream()
                .map(mapper::toDto)
                .collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public BusinessServiceDto getById(@PathVariable("id") Long id) {
        return transactionTemplate.execute(status -> {
            BusinessService bs = findOrThrow(id);
            return mapper.toDto(bs);
        });
    }

    @PostMapping
    public ResponseEntity<BusinessServiceDto> create(@RequestBody BusinessServiceDto request) {
        BusinessServiceDto result = transactionTemplate.execute(status -> {
            BusinessService bs = manager.createBusinessService();
            bs.setName(request.getName());
            if (request.getAttributes() != null) {
                bs.setAttributes(request.getAttributes());
            }
            bs.setReduceFunction(mapper.toReduceFunction(request.getReduceFunction()));
            bs.save();

            if (request.getEdges() != null) {
                addEdges(bs, request.getEdges());
                bs.save();
            }

            return mapper.toDto(bs);
        });
        reloadStateMachine();
        return ResponseEntity.status(201).body(result);
    }

    @PutMapping("/{id}")
    public BusinessServiceDto update(@PathVariable("id") Long id, @RequestBody BusinessServiceDto request) {
        BusinessServiceDto result = transactionTemplate.execute(status -> {
            BusinessService bs = findOrThrow(id);
            bs.setName(request.getName());
            if (request.getAttributes() != null) {
                bs.setAttributes(request.getAttributes());
            }
            bs.setReduceFunction(mapper.toReduceFunction(request.getReduceFunction()));

            // Clear existing edges and re-add from request
            // Copy to list first to avoid ConcurrentModificationException
            new java.util.ArrayList<>(bs.getEdges()).forEach(bs::removeEdge);
            if (request.getEdges() != null) {
                addEdges(bs, request.getEdges());
            }

            bs.save();
            return mapper.toDto(bs);
        });
        reloadStateMachine();
        return result;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        transactionTemplate.executeWithoutResult(status -> {
            BusinessService bs = findOrThrow(id);
            manager.deleteBusinessService(bs);
        });
        reloadStateMachine();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/status")
    public BusinessServiceStatusDto getStatus(@PathVariable("id") Long id) {
        return transactionTemplate.execute(status -> {
            BusinessService bs = findOrThrow(id);
            Status opStatus = manager.getOperationalStatus(bs);
            List<GraphVertex> rootCause = (opStatus.isGreaterThan(Status.NORMAL))
                    ? stateMachine.calculateRootCause(bs)
                    : List.of();
            return mapper.toStatusDto(bs, opStatus, rootCause);
        });
    }

    /**
     * Reloads the BSM state machine with the current set of business services.
     * Called after every mutation (create/update/delete) to reflect changes
     * immediately without relying on the event-based daemon reload roundtrip
     * through Kafka.
     */
    private void reloadStateMachine() {
        transactionTemplate.executeWithoutResult(status -> {
            stateMachine.setBusinessServices(manager.getAllBusinessServices());
        });
    }

    private BusinessService findOrThrow(Long id) {
        try {
            return manager.getBusinessServiceById(id);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(404),
                    "Business service not found: " + id);
        }
    }

    private void addEdges(BusinessService bs, List<EdgeDto> edges) {
        for (EdgeDto edgeDto : edges) {
            var mapFn = mapper.toMapFunction(edgeDto.getMapFunction());
            switch (edgeDto.getType()) {
                case "child" -> manager.addChildEdge(bs,
                        manager.getBusinessServiceById(edgeDto.getChildId()),
                        mapFn, edgeDto.getWeight());
                case "ipService" -> manager.addIpServiceEdge(bs,
                        manager.getIpServiceById(edgeDto.getIpServiceId()),
                        mapFn, edgeDto.getWeight(), edgeDto.getFriendlyName());
                case "reductionKey" -> manager.addReductionKeyEdge(bs,
                        edgeDto.getReductionKey(),
                        mapFn, edgeDto.getWeight(), edgeDto.getFriendlyName());
                case "application" -> manager.addApplicationEdge(bs,
                        manager.getApplicationById(edgeDto.getApplicationId()),
                        mapFn, edgeDto.getWeight());
                default -> throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                        "Unknown edge type: " + edgeDto.getType());
            }
        }
    }
}
