package com.mahitotsu.arachne.samples.builtintools;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class BuiltInToolsApplication {

    public static void main(String[] args) {
        SpringApplication.run(BuiltInToolsApplication.class, args);
    }

    @Bean
    @ConditionalOnProperty(name = "arachne.strands.model.provider", havingValue = "deterministicBuiltIn")
    DemoBuiltInToolsModel demoBuiltInToolsModel() {
        return new DemoBuiltInToolsModel();
    }
}