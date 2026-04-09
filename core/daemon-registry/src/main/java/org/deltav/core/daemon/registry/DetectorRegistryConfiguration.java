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

import java.util.List;

import org.opennms.netmgt.config.api.SnmpAgentConfigFactory;
import org.opennms.netmgt.provision.ServiceDetectorFactory;
import org.opennms.netmgt.provision.detector.datagram.DnsDetectorFactory;
import org.opennms.netmgt.provision.detector.datagram.NtpDetectorFactory;
import org.opennms.netmgt.provision.detector.icmp.IcmpDetectorFactory;
import org.opennms.netmgt.provision.detector.jmx.Jsr160DetectorFactory;
import org.opennms.netmgt.provision.detector.loop.LoopDetectorFactory;
import org.opennms.netmgt.provision.detector.registry.api.ServiceDetectorRegistry;
import org.opennms.netmgt.provision.detector.simple.FtpDetectorFactory;
import org.opennms.netmgt.provision.detector.simple.HttpDetectorFactory;
import org.opennms.netmgt.provision.detector.simple.HttpsDetectorFactory;
import org.opennms.netmgt.provision.detector.simple.ImapDetectorFactory;
import org.opennms.netmgt.provision.detector.simple.ImapsDetectorFactory;
import org.opennms.netmgt.provision.detector.simple.LdapDetectorFactory;
import org.opennms.netmgt.provision.detector.simple.LdapsDetectorFactory;
import org.opennms.netmgt.provision.detector.simple.MemcachedDetectorFactory;
import org.opennms.netmgt.provision.detector.simple.NrpeDetectorFactory;
import org.opennms.netmgt.provision.detector.simple.Pop3DetectorFactory;
import org.opennms.netmgt.provision.detector.simple.SmtpDetectorFactory;
import org.opennms.netmgt.provision.detector.simple.TcpDetectorFactory;
import org.opennms.netmgt.provision.detector.smb.SmbDetectorFactory;
import org.opennms.netmgt.provision.detector.snmp.SnmpDetectorFactory;
import org.opennms.netmgt.provision.detector.ssh.SshDetectorFactory;
import org.opennms.netmgt.provision.detector.web.WebDetectorFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the service detector factories that Delta-V supports.
 * Import this configuration from any daemon boot config that needs a
 * {@link ServiceDetectorRegistry}.
 *
 * <p>Requires a {@link SnmpAgentConfigFactory} bean. On Provisiond this is the
 * real one backed by snmp-config.xml. On Minion it's a
 * {@link NoOpSnmpAgentConfigFactory} since Minion receives SNMP config via
 * RPC request attributes.</p>
 */
@Configuration
public class DetectorRegistryConfiguration {

    @Bean
    public ServiceDetectorRegistry serviceDetectorRegistry(SnmpAgentConfigFactory snmpAgentConfigFactory) {
        List<ServiceDetectorFactory<?>> factories = List.of(
            new IcmpDetectorFactory(),
            new SnmpDetectorFactory(snmpAgentConfigFactory),
            new SmbDetectorFactory(),
            new LoopDetectorFactory(),
            new TcpDetectorFactory(),
            new HttpDetectorFactory(),
            new HttpsDetectorFactory(),
            new FtpDetectorFactory(),
            new Pop3DetectorFactory(),
            new SmtpDetectorFactory(),
            new ImapDetectorFactory(),
            new ImapsDetectorFactory(),
            new LdapDetectorFactory(),
            new LdapsDetectorFactory(),
            new NrpeDetectorFactory(),
            new MemcachedDetectorFactory(),
            new DnsDetectorFactory(),
            new NtpDetectorFactory(),
            new SshDetectorFactory(),
            new WebDetectorFactory(),
            new Jsr160DetectorFactory()
        );
        return new LocalServiceDetectorRegistry(factories);
    }
}
