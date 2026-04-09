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
package org.opennms.netmgt.model;

import java.net.InetAddress;

import org.opennms.netmgt.model.NetworkBuilder.InterfaceBuilder;

import static org.opennms.core.utils.InetAddressUtils.addr;

/**
 * Builder for constructing OnmsSnmpInterface entities with IP interface associations.
 */
public class SnmpInterfaceBuilder {

    private final OnmsSnmpInterface m_snmpIf;

    public SnmpInterfaceBuilder(OnmsSnmpInterface snmpIf) {
        m_snmpIf = snmpIf;
    }

    public SnmpInterfaceBuilder setIfSpeed(long ifSpeed) {
        m_snmpIf.setIfSpeed(Long.valueOf(ifSpeed));
        return this;
    }

    public SnmpInterfaceBuilder setIfDescr(String ifDescr) {
        m_snmpIf.setIfDescr(ifDescr);
        return this;
    }

    public SnmpInterfaceBuilder setIfAlias(String ifAlias) {
        m_snmpIf.setIfAlias(ifAlias);
        return this;
    }

    public SnmpInterfaceBuilder setIfName(String ifName) {
        m_snmpIf.setIfName(ifName);
        return this;
    }

    public SnmpInterfaceBuilder setIfType(Integer ifType) {
        m_snmpIf.setIfType(ifType);
        return this;
    }

    public OnmsSnmpInterface getSnmpInterface() {
        return m_snmpIf;
    }

    public SnmpInterfaceBuilder setIfOperStatus(Integer ifOperStatus) {
        m_snmpIf.setIfOperStatus(ifOperStatus);
        return this;
    }

    public SnmpInterfaceBuilder setCollectionEnabled(boolean collect) {
        m_snmpIf.setCollectionEnabled(collect);
        return this;
    }

    public SnmpInterfaceBuilder setPhysAddr(String physAddr) {
        m_snmpIf.setPhysAddr(physAddr);
        return this;
    }

    public InterfaceBuilder addIpInterface(final String ipAddress) {
        return addIpInterface(addr(ipAddress));
    }

    public InterfaceBuilder addIpInterface(final InetAddress ipAddress) {
        final OnmsIpInterface iface = new OnmsIpInterface(ipAddress, m_snmpIf.getNode());
        m_snmpIf.addIpInterface(iface);
        return new InterfaceBuilder(iface);
    }
}
