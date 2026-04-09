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
package org.deltav.minion.boot;

import org.deltav.core.daemon.common.EventConfEnrichmentService;
import org.deltav.minion.common.MinionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
    scanBasePackages = {
        "org.deltav.minion.common",
        "org.deltav.minion.boot",
        "org.deltav.core.daemon.common"
    },
    exclude = {
        HibernateJpaAutoConfiguration.class,
        DataJpaRepositoriesAutoConfiguration.class
    },
    excludeName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"
    }
)
@ComponentScan(
    basePackages = {
        "org.deltav.minion.common",
        "org.deltav.minion.boot",
        "org.deltav.core.daemon.common"
    },
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = EventConfEnrichmentService.class
    )
)
@EnableScheduling
@EnableConfigurationProperties(MinionProperties.class)
public class MinionApplication {

    public static void main(String[] args) {
        SpringApplication.run(MinionApplication.class, args);
    }
}
