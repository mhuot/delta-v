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
