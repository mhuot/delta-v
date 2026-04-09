package org.deltav.netmgt.poller.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
    "org.deltav.core.daemon.common",
    "org.deltav.netmgt.poller.boot",
    "org.opennms.netmgt.model.jakarta.dao"
})
public class PollerdApplication {
    public static void main(String[] args) {
        SpringApplication.run(PollerdApplication.class, args);
    }
}
