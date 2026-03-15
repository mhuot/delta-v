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
import org.opennms.netmgt.model.PrimaryType;

class PrimaryTypeConverterTest {

    private final PrimaryTypeConverter converter = new PrimaryTypeConverter();

    @Test
    void convertToDatabaseColumn_primary() {
        assertThat(converter.convertToDatabaseColumn(PrimaryType.PRIMARY)).isEqualTo("P");
    }

    @Test
    void convertToDatabaseColumn_secondary() {
        assertThat(converter.convertToDatabaseColumn(PrimaryType.SECONDARY)).isEqualTo("S");
    }

    @Test
    void convertToDatabaseColumn_notEligible() {
        assertThat(converter.convertToDatabaseColumn(PrimaryType.NOT_ELIGIBLE)).isEqualTo("N");
    }

    @Test
    void convertToDatabaseColumn_null() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_P() {
        PrimaryType result = converter.convertToEntityAttribute("P");
        assertThat(result).isEqualTo(PrimaryType.PRIMARY);
    }

    @Test
    void convertToEntityAttribute_S() {
        PrimaryType result = converter.convertToEntityAttribute("S");
        assertThat(result).isEqualTo(PrimaryType.SECONDARY);
    }

    @Test
    void convertToEntityAttribute_N() {
        PrimaryType result = converter.convertToEntityAttribute("N");
        assertThat(result).isEqualTo(PrimaryType.NOT_ELIGIBLE);
    }

    @Test
    void convertToEntityAttribute_null() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void roundTrip_allTypes() {
        for (PrimaryType type : PrimaryType.getAllTypes()) {
            String dbValue = converter.convertToDatabaseColumn(type);
            assertThat(dbValue).hasSize(1);
            PrimaryType result = converter.convertToEntityAttribute(dbValue);
            assertThat(result).isEqualTo(type);
        }
    }
}
