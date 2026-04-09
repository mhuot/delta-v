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
package org.deltav.core.daemon.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opennms.core.mate.api.ContextKey;
import org.opennms.core.mate.api.EmptyScope;
import org.opennms.core.mate.api.Scope;
import org.opennms.features.scv.api.Credentials;
import org.opennms.features.scv.api.SecureCredentialsVault;
import org.opennms.netmgt.dao.api.IpInterfaceDao;
import org.opennms.netmgt.dao.api.MonitoredServiceDao;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.dao.api.SessionUtils;
import org.opennms.netmgt.dao.api.SnmpInterfaceDao;
import org.opennms.netmgt.model.OnmsIpInterface;
import org.opennms.netmgt.model.OnmsMonitoredService;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.OnmsServiceType;
import org.opennms.netmgt.model.monitoringLocations.OnmsMonitoringLocation;

class DaemonEntityScopeProviderTest {

    private NodeDao nodeDao;
    private IpInterfaceDao ipInterfaceDao;
    private SnmpInterfaceDao snmpInterfaceDao;
    private MonitoredServiceDao monitoredServiceDao;
    private SessionUtils sessionUtils;
    private SecureCredentialsVault scv;

    private DaemonEntityScopeProvider provider;

    @BeforeEach
    void setUp() {
        nodeDao = mock(NodeDao.class);
        ipInterfaceDao = mock(IpInterfaceDao.class);
        snmpInterfaceDao = mock(SnmpInterfaceDao.class);
        monitoredServiceDao = mock(MonitoredServiceDao.class);
        sessionUtils = mock(SessionUtils.class);
        scv = mock(SecureCredentialsVault.class);

        // Make withReadOnlyTransaction execute the supplier directly so tests
        // don't need a real Hibernate session.
        // Cast to Supplier overload explicitly to disambiguate from the Runnable overload.
        when(sessionUtils.<Object>withReadOnlyTransaction(any(Supplier.class))).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });

        provider = new DaemonEntityScopeProvider(
                nodeDao, ipInterfaceDao, snmpInterfaceDao, monitoredServiceDao, sessionUtils, scv);
    }

    // -----------------------------------------------------------------------
    // SCV scope
    // -----------------------------------------------------------------------

    @Test
    void getScopeForScv_resolvesUsernameAndPassword() {
        Credentials credentials = new Credentials("user1", "pass1");
        when(scv.getCredentials("myalias")).thenReturn(credentials);

        Scope scope = provider.getScopeForScv();

        assertThat(scope.get(new ContextKey("scv", "myalias:username")))
                .isPresent()
                .hasValueSatisfying(sv -> assertThat(sv.value).isEqualTo("user1"));

        assertThat(scope.get(new ContextKey("scv", "myalias:password")))
                .isPresent()
                .hasValueSatisfying(sv -> assertThat(sv.value).isEqualTo("pass1"));
    }

    // -----------------------------------------------------------------------
    // Environment scope
    // -----------------------------------------------------------------------

    @Test
    void getScopeForEnv_resolvesPATH() {
        Scope scope = provider.getScopeForEnv();

        assertThat(scope.get(new ContextKey("env", "PATH")))
                .isPresent()
                .hasValueSatisfying(sv -> assertThat(sv.value).isNotEmpty());
    }

    // -----------------------------------------------------------------------
    // Node scope
    // -----------------------------------------------------------------------

    @Test
    void getScopeForNode_returnsNodeAttributes() {
        OnmsNode node = new OnmsNode();
        node.setLabel("test-node");
        node.setForeignSource("fs1");
        node.setForeignId("fid1");
        node.setLocation(new OnmsMonitoringLocation("Default", "Default"));

        when(nodeDao.get(1)).thenReturn(node);

        Scope scope = provider.getScopeForNode(1);

        assertThat(scope.get(new ContextKey("node", "label")))
                .isPresent()
                .hasValueSatisfying(sv -> assertThat(sv.value).isEqualTo("test-node"));

        assertThat(scope.get(new ContextKey("node", "foreign-source")))
                .isPresent()
                .hasValueSatisfying(sv -> assertThat(sv.value).isEqualTo("fs1"));

        assertThat(scope.get(new ContextKey("node", "foreign-id")))
                .isPresent()
                .hasValueSatisfying(sv -> assertThat(sv.value).isEqualTo("fid1"));
    }

    @Test
    void getScopeForNode_nullId_returnsEmpty() {
        Scope scope = provider.getScopeForNode(null);

        assertThat(scope).isEqualTo(EmptyScope.EMPTY);
    }

    @Test
    void getScopeForNode_unknownId_returnsEmpty() {
        when(nodeDao.get(999)).thenReturn(null);

        Scope scope = provider.getScopeForNode(999);

        assertThat(scope).isEqualTo(EmptyScope.EMPTY);
    }

    // -----------------------------------------------------------------------
    // Interface scope
    // -----------------------------------------------------------------------

    @Test
    void getScopeForInterface_returnsInterfaceAttributes() throws Exception {
        OnmsNode node = new OnmsNode();
        node.setLocation(new OnmsMonitoringLocation("Default", "Default"));

        OnmsIpInterface ipInterface = new OnmsIpInterface(InetAddress.getByName("192.168.1.1"), node);
        ipInterface.setIpHostName("host1");

        when(ipInterfaceDao.findByNodeIdAndIpAddress(1, "192.168.1.1")).thenReturn(ipInterface);

        Scope scope = provider.getScopeForInterface(1, "192.168.1.1");

        assertThat(scope.get(new ContextKey("interface", "hostname")))
                .isPresent()
                .hasValueSatisfying(sv -> assertThat(sv.value).isEqualTo("host1"));
    }

    // -----------------------------------------------------------------------
    // Service scope
    // -----------------------------------------------------------------------

    @Test
    void getScopeForService_returnsServiceName() throws Exception {
        OnmsServiceType serviceType = new OnmsServiceType("ICMP");

        OnmsMonitoredService monitoredService = new OnmsMonitoredService();
        monitoredService.setServiceType(serviceType);

        InetAddress address = InetAddress.getByName("192.168.1.1");
        when(monitoredServiceDao.get(1, address, "ICMP")).thenReturn(monitoredService);

        Scope scope = provider.getScopeForService(1, address, "ICMP");

        assertThat(scope.get(new ContextKey("service", "name")))
                .isPresent()
                .hasValueSatisfying(sv -> assertThat(sv.value).isEqualTo("ICMP"));
    }
}
