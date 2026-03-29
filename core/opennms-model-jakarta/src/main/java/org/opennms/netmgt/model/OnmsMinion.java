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

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * <p>The OnmsMinion represents a Minion node which has reported to OpenNMS.</p>
 */
@Entity
@DiscriminatorValue(OnmsMonitoringSystem.TYPE_MINION)
public class OnmsMinion extends OnmsMonitoringSystem {

    private static final long serialVersionUID = 7512728871301272703L;

    private String m_status;

    private String m_version;

    public OnmsMinion() {
    }

    public OnmsMinion(final String id, final String location, final String status, final Date lastUpdated) {
        super(id, location);
        setStatus(status);
        setLastUpdated(lastUpdated);
    }

    @Column(name="status")
    public String getStatus() {
        return m_status;
    }

    public void setStatus(final String status) {
        m_status = status;
    }

    @Column(name="version")
    public String getVersion() {
        return m_version;
    }

    public void setVersion(final String version) {
        m_version = version;
    }

    @Override
    public String toString() {
        return "OnmsMinion [id=" + getId() + ", location=" + getLocation() + ", status=" + m_status + ", version=" + getVersion() + ", lastUpdated=" + getLastUpdated() + ", properties=" + getProperties() + "]";
    }
}
