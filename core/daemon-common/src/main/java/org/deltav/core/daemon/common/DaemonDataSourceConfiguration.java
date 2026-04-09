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

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@ConditionalOnProperty(name = "spring.datasource.url")
public class DaemonDataSourceConfiguration {
    // Spring Boot auto-configuration handles:
    // - HikariCP DataSource from spring.datasource.* properties
    // - Hibernate SessionFactory from spring.jpa.* properties
    // - JpaTransactionManager
    //
    // @EntityScan is placed on each boot application's configuration
    // to scan the correct entity package for that daemon.
    // Schema management is "none" (Liquibase manages via db-init container).
}
