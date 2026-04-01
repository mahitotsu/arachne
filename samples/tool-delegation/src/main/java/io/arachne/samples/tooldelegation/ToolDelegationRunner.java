package com.mahitotsu.arachne.samples.tooldelegation;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import io.arachne.strands.agent.Agent;
import io.arachne.strands.spring.AgentFactory;

@Component
public class ToolDelegationRunner implements ApplicationRunner {

    private final AgentFactory agentFactory;

    public ToolDelegationRunner(AgentFactory agentFactory) {
        this.agentFactory = agentFactory;
    }

    @Override
    public void run(ApplicationArguments args) {
        Agent tripPlannerAgent = agentFactory.builder("trip-planner").build();
        String request = "Plan a short Tokyo outing for tomorrow. Use tools if needed. Return city, forecast, and one advice sentence.";

        System.out.println("Arachne tool delegation sample");
        System.out.println("request> " + request);

        TripPlan summary = tripPlannerAgent.run(request, TripPlan.class);

        System.out.println("summary.city> " + summary.city());
        System.out.println("summary.forecast> " + summary.forecast());
        System.out.println("summary.advice> " + summary.advice());
    }
}