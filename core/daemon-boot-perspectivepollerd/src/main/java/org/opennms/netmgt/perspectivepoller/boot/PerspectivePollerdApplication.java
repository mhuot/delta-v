package org.opennms.netmgt.perspectivepoller.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
    "org.opennms.core.daemon.common",
    "org.opennms.netmgt.perspectivepoller.boot",
    "org.opennms.netmgt.model.jakarta.dao"
})
public class PerspectivePollerdApplication {
    public static void main(String[] args) {
        SpringApplication.run(PerspectivePollerdApplication.class, args);
    }
}
