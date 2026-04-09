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

import org.deltav.core.daemon.common.NoOpTracerRegistry;
import org.opennms.core.tracing.api.TracerRegistry;
import org.opennms.distributed.core.api.Identity;
import org.opennms.distributed.core.api.MinionIdentity;
import org.deltav.minion.common.MinionDistPollerDao;
import org.deltav.minion.common.MinionProperties;
import org.deltav.minion.common.SpringMinionIdentity;
import org.opennms.netmgt.dao.api.DistPollerDao;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Shared infrastructure beans that multiple protocol subsystems depend on.
 *
 * <p>Provides identity, tracing, and DAO beans that were previously wired
 * via OSGi blueprint in the Karaf Minion container.</p>
 */
@Configuration
public class MinionInfraConfiguration {

    @Bean
    @Primary
    public SpringMinionIdentity minionIdentity(MinionProperties properties) {
        return new SpringMinionIdentity(properties);
    }

    @Bean
    public TracerRegistry tracerRegistry() {
        return new NoOpTracerRegistry();
    }

    @Bean
    @Primary
    public DistPollerDao distPollerDao(SpringMinionIdentity identity) {
        return new MinionDistPollerDao(identity);
    }
}
