package com.mahitotsu.arachne.samples.tooldelegation;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.mahitotsu.arachne.strands.agent.Agent;
import com.mahitotsu.arachne.strands.agent.AgentResult;
import com.mahitotsu.arachne.strands.spring.AgentFactory;

@Tag("integration")
@SpringBootTest
@EnabledIfSystemProperty(named = "arachne.integration.bedrock", matches = "true")
class ToolDelegationBedrockIntegrationTest {

    @MockitoBean
    private ToolDelegationRunner toolDelegationRunner;

    @Autowired
    private AgentFactory agentFactory;

    @Test
    void bedrockDelegationReturnsStructuredTripPlan() {
        assertThat(toolDelegationRunner).isNotNull();

        Agent tripPlannerAgent = agentFactory.builder("trip-planner").build();

        AgentResult result = tripPlannerAgent.run(
                "Plan a short Tokyo outing for tomorrow. Use tools if needed. Return city, forecast, and one advice sentence.",
                TripPlan.class);
        TripPlan summary = result.structuredOutput(TripPlan.class);

        assertThat(summary.city()).isNotBlank();
        assertThat(summary.city()).matches(city -> city.contains("東京") || city.toLowerCase().contains("tokyo"),
                "expected structured output to stay focused on Tokyo");
        assertThat(summary.forecast()).isNotBlank();
        assertThat(summary.advice()).isNotBlank();
    }
}