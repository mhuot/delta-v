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

import org.junit.jupiter.api.Test;
import org.opennms.netmgt.model.OnmsSeverity;

class OnmsSeverityConverterTest {

    private final OnmsSeverityConverter converter = new OnmsSeverityConverter();

    @Test
    void convertToDatabaseColumn_null_returnsOne() {
        // Legacy UserType stores null as 1 (INDETERMINATE)
        assertThat(converter.convertToDatabaseColumn(null)).isEqualTo(1);
    }

    @Test
    void convertToDatabaseColumn_indeterminate() {
        assertThat(converter.convertToDatabaseColumn(OnmsSeverity.INDETERMINATE)).isEqualTo(1);
    }

    @Test
    void convertToDatabaseColumn_critical() {
        assertThat(converter.convertToDatabaseColumn(OnmsSeverity.CRITICAL)).isEqualTo(7);
    }

    @Test
    void convertToEntityAttribute_null() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_one() {
        assertThat(converter.convertToEntityAttribute(1)).isEqualTo(OnmsSeverity.INDETERMINATE);
    }

    @Test
    void convertToEntityAttribute_seven() {
        assertThat(converter.convertToEntityAttribute(7)).isEqualTo(OnmsSeverity.CRITICAL);
    }

    @Test
    void allValues_roundTrip() {
        for (OnmsSeverity severity : OnmsSeverity.values()) {
            Integer dbValue = converter.convertToDatabaseColumn(severity);
            assertThat(dbValue).isEqualTo(severity.getId());
            OnmsSeverity result = converter.convertToEntityAttribute(dbValue);
            assertThat(result).isEqualTo(severity);
        }
    }

    @Test
    void allValues_correctIds() {
        assertThat(converter.convertToDatabaseColumn(OnmsSeverity.INDETERMINATE)).isEqualTo(1);
        assertThat(converter.convertToDatabaseColumn(OnmsSeverity.CLEARED)).isEqualTo(2);
        assertThat(converter.convertToDatabaseColumn(OnmsSeverity.NORMAL)).isEqualTo(3);
        assertThat(converter.convertToDatabaseColumn(OnmsSeverity.WARNING)).isEqualTo(4);
        assertThat(converter.convertToDatabaseColumn(OnmsSeverity.MINOR)).isEqualTo(5);
        assertThat(converter.convertToDatabaseColumn(OnmsSeverity.MAJOR)).isEqualTo(6);
        assertThat(converter.convertToDatabaseColumn(OnmsSeverity.CRITICAL)).isEqualTo(7);
    }
}
