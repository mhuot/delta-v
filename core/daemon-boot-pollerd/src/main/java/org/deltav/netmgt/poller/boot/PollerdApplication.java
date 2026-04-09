package org.opennms.netmgt.poller.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
    "org.opennms.core.daemon.common",
    "org.opennms.netmgt.poller.boot",
    "org.opennms.netmgt.model.jakarta.dao"
})
public class PollerdApplication {
    public static void main(String[] args) {
        SpringApplication.run(PollerdApplication.class, args);
    }
}
