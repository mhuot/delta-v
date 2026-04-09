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
import java.util.stream.Collectors;

import org.deltav.netmgt.bsm.rest.model.MonitoredServiceDto;
import org.opennms.netmgt.bsm.service.BusinessServiceManager;
import org.opennms.netmgt.bsm.service.model.IpService;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v3/monitored-services")
public class MonitoredServiceRestController {

    private final BusinessServiceManager manager;
    private final TransactionTemplate transactionTemplate;

    public MonitoredServiceRestController(BusinessServiceManager manager,
                                          TransactionTemplate transactionTemplate) {
        this.manager = manager;
        this.transactionTemplate = transactionTemplate;
    }

    @GetMapping
    public List<MonitoredServiceDto> listAll() {
        return transactionTemplate.execute(status ->
            manager.getAllIpServices().stream()
                .map(this::toDto)
                .collect(Collectors.toList()));
    }

    private MonitoredServiceDto toDto(IpService svc) {
        return new MonitoredServiceDto(
            svc.getId(),
            svc.getNodeLabel(),
            svc.getIpAddress(),
            svc.getServiceName());
    }
}
