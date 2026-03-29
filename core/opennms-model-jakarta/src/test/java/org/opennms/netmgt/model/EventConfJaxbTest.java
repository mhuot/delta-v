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
