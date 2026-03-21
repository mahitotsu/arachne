package io.arachne.samples.phase5skills;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Phase5SkillsApplication {

    public static void main(String[] args) {
        SpringApplication.run(Phase5SkillsApplication.class, args);
    }

    @Bean
    DemoSkillsModel demoSkillsModel() {
        return new DemoSkillsModel();
    }
}