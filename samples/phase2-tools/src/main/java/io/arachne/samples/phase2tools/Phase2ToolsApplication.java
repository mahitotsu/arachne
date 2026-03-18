package io.arachne.samples.phase2tools;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import io.arachne.strands.agent.Agent;
import io.arachne.strands.spring.AgentFactory;

@SpringBootApplication
public class Phase2ToolsApplication {

    public static void main(String[] args) {
        SpringApplication.run(Phase2ToolsApplication.class, args);
    }

    @Bean
    @SuppressWarnings("unused")
    Agent tripPlannerAgent(AgentFactory factory) {
        return factory.builder()
                .toolQualifiers("trip-planner")
                .systemPrompt("You are a trip planner. Call tools when you need local facts, then return concise travel advice.")
                .build();
    }

    @Bean
    @SuppressWarnings("unused")
    Agent weatherResearchAgent(AgentFactory factory) {
        return factory.builder()
                .useDiscoveredTools(false)
                .systemPrompt("You are a weather research specialist. Answer with one short factual sentence.")
                .build();
    }
}