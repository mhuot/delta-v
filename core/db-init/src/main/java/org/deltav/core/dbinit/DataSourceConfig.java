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
package org.deltav.core.dbinit;

import javax.sql.DataSource;

import org.postgresql.Driver;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource adminDataSource(DbInitProperties props) {
        return DataSourceBuilder.create()
                .type(SimpleDriverDataSource.class)
                .driverClassName(Driver.class.getName())
                .url(props.adminUrl())
                .username(props.adminUser())
                .password(props.adminPassword())
                .build();
    }

    @Bean
    @Primary
    public DataSource dataSource(DbInitProperties props) {
        String host = extractHost(props.adminUrl());
        String appUrl = "jdbc:postgresql://" + host + "/" + props.databaseName();
        return DataSourceBuilder.create()
                .type(SimpleDriverDataSource.class)
                .driverClassName(Driver.class.getName())
                .url(appUrl)
                .username(props.databaseUser())
                .password(props.databasePassword())
                .build();
    }

    private String extractHost(String jdbcUrl) {
        // jdbc:postgresql://host:port/dbname -> host:port
        String withoutPrefix = jdbcUrl.replace("jdbc:postgresql://", "");
        int slashIdx = withoutPrefix.indexOf('/');
        return slashIdx > 0 ? withoutPrefix.substring(0, slashIdx) : withoutPrefix;
    }
}
