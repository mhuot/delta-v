package org.deltav.netmgt.provision.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
    scanBasePackages = {
        "org.deltav.core.daemon.common",
        "org.opennms.netmgt.model.jakarta.dao",
        "org.deltav.netmgt.provision.boot"
    }
    // Do NOT exclude HibernateJpaAutoConfiguration — Provisiond needs JPA
)
@EnableScheduling
public class ProvisiondApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProvisiondApplication.class, args);
    }
}
