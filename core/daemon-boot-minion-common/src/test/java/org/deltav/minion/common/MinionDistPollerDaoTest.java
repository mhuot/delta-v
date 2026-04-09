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
package org.deltav.minion.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.opennms.netmgt.model.OnmsDistPoller;

class MinionDistPollerDaoTest {

    private SpringMinionIdentity buildIdentity(String id, String location) {
        MinionProperties properties = new MinionProperties();
        properties.setId(id);
        properties.setLocation(location);
        return new SpringMinionIdentity(properties);
    }

    @Test
    void whoamiReturnsIdentityBasedPoller() {
        SpringMinionIdentity identity = buildIdentity("minion-01", "NYC");
        MinionDistPollerDao dao = new MinionDistPollerDao(identity);

        OnmsDistPoller poller = dao.whoami();

        assertThat(poller.getId()).isEqualTo("minion-01");
        assertThat(poller.getLabel()).isEqualTo("minion-01");
        assertThat(poller.getLocation()).isEqualTo("NYC");
    }
}
