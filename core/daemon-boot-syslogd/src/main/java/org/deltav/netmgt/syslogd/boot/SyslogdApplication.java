package org.deltav.netmgt.syslogd.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
    scanBasePackages = {
        "org.deltav.core.daemon.common",
        "org.deltav.core.daemon.sink.kafka",
        "org.deltav.netmgt.syslogd.boot"
    },
    exclude = {
        HibernateJpaAutoConfiguration.class,
        DataJpaRepositoriesAutoConfiguration.class
    }
)
@EnableScheduling
public class SyslogdApplication {

    public static void main(String[] args) {
        SpringApplication.run(SyslogdApplication.class, args);
    }
}
