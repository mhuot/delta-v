package org.opennms.core.daemon.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
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
