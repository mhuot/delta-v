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
package org.opennms.netmgt.model.jakarta.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;

import org.junit.jupiter.api.Test;
import org.opennms.core.utils.InetAddressUtils;

class InetAddressConverterTest {

    private final InetAddressConverter converter = new InetAddressConverter();

    @Test
    void convertToDatabaseColumn_ipv4() throws Exception {
        InetAddress address = InetAddress.getByName("192.168.1.1");
        String result = converter.convertToDatabaseColumn(address);
        assertThat(result).isEqualTo("192.168.1.1");
    }

    @Test
    void convertToDatabaseColumn_ipv6() throws Exception {
        InetAddress address = InetAddress.getByName("::1");
        String result = converter.convertToDatabaseColumn(address);
        assertThat(result).isNotNull();
        assertThat(result).isNotEmpty();
    }

    @Test
    void convertToDatabaseColumn_null() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_ipv4() {
        InetAddress result = converter.convertToEntityAttribute("192.168.1.1");
        assertThat(result).isNotNull();
        assertThat(result.getHostAddress()).isEqualTo("192.168.1.1");
    }

    @Test
    void convertToEntityAttribute_null() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void roundTrip_ipv4() throws Exception {
        InetAddress original = InetAddress.getByName("10.0.0.1");
        String dbValue = converter.convertToDatabaseColumn(original);
        InetAddress result = converter.convertToEntityAttribute(dbValue);
        assertThat(result).isEqualTo(original);
    }

    @Test
    void roundTrip_ipv6() throws Exception {
        InetAddress original = InetAddressUtils.addr("::1");
        String dbValue = converter.convertToDatabaseColumn(original);
        InetAddress result = converter.convertToEntityAttribute(dbValue);
        assertThat(result).isEqualTo(original);
    }

    @Test
    void convertToDatabaseColumn_usesInetAddressUtilsStr() throws Exception {
        // InetAddressUtils.str() produces a normalized form — verify round-trip consistency
        InetAddress address = InetAddress.getByName("192.168.0.1");
        String dbValue = converter.convertToDatabaseColumn(address);
        assertThat(dbValue).isEqualTo(InetAddressUtils.str(address));
    }
}
