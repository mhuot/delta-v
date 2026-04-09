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

import java.io.Serializable;

import org.opennms.netmgt.events.api.EventDatabaseConstants;
import org.opennms.netmgt.xml.event.Parm;

/**
 * Plain POJO representing an event parameter.
 * Used as a value object embedded in OnmsAlarm and alarm-related DTOs.
 */
public class OnmsEventParameter implements Serializable {

    private static final long serialVersionUID = 4530678411898489175L;

    private String name;
    private String value;
    private String type;
    private int position;

    public OnmsEventParameter() {}

    public OnmsEventParameter(Parm parm) {
        name = parm.getParmName();
        value = EventDatabaseConstants.sanitize(parm.getValue().getContent() == null ? "" : parm.getValue().getContent());
        type = parm.getValue().getType();
    }

    public OnmsEventParameter(final String name, final String value, final String type) {
        this.name = name;
        this.value = EventDatabaseConstants.sanitize(value == null ? "" : value);
        this.type = type;
    }

    public String getName() { return name; }
    public String getValue() { return value; }
    public String getType() { return type; }
    int getPosition() { return position; }

    public void setName(String name) { this.name = name; }
    public void setValue(String value) { this.value = EventDatabaseConstants.sanitize(value); }
    public void setType(String type) { this.type = type; }
    void setPosition(int position) { this.position = position; }
}
