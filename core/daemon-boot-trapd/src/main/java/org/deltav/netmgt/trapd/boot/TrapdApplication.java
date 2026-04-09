package org.deltav.netmgt.trapd.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
    scanBasePackages = {
        "org.deltav.core.daemon.common",
        "org.deltav.core.daemon.sink.kafka",
        "org.deltav.netmgt.trapd.boot"
    },
    exclude = {
        HibernateJpaAutoConfiguration.class,
        DataJpaRepositoriesAutoConfiguration.class
    }
)
@EnableScheduling
public class TrapdApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrapdApplication.class, args);
    }
}
