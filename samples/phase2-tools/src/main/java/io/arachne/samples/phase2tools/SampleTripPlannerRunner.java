package io.arachne.samples.phase2tools;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import io.arachne.strands.agent.Agent;

@Component
public class SampleTripPlannerRunner implements ApplicationRunner {

    private final Agent tripPlannerAgent;

    public SampleTripPlannerRunner(Agent tripPlannerAgent) {
        this.tripPlannerAgent = tripPlannerAgent;
    }

    @Override
    public void run(ApplicationArguments args) {
        String request = "Plan a short Tokyo outing for tomorrow. Use tools if needed. Return city, forecast, and one advice sentence.";

        System.out.println("Arachne Phase 2 agent-as-tool sample");
        System.out.println("request> " + request);

        TripPlan summary = tripPlannerAgent.run(request, TripPlan.class);

        System.out.println("summary.city> " + summary.city());
        System.out.println("summary.forecast> " + summary.forecast());
        System.out.println("summary.advice> " + summary.advice());
    }
}