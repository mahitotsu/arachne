package io.arachne.samples.builtintools;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class BuiltInToolsApplication {

    public static void main(String[] args) {
        SpringApplication.run(BuiltInToolsApplication.class, args);
    }

    @Bean
    DemoBuiltInToolsModel demoBuiltInToolsModel() {
        return new DemoBuiltInToolsModel();
    }
}