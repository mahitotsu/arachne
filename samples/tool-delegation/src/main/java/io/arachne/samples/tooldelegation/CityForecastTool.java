package io.arachne.samples.tooldelegation;

import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import io.arachne.strands.agent.Agent;
import io.arachne.strands.spring.AgentFactory;
import io.arachne.strands.tool.annotation.StrandsTool;
import io.arachne.strands.tool.annotation.ToolParam;
import jakarta.validation.constraints.NotBlank;

@Service
@Validated
public class CityForecastTool {

    private final AgentFactory agentFactory;

    public CityForecastTool(AgentFactory agentFactory) {
        this.agentFactory = agentFactory;
    }

        @StrandsTool(
            description = "Look up a short forecast summary for a city by delegating to a specialist agent.",
            qualifiers = "trip-planner")
    public String lookupForecast(
            @ToolParam(description = "City name to research") @NotBlank String city,
            @ToolParam(description = "Specific travel context such as clothing, walking, or sightseeing", required = false)
            String context) {
        Agent weatherResearchAgent = agentFactory.builder("weather-research").build();
        String prompt = "Give a short forecast-oriented answer for " + city
                + ". Focus on " + (context == null || context.isBlank() ? "general travel planning" : context)
                + ". Keep it to one sentence.";
        return weatherResearchAgent.run(prompt).text();
    }
}