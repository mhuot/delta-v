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
import org.opennms.netmgt.model.OnmsNode.NodeType;

class NodeTypeConverterTest {

    private final NodeTypeConverter converter = new NodeTypeConverter();

    @Test
    void convertToDatabaseColumn_active() {
        assertThat(converter.convertToDatabaseColumn(NodeType.ACTIVE)).isEqualTo("A");
    }

    @Test
    void convertToDatabaseColumn_deleted() {
        assertThat(converter.convertToDatabaseColumn(NodeType.DELETED)).isEqualTo("D");
    }

    @Test
    void convertToDatabaseColumn_unknown() {
        assertThat(converter.convertToDatabaseColumn(NodeType.UNKNOWN)).isEqualTo(" ");
    }

    @Test
    void convertToDatabaseColumn_null() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_A() {
        assertThat(converter.convertToEntityAttribute("A")).isEqualTo(NodeType.ACTIVE);
    }

    @Test
    void convertToEntityAttribute_D() {
        assertThat(converter.convertToEntityAttribute("D")).isEqualTo(NodeType.DELETED);
    }

    @Test
    void convertToEntityAttribute_space() {
        assertThat(converter.convertToEntityAttribute(" ")).isEqualTo(NodeType.UNKNOWN);
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
        for (NodeType type : NodeType.values()) {
            String dbValue = converter.convertToDatabaseColumn(type);
            assertThat(dbValue).isNotNull().hasSize(1);
            NodeType result = converter.convertToEntityAttribute(dbValue);
            assertThat(result).isEqualTo(type);
        }
    }

    @Test
    void dbValues_matchCharacters() {
        assertThat(converter.convertToDatabaseColumn(NodeType.ACTIVE)).isEqualTo(String.valueOf(NodeType.ACTIVE.value()));
        assertThat(converter.convertToDatabaseColumn(NodeType.DELETED)).isEqualTo(String.valueOf(NodeType.DELETED.value()));
        assertThat(converter.convertToDatabaseColumn(NodeType.UNKNOWN)).isEqualTo(String.valueOf(NodeType.UNKNOWN.value()));
    }
}
