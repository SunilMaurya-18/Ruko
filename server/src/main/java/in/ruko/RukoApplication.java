package in.ruko;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RukoApplication {

    public static void main(String[] args) {
        SpringApplication.run(RukoApplication.class, args);
    }
}
