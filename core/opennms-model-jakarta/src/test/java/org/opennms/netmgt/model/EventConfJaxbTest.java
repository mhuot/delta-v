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

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class EventConfJaxbTest {

    @Test
    void eventConfEventFieldsAreAccessible() {
        EventConfEvent event = new EventConfEvent();
        event.setUei("uei.opennms.org/test/eventConfMigration");
        event.setEventLabel("Test Event");
        assertEquals("uei.opennms.org/test/eventConfMigration", event.getUei());
        assertEquals("Test Event", event.getEventLabel());
    }

    @Test
    void eventConfSourceBidirectionalRelationship() {
        EventConfSource source = new EventConfSource();
        source.setName("test-source");
        EventConfEvent event = new EventConfEvent();
        event.setUei("uei.opennms.org/test");
        event.setSource(source);
        assertEquals(source, event.getSource());
        assertEquals("test-source", event.getSource().getName());
    }
}
