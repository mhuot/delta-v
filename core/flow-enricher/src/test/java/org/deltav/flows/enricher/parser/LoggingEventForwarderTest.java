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
package org.deltav.flows.enricher.parser;

import org.junit.jupiter.api.Test;
import org.opennms.netmgt.xml.event.Event;
import org.opennms.netmgt.xml.event.Log;

class LoggingEventForwarderTest {

    @Test
    void sendNowEventDoesNotThrow() {
        LoggingEventForwarder forwarder = new LoggingEventForwarder();
        Event event = new Event();
        event.setUei("uei.deltav/test/parser/clockSkew");
        forwarder.sendNow(event);
    }

    @Test
    void sendNowLogDoesNotThrow() {
        LoggingEventForwarder forwarder = new LoggingEventForwarder();
        Log log = new Log();
        forwarder.sendNow(log);
    }

    @Test
    void sendNowSyncEventDoesNotThrow() {
        LoggingEventForwarder forwarder = new LoggingEventForwarder();
        Event event = new Event();
        event.setUei("uei.deltav/test/parser/clockSkew");
        forwarder.sendNowSync(event);
    }

    @Test
    void sendNowSyncLogDoesNotThrow() {
        LoggingEventForwarder forwarder = new LoggingEventForwarder();
        Log log = new Log();
        forwarder.sendNowSync(log);
    }

    @Test
    void nullEventIsTolerated() {
        LoggingEventForwarder forwarder = new LoggingEventForwarder();
        forwarder.sendNow((Event) null);
        forwarder.sendNowSync((Event) null);
    }
}
