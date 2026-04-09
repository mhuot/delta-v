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
