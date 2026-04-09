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
package org.deltav.core.daemon.registry;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;

import org.opennms.netmgt.config.api.SnmpAgentConfigFactory;
import org.opennms.netmgt.config.snmp.Definition;
import org.opennms.netmgt.config.snmp.SnmpConfig;
import org.opennms.netmgt.config.snmp.SnmpProfile;
import org.opennms.netmgt.snmp.SnmpAgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link SnmpAgentConfigFactory} that throws {@link UnsupportedOperationException}
 * on every call. Used by Minion, which receives SNMP agent config via RPC
 * request attributes rather than from a local factory. Any invocation indicates
 * a bug - Minion should never resolve SNMP config locally.
 */
public class NoOpSnmpAgentConfigFactory implements SnmpAgentConfigFactory {

    private static final Logger LOG = LoggerFactory.getLogger(NoOpSnmpAgentConfigFactory.class);

    public NoOpSnmpAgentConfigFactory() {
        LOG.info("NoOpSnmpAgentConfigFactory installed - SNMP config must come via RPC request attributes");
    }

    @Override
    public void saveCurrent() throws IOException {
        throw unsupported("saveCurrent");
    }

    @Override
    public SnmpAgentConfig getAgentConfig(InetAddress address, String location) {
        throw unsupported("getAgentConfig");
    }

    @Override
    public SnmpAgentConfig getAgentConfigFromProfile(SnmpProfile snmpProfile, InetAddress address, boolean metaDataInterpolation) {
        throw unsupported("getAgentConfigFromProfile");
    }

    @Override
    public void saveDefinition(Definition definition) {
        throw unsupported("saveDefinition");
    }

    @Override
    public boolean removeFromDefinition(InetAddress ipAddress, String location, String module) {
        throw unsupported("removeFromDefinition");
    }

    @Override
    public void saveAgentConfigAsDefinition(SnmpAgentConfig snmpAgentConfig, String location, String module) {
        throw unsupported("saveAgentConfigAsDefinition");
    }

    @Override
    public List<SnmpProfile> getProfiles() {
        throw unsupported("getProfiles");
    }

    @Override
    public SnmpConfig getSnmpConfig() {
        throw unsupported("getSnmpConfig");
    }

    private UnsupportedOperationException unsupported(String method) {
        return new UnsupportedOperationException(
            "NoOpSnmpAgentConfigFactory." + method + " invoked - this indicates a bug; " +
            "Minion should receive SNMP config via RPC request attributes.");
    }
}
