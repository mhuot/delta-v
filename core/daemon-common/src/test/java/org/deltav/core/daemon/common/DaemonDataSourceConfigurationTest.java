/*
 * Copyright (C) 2026 BeaconStrategists, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
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
package org.deltav.core.daemon.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

class DaemonDataSourceConfigurationTest {

    @Test
    void hasRequiredAnnotations() {
        assertThat(DaemonDataSourceConfiguration.class.isAnnotationPresent(Configuration.class)).isTrue();
        assertThat(DaemonDataSourceConfiguration.class.isAnnotationPresent(EnableTransactionManagement.class)).isTrue();
    }

    @Test
    void hasConditionalOnPropertyGuard() {
        ConditionalOnProperty annotation = DaemonDataSourceConfiguration.class
            .getAnnotation(ConditionalOnProperty.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.name()).containsExactly("spring.datasource.url");
    }

    @Test
    void entityScanIsNotOnSharedClass() {
        // @EntityScan is intentionally NOT on DaemonDataSourceConfiguration.
        // Each Spring Boot application places @EntityScan on its own configuration
        // class to scan the correct entity package for that daemon.
        assertThat(DaemonDataSourceConfiguration.class.getAnnotations())
                .noneMatch(a -> a.annotationType().getSimpleName().equals("EntityScan"));
    }
}
