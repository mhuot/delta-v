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
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.opennms.core.network.IPAddress;
import org.opennms.core.utils.ByteArrayComparator;
import org.opennms.core.utils.InetAddressUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsulates a contiguous IPv4/IPv6 address range with iteration support.
 *
 * Copied from opennms-model to model-jakarta because opennms-model is excluded
 * from the Spring Boot classpath (javax/jakarta persistence split-package).
 */
public final class IPAddrRange implements Iterable<InetAddress>, Serializable {

    private static final long serialVersionUID = -106414771861377679L;
    private static final Logger LOG = LoggerFactory.getLogger(IPAddrRange.class);

    private final byte[] m_begin;
    private byte[] m_end;

    static class IPAddressRangeGenerator implements Enumeration<InetAddress>, Iterator<InetAddress> {
        private BigInteger m_next;
        private final BigInteger m_end;

        static InetAddress make(BigInteger addr) {
            try {
                return InetAddressUtils.convertBigIntegerIntoInetAddress(addr);
            } catch (UnknownHostException e) {
                return null;
            }
        }

        IPAddressRangeGenerator(byte[] start, byte[] end) {
            if (new ByteArrayComparator().compare(start, end) > 0)
                throw new IllegalArgumentException("start must be less than or equal to end");
            m_next = new BigInteger(1, Arrays.copyOf(start, start.length));
            m_end = new BigInteger(1, Arrays.copyOf(end, end.length));
        }

        @Override
        public boolean hasMoreElements() {
            return (m_next.compareTo(m_end) <= 0);
        }

        @Override
        public InetAddress nextElement() {
            if (!hasMoreElements())
                throw new NoSuchElementException("End of Range");
            InetAddress element = make(m_next);
            m_next = m_next.add(BigInteger.ONE);
            return element;
        }

        @Override
        public boolean hasNext() {
            return hasMoreElements();
        }

        @Override
        public InetAddress next() {
            return nextElement();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("remove not supported");
        }
    }

    IPAddrRange(String fromIP, String toIP) throws java.net.UnknownHostException {
        this(InetAddressUtils.addr(fromIP), InetAddressUtils.addr(toIP));
    }

    IPAddrRange(InetAddress start, InetAddress end) {
        byte[] from = start.getAddress();
        byte[] to = end.getAddress();
        if (new ByteArrayComparator().compare(from, to) > 0) {
            LOG.warn("Beginning of address range is greater than end ({} - {}), swapping",
                    InetAddressUtils.str(start), InetAddressUtils.str(end));
            m_end = from;
            m_begin = to;
        } else {
            m_begin = from;
            m_end = to;
        }
    }

    public byte[] getBegin() { return m_begin; }
    public byte[] getEnd() { return m_end; }

    public void incrementEnd() {
        m_end = new IPAddress(m_end).incr().toOctets();
    }

    boolean contains(InetAddress ipAddr) {
        return InetAddressUtils.isInetAddressInRange(ipAddr.getAddress(), m_begin, m_end);
    }

    boolean contains(String ipAddr) throws java.net.UnknownHostException {
        return InetAddressUtils.isInetAddressInRange(ipAddr, m_begin, m_end);
    }

    @Override
    public Iterator<InetAddress> iterator() {
        return new IPAddressRangeGenerator(m_begin, m_end);
    }

    Enumeration<InetAddress> elements() {
        return new IPAddressRangeGenerator(m_begin, m_end);
    }

    @Override
    public String toString() {
        return "IPAddrRange[begin=" + InetAddressUtils.getInetAddress(m_begin)
                + ",end=" + InetAddressUtils.getInetAddress(m_end) + "]";
    }

    public BigInteger size() {
        return InetAddressUtils.difference(
                InetAddressUtils.getInetAddress(m_end),
                InetAddressUtils.getInetAddress(m_begin)).add(BigInteger.ONE);
    }
}
