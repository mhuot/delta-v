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
