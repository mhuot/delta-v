package org.opennms.netmgt.collectd.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
    "org.opennms.core.daemon.common",
    "org.opennms.netmgt.collectd.boot",
    "org.opennms.netmgt.model.jakarta.dao"
})
public class CollectdApplication {
    public static void main(String[] args) {
        SpringApplication.run(CollectdApplication.class, args);
    }
}
