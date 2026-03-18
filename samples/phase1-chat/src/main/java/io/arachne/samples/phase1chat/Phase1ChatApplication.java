package io.arachne.samples.phase1chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import io.arachne.strands.agent.Agent;
import io.arachne.strands.spring.AgentFactory;

@SpringBootApplication
public class Phase1ChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(Phase1ChatApplication.class, args);
    }

    @Bean
    Agent sampleAgent(AgentFactory factory) {
        return factory.builder().build();
    }
}