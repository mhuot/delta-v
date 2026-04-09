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
package org.deltav.minion.boot;

import org.opennms.netmgt.icmp.PingerFactory;
import org.opennms.netmgt.icmp.best.BestMatchPingerFactory;
import org.opennms.netmgt.icmp.proxy.PingProxyRpcModule;
import org.opennms.netmgt.icmp.proxy.PingSweepRpcModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the ICMP proxy RPC modules (module IDs "PING" and "PING-SWEEP").
 *
 * <p>Provides a {@link BestMatchPingerFactory} that auto-detects the best
 * available ICMP implementation (JNI, JNA, or NullPinger) and injects it
 * into both ping modules.</p>
 */
@Configuration
@ConditionalOnProperty(name = "opennms.minion.icmp.enabled", havingValue = "true", matchIfMissing = true)
public class IcmpProxyConfiguration {

    @Bean
    public PingerFactory pingerFactory() {
        return new BestMatchPingerFactory();
    }

    @Bean
    public PingProxyRpcModule pingProxyRpcModule(PingerFactory pingerFactory) {
        PingProxyRpcModule module = new PingProxyRpcModule();
        module.setPingerFactory(pingerFactory);
        return module;
    }

    @Bean
    public PingSweepRpcModule pingSweepRpcModule(PingerFactory pingerFactory) {
        PingSweepRpcModule module = new PingSweepRpcModule();
        module.setPingerFactory(pingerFactory);
        return module;
    }
}
