/*
 * Copyright (C) 1999-2024 The OpenNMS Group, Inc.
 * Copyright (C) 2026 BeaconStrategists, Inc. (Modifications)
 *
 * This file is part of OpenNMS(R) / Delta-V.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
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
package org.opennms.netmgt.enlinkd.persistence.api;

import java.net.InetAddress;
import java.util.List;

import org.opennms.netmgt.enlinkd.model.OspfElement;


/**
 * <p>OspfElementDao interface.</p>
 */
public interface OspfElementDao extends ElementDao<OspfElement, Integer> {

    OspfElement findByRouterId(InetAddress routerId);

    List<OspfElement> findAllByRouterId(InetAddress routerId);

    /**
     * Returns all OspfElements that have an ospfRouterId that matches an ospfRemRouterId of an OspfLink related to the given
     * node. Used to retrieve all OspfElements that need to be accessed when finding Ospf links of a node.
     */
    List<OspfElement> findByRouterIdOfRelatedOspfLink(int nodeId);

}
