package org.deltav.netmgt.alarmd.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
    "org.deltav.core.daemon.common",
    "org.deltav.netmgt.alarmd.boot",
    "org.opennms.netmgt.model.jakarta.dao"
})
public class AlarmdApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlarmdApplication.class, args);
    }
}
