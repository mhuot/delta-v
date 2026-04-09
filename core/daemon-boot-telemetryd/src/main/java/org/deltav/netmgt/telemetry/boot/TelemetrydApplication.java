package org.deltav.netmgt.telemetry.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
    "org.opennms.core.daemon.common",
    "org.opennms.netmgt.telemetry.boot",
    "org.opennms.netmgt.model.jakarta.dao"
})
public class TelemetrydApplication {
    public static void main(String[] args) {
        SpringApplication.run(TelemetrydApplication.class, args);
    }
}
