package org.opennms.netmgt.perspectivepoller.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PerspectivePollerdApplication {
    public static void main(String[] args) {
        SpringApplication.run(PerspectivePollerdApplication.class, args);
    }
}
