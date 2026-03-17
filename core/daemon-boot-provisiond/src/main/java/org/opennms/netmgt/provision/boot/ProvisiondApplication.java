package org.opennms.netmgt.provision.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for Provisiond.
 * Full configuration will be added in a subsequent task.
 */
@SpringBootApplication
public class ProvisiondApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProvisiondApplication.class, args);
    }
}
