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

import static org.opennms.core.utils.InetAddressUtils.addr;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.opennms.netmgt.model.OnmsNode.NodeLabelSource;
import org.opennms.netmgt.model.OnmsNode.NodeType;
import org.opennms.netmgt.model.monitoringLocations.OnmsMonitoringLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyAccessorFactory;

/**
 * Builder for constructing OnmsNode/interface/service hierarchies.
 * Used by provisioning adapters and test infrastructure.
 */
public class NetworkBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(NetworkBuilder.class);

    private final OnmsMonitoringLocation m_location;

    private final OnmsDistPoller m_distPoller;

    OnmsNode m_currentNode;

    BeanWrapper m_assetBean;

    OnmsIpInterface m_currentIf;

    OnmsSnmpInterface m_currentSnmpIf;

    OnmsMonitoredService m_currentMonSvc;

    private Map<String, OnmsServiceType> m_serviceTypeCache = new HashMap<String, OnmsServiceType>();

    private Map<String, OnmsCategory> m_categoryCache = new HashMap<String, OnmsCategory>();

    public NetworkBuilder(final OnmsDistPoller distPoller) {
        m_location = new OnmsMonitoringLocation("Default", "Default");
        m_distPoller = distPoller;
    }

    public NetworkBuilder() {
        m_location = new OnmsMonitoringLocation("Default", "Default");
        m_distPoller = new OnmsDistPoller("00000000-0000-0000-0000-000000000000");
        m_distPoller.setLabel("localhost");
        m_distPoller.setLocation("Default");
        m_distPoller.setType(OnmsMonitoringSystem.TYPE_OPENNMS);
    }

    public OnmsMonitoringLocation getLocation() {
        return m_location;
    }

    public OnmsDistPoller getDistPoller() {
        return m_distPoller;
    }

    public NodeBuilder addNode(String label) {
        m_currentNode = new OnmsNode(m_location, label);
        m_assetBean = PropertyAccessorFactory.forBeanPropertyAccess(m_currentNode.getAssetRecord());
        return new NodeBuilder(m_currentNode);
    }

    public static class NodeBuilder {
        OnmsNode m_node;

        NodeBuilder(final OnmsNode node) {
            m_node = node;
        }

        public OnmsNode getNode() {
            return m_node;
        }

        public NodeBuilder setId(final Integer id) {
            m_node.setId(id);
            return this;
        }

        public NodeBuilder setForeignSource(final String foreignSource) {
            m_node.setForeignSource(foreignSource);
            return this;
        }

        public NodeBuilder setForeignId(final String foreignId) {
            m_node.setForeignId(foreignId);
            return this;
        }

        public NodeBuilder setLocation(final String locationName) {
            if (locationName != null) {
                final OnmsMonitoringLocation location = new OnmsMonitoringLocation();
                location.setLocationName(locationName);
                m_node.setLocation(location);
            } else {
                m_node.setLocation(null);
            }
            return this;
        }

        public OnmsAssetRecord getAssetRecord() {
            return m_node.getAssetRecord();
        }

        public NodeBuilder setLabelSource(final NodeLabelSource labelSource) {
            m_node.setLabelSource(labelSource);
            return this;
        }

        public NodeBuilder setType(final NodeType type) {
            m_node.setType(type);
            return this;
        }

        public NodeBuilder setSysObjectId(final String sysObjectId) {
            m_node.setSysObjectId(sysObjectId);
            return this;
        }

        public NodeBuilder setSysName(final String nodesysname) {
            m_node.setSysName(nodesysname);
            return this;
        }

        public NodeBuilder setParent(final OnmsNode parent) {
            m_node.setParent(parent);
            return this;
        }

        public NodeBuilder setNodeParentId(Integer id) {
            m_node.setNodeParentId(id);
            return this;
        }
    }

    public InterfaceBuilder addInterface(final String ipAddr) {
        return addInterface(addr(ipAddr));
    }

    public InterfaceBuilder addInterface(final InetAddress ipAddr) {
        m_currentIf = new OnmsIpInterface(ipAddr, m_currentNode);
        return new InterfaceBuilder(m_currentIf);
    }

    public static class InterfaceBuilder {
        final OnmsIpInterface m_iface;

        InterfaceBuilder(final OnmsIpInterface iface) {
            m_iface = iface;
        }

        public InterfaceBuilder setNetMask(final InetAddress mask) {
            m_iface.setNetMask(mask);
            return this;
        }

        public InterfaceBuilder setNetMask(final String mask) {
            return this.setNetMask(addr(mask));
        }

        public InterfaceBuilder setIsManaged(final String managed) {
            m_iface.setIsManaged(managed);
            return this;
        }

        public InterfaceBuilder setIsSnmpPrimary(final String isSnmpPrimary) {
            m_iface.setIsSnmpPrimary(PrimaryType.get(isSnmpPrimary));
            return this;
        }

        public OnmsIpInterface getInterface() {
            return m_iface;
        }

        @Deprecated
        public SnmpInterfaceBuilder addSnmpInterface(final int ifIndex) {
            final OnmsSnmpInterface snmpIf = new OnmsSnmpInterface(m_iface.getNode(), ifIndex);
            m_iface.setSnmpInterface(snmpIf);
            snmpIf.getIpInterfaces().add(m_iface);
            return new SnmpInterfaceBuilder(snmpIf);
        }

        public InterfaceBuilder setId(final int id) {
            m_iface.setId(id);
            return this;
        }
    }

    public InterfaceBuilder addInterface(final String ipAddr, final OnmsSnmpInterface snmpInterface) {
        m_currentIf = new OnmsIpInterface(addr(ipAddr), m_currentNode);
        m_currentIf.setSnmpInterface(snmpInterface);
        return new InterfaceBuilder(m_currentIf);
    }

    public SnmpInterfaceBuilder addSnmpInterface(final int ifIndex) {
        final OnmsSnmpInterface snmp = new OnmsSnmpInterface(m_currentNode, ifIndex);
        if (m_currentIf != null) snmp.getIpInterfaces().add(m_currentIf);
        final SnmpInterfaceBuilder snmpInterfaceBuilder = new SnmpInterfaceBuilder(snmp);
        m_currentSnmpIf = snmp;
        return snmpInterfaceBuilder;
    }

    public OnmsMonitoredService addService(final OnmsServiceType serviceType) {
        m_serviceTypeCache.put(serviceType.getName(), serviceType);
        if (m_currentIf != null) {
            m_currentMonSvc = new OnmsMonitoredService(m_currentIf, serviceType);
            m_currentMonSvc.setStatus("A");
            return m_currentMonSvc;
        } else if (m_currentSnmpIf != null) {
            final Set<OnmsIpInterface> ipInterfaces = m_currentSnmpIf.getIpInterfaces();
            if (ipInterfaces != null && ipInterfaces.size() > 0) {
                final OnmsIpInterface current = ipInterfaces.toArray(new OnmsIpInterface[]{})[ipInterfaces.size() - 1];
                m_currentMonSvc = new OnmsMonitoredService(current, serviceType);
                m_currentMonSvc.setStatus("A");
                return m_currentMonSvc;
            }
        }
        m_currentMonSvc = null;
        return null;
    }

    public void setDisplayCategory(final String displayCategory) {
        m_currentNode.getAssetRecord().setDisplayCategory(displayCategory);
    }

    public void setBuilding(final String building) {
        m_currentNode.getAssetRecord().setBuilding(building);
    }

    public OnmsNode getCurrentNode() {
        return m_currentNode;
    }

    public void addCategory(final OnmsCategory cat) {
        m_categoryCache.put(cat.getName(), cat);
        m_currentNode.addCategory(cat);
    }

    public void addCategory(final String categoryName) {
        addCategory(getCategory(categoryName));
    }

    public void clearInterface() {
        m_currentIf = null;
        m_currentMonSvc = null;
    }

    public OnmsMonitoredService addService(final String serviceName) {
        return addService(getServiceType(serviceName));
    }

    public void setAssetAttribute(final String name, final String value) {
        try {
            m_assetBean.setPropertyValue(name, value);
        } catch (final BeansException e) {
            LOG.warn("Could not set property '{}' on asset '{}'", value, name, e);
        }
    }

    public void setNodeMetaDataEntry(final String context, final String key, final String value) {
        if (m_currentNode != null) {
            m_currentNode.addMetaData(context, key, value);
        }
    }

    public void setInterfaceMetaDataEntry(final String context, final String key, final String value) {
        if (m_currentIf != null) {
            m_currentIf.addMetaData(context, key, value);
        }
    }

    public void setServiceMetaDataEntry(final String context, final String key, final String value) {
        if (m_currentMonSvc != null) {
            m_currentMonSvc.addMetaData(context, key, value);
        }
    }

    public OnmsServiceType getServiceType(final String serviceName) {
        if (!m_serviceTypeCache.containsKey(serviceName)) {
            m_serviceTypeCache.put(serviceName, new OnmsServiceType(serviceName));
        }
        return m_serviceTypeCache.get(serviceName);
    }

    public OnmsCategory getCategory(final String categoryName) {
        if (!m_categoryCache.containsKey(categoryName)) {
            m_categoryCache.put(categoryName, new OnmsCategory(categoryName));
        }
        return m_categoryCache.get(categoryName);
    }
}
