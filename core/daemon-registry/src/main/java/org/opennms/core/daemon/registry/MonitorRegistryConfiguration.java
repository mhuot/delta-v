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
package org.opennms.core.daemon.registry;

import java.util.List;

import org.opennms.netmgt.poller.ServiceMonitorRegistry;
import org.opennms.netmgt.poller.monitors.BgpSessionMonitor;
import org.opennms.netmgt.poller.monitors.DNSResolutionMonitor;
import org.opennms.netmgt.poller.monitors.DnsMonitor;
import org.opennms.netmgt.poller.monitors.HttpMonitor;
import org.opennms.netmgt.poller.monitors.HttpsMonitor;
import org.opennms.netmgt.poller.monitors.IcmpMonitor;
import org.opennms.netmgt.poller.monitors.MinaSshMonitor;
import org.opennms.netmgt.poller.monitors.NtpMonitor;
import org.opennms.netmgt.poller.monitors.PageSequenceMonitor;
import org.opennms.netmgt.poller.monitors.PassiveServiceMonitor;
import org.opennms.netmgt.poller.monitors.SSLCertMonitor;
import org.opennms.netmgt.poller.monitors.SnmpMonitor;
import org.opennms.netmgt.poller.monitors.SshMonitor;
import org.opennms.netmgt.poller.monitors.StrafePingMonitor;
import org.opennms.netmgt.poller.monitors.TcpMonitor;
import org.opennms.netmgt.poller.monitors.WebMonitor;
import org.opennms.protocols.radius.monitor.RadiusAuthMonitor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the service monitors that Delta-V supports.
 * Import this configuration from any daemon boot config that needs a
 * {@link ServiceMonitorRegistry}.
 */
@Configuration
public class MonitorRegistryConfiguration {

    @Bean
    public ServiceMonitorRegistry serviceMonitorRegistry() throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        return new LocalServiceMonitorRegistry(List.of(
            new IcmpMonitor(),
            new SnmpMonitor(),
            new TcpMonitor(),
            new HttpMonitor(),
            new HttpsMonitor(),
            new DnsMonitor(),
            new SshMonitor(),
            new SSLCertMonitor(),
            new PageSequenceMonitor(),
            new BgpSessionMonitor(),
            new DNSResolutionMonitor(),
            new MinaSshMonitor(),
            new NtpMonitor(),
            new StrafePingMonitor(),
            new WebMonitor(),
            new PassiveServiceMonitor(),
            new RadiusAuthMonitor()
        ));
    }
}
