package dev.golemcore.brain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GolemCoreBrainApplication {

    public static void main(String[] args) {
        SpringApplication.run(GolemCoreBrainApplication.class, args);
    }
}
