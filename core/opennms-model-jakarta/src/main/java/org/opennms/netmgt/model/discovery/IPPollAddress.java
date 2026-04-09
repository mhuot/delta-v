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
