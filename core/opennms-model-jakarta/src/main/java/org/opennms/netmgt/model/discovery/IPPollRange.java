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
import java.util.Enumeration;
import java.util.Iterator;

/**
 * Encapsulates an address range plus retry/timeout information for discovery.
 *
 * Copied from opennms-model to model-jakarta because opennms-model is excluded
 * from the Spring Boot classpath (javax/jakarta persistence split-package).
 */
public class IPPollRange implements Iterable<IPPollAddress>, Serializable {
    private static final long serialVersionUID = -287583115922481242L;

    private final IPAddrRange m_range;
    private final String m_foreignSource;
    private final String m_location;
    private final long m_timeout;
    private final int m_retries;

    final class IPPollRangeGenerator implements Enumeration<IPPollAddress>, Iterator<IPPollAddress> {
        private final Enumeration<InetAddress> m_range;

        public IPPollRangeGenerator(Enumeration<InetAddress> en) {
            m_range = en;
        }

        @Override public boolean hasMoreElements() { return m_range.hasMoreElements(); }
        @Override public IPPollAddress nextElement() {
            return new IPPollAddress(m_foreignSource, m_location, m_range.nextElement(), m_timeout, m_retries);
        }
        @Override public boolean hasNext() { return hasMoreElements(); }
        @Override public IPPollAddress next() { return nextElement(); }
        @Override public void remove() { throw new UnsupportedOperationException("remove not supported"); }
    }

    public IPPollRange(String foreignSource, String location, String fromIP, String toIP,
                       long timeout, int retries) throws java.net.UnknownHostException {
        m_range = new IPAddrRange(fromIP, toIP);
        m_foreignSource = foreignSource;
        m_location = location;
        m_timeout = timeout;
        m_retries = retries;
    }

    public IPPollRange(String foreignSource, String location, InetAddress start, InetAddress end,
                       long timeout, int retries) {
        this(foreignSource, location, new IPAddrRange(start, end), timeout, retries);
    }

    IPPollRange(String foreignSource, String location, IPAddrRange range, long timeout, int retries) {
        m_range = range;
        m_foreignSource = foreignSource;
        m_location = location;
        m_timeout = timeout;
        m_retries = retries;
    }

    public String getForeignSource() { return m_foreignSource; }
    public String getLocation() { return m_location; }
    public long getTimeout() { return m_timeout; }
    public int getRetries() { return m_retries; }
    public IPAddrRange getAddressRange() { return m_range; }

    public Enumeration<IPPollAddress> elements() {
        return new IPPollRangeGenerator(m_range.elements());
    }

    @Override
    public Iterator<IPPollAddress> iterator() {
        return new IPPollRangeGenerator(m_range.elements());
    }

    @Override
    public String toString() {
        return "IPPollRange[foreignSource=" + m_foreignSource
                + ",location=" + m_location
                + ",range=" + m_range
                + ",timeout=" + m_timeout
                + ",retries=" + m_retries + "]";
    }
}
