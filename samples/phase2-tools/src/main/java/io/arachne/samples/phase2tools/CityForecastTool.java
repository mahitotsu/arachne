package io.arachne.samples.phase2tools;

import jakarta.validation.constraints.NotBlank;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import io.arachne.strands.agent.Agent;
import io.arachne.strands.tool.annotation.StrandsTool;
import io.arachne.strands.tool.annotation.ToolParam;

@Service
@Validated
public class CityForecastTool {

    private final Agent weatherResearchAgent;

    public CityForecastTool(@Qualifier("weatherResearch") Agent weatherResearchAgent) {
        this.weatherResearchAgent = weatherResearchAgent;
    }

        @StrandsTool(
            description = "Look up a short forecast summary for a city by delegating to a specialist agent.",
            qualifiers = "trip-planner")
    public String lookupForecast(
            @ToolParam(description = "City name to research") @NotBlank String city,
            @ToolParam(description = "Specific travel context such as clothing, walking, or sightseeing", required = false)
            String context) {
        String prompt = "Give a short forecast-oriented answer for " + city
                + ". Focus on " + (context == null || context.isBlank() ? "general travel planning" : context)
                + ". Keep it to one sentence.";
        return weatherResearchAgent.run(prompt).text();
    }
}