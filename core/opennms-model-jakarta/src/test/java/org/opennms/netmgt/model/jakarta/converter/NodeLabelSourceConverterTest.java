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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.opennms.netmgt.model.OnmsNode.NodeLabelSource;

class NodeLabelSourceConverterTest {

    private final NodeLabelSourceConverter converter = new NodeLabelSourceConverter();

    @Test
    void convertToDatabaseColumn_user() {
        assertThat(converter.convertToDatabaseColumn(NodeLabelSource.USER)).isEqualTo("U");
    }

    @Test
    void convertToDatabaseColumn_netbios() {
        assertThat(converter.convertToDatabaseColumn(NodeLabelSource.NETBIOS)).isEqualTo("N");
    }

    @Test
    void convertToDatabaseColumn_hostname() {
        assertThat(converter.convertToDatabaseColumn(NodeLabelSource.HOSTNAME)).isEqualTo("H");
    }

    @Test
    void convertToDatabaseColumn_sysname() {
        assertThat(converter.convertToDatabaseColumn(NodeLabelSource.SYSNAME)).isEqualTo("S");
    }

    @Test
    void convertToDatabaseColumn_address() {
        assertThat(converter.convertToDatabaseColumn(NodeLabelSource.ADDRESS)).isEqualTo("A");
    }

    @Test
    void convertToDatabaseColumn_unknown() {
        assertThat(converter.convertToDatabaseColumn(NodeLabelSource.UNKNOWN)).isEqualTo(" ");
    }

    @Test
    void convertToDatabaseColumn_null() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_U() {
        assertThat(converter.convertToEntityAttribute("U")).isEqualTo(NodeLabelSource.USER);
    }

    @Test
    void convertToEntityAttribute_N() {
        assertThat(converter.convertToEntityAttribute("N")).isEqualTo(NodeLabelSource.NETBIOS);
    }

    @Test
    void convertToEntityAttribute_H() {
        assertThat(converter.convertToEntityAttribute("H")).isEqualTo(NodeLabelSource.HOSTNAME);
    }

    @Test
    void convertToEntityAttribute_S() {
        assertThat(converter.convertToEntityAttribute("S")).isEqualTo(NodeLabelSource.SYSNAME);
    }

    @Test
    void convertToEntityAttribute_A() {
        assertThat(converter.convertToEntityAttribute("A")).isEqualTo(NodeLabelSource.ADDRESS);
    }

    @Test
    void convertToEntityAttribute_space() {
        assertThat(converter.convertToEntityAttribute(" ")).isEqualTo(NodeLabelSource.UNKNOWN);
    }

    @Test
    void convertToEntityAttribute_null() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_invalid_throws() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("X"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allValues_roundTrip() {
        for (NodeLabelSource source : NodeLabelSource.values()) {
            String dbValue = converter.convertToDatabaseColumn(source);
            assertThat(dbValue).isNotNull().hasSize(1);
            NodeLabelSource result = converter.convertToEntityAttribute(dbValue);
            assertThat(result).isEqualTo(source);
        }
    }

    @Test
    void dbValues_matchCharacters() {
        for (NodeLabelSource source : NodeLabelSource.values()) {
            assertThat(converter.convertToDatabaseColumn(source))
                    .isEqualTo(String.valueOf(source.value()));
        }
    }
}
