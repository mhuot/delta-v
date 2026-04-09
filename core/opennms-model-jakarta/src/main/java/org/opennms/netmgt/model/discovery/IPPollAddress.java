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
package org.opennms.netmgt.model.discovery;

import java.io.Serializable;
import java.net.InetAddress;

/**
 * Polling information used by the discovery process and PingSweepRpcModule.
 * Each instance encapsulates an internet address, timeout in milliseconds,
 * and a retry count.
 *
 * Copied from opennms-model to model-jakarta because opennms-model is excluded
 * from the Spring Boot classpath (javax/jakarta persistence split-package).
 */
public class IPPollAddress implements Serializable {

    private static final long serialVersionUID = -4162816651553193934L;

    private final String m_foreignSource;
    private final String m_location;
    private final InetAddress m_address;
    private final long m_timeout;
    private final int m_retries;

    public IPPollAddress(final String foreignSource, final String location,
                         final InetAddress ipAddress, final long timeout, final int retries) {
        m_foreignSource = foreignSource;
        m_location = location;
        m_address = ipAddress;
        m_timeout = timeout;
        m_retries = retries;
    }

    public String getForeignSource() {
        return m_foreignSource;
    }

    public String getLocation() {
        return m_location;
    }

    public long getTimeout() {
        return m_timeout;
    }

    public int getRetries() {
        return m_retries;
    }

    public InetAddress getAddress() {
        return m_address;
    }

    @Override
    public boolean equals(final Object object) {
        if (object instanceof IPPollAddress) {
            IPPollAddress other = (IPPollAddress) object;
            return other.getAddress().equals(m_address)
                    && other.getRetries() == m_retries
                    && other.getTimeout() == m_timeout;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(m_address, m_retries, m_timeout);
    }

    @Override
    public String toString() {
        return "IPPollAddress[address=" + m_address
                + ",retries=" + m_retries
                + ",timeout=" + m_timeout + "]";
    }
}
