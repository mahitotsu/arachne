package com.mahitotsu.arachne.samples.skillactivation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SkillActivationApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkillActivationApplication.class, args);
    }

    @Bean
    DemoSkillsModel demoSkillsModel() {
        return new DemoSkillsModel();
    }
}