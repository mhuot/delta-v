package org.deltav.netmgt.perspectivepoller.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
    "org.deltav.core.daemon.common",
    "org.deltav.netmgt.perspectivepoller.boot",
    "org.opennms.netmgt.model.jakarta.dao"
})
public class PerspectivePollerdApplication {
    public static void main(String[] args) {
        SpringApplication.run(PerspectivePollerdApplication.class, args);
    }
}
