package io.arachne.samples.phase2tools;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;

import io.arachne.strands.agent.Agent;
import io.arachne.strands.spring.AgentFactory;

@SpringBootApplication
public class Phase2ToolsApplication {

    public static void main(String[] args) {
        SpringApplication.run(Phase2ToolsApplication.class, args);
    }

    @Bean
    @Qualifier("tripPlanner")
    @SuppressWarnings("unused")
    Agent tripPlannerAgent(AgentFactory factory) {
        return factory.builder("trip-planner")
                .build();
    }

    @Bean
    @Qualifier("weatherResearch")
    @SuppressWarnings("unused")
    Agent weatherResearchAgent(AgentFactory factory) {
        return factory.builder("weather-research")
                .build();
    }
}