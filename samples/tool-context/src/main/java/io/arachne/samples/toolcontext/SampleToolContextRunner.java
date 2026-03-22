package io.arachne.samples.toolcontext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import io.arachne.strands.agent.Agent;
import io.arachne.strands.spring.AgentFactory;

@Component
public class SampleToolContextRunner implements ApplicationRunner {

    private final AgentFactory agentFactory;
    private final DemoRequestContext requestContext;

    public SampleToolContextRunner(AgentFactory agentFactory, DemoRequestContext requestContext) {
        this.agentFactory = agentFactory;
        this.requestContext = requestContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        Agent agent = agentFactory.builder()
                .toolExecutionMode(io.arachne.strands.tool.ToolExecutionMode.PARALLEL)
                .build();
        String prompt = "Demonstrate the tool context split.";

        requestContext.clear();
        requestContext.setCurrentRequestId("demo-request-42");
        try {
            String reply = agent.run(prompt).text();

            System.out.println("Arachne Tool Context sample");
            System.out.println("request> " + prompt);
            System.out.println("final.reply> " + reply);
            System.out.println("state.toolCalls> " + sortedEntries(agent.getState().get("toolCalls")));
            System.out.println("propagation.requestIds> " + sortedEntries(requestContext.observedRequestIds()));
            System.out.println("tool.results> " + sortedEntries(agent.getState().get("toolResults")));
        } finally {
            requestContext.clear();
        }
    }

    private List<String> sortedEntries(Object value) {
        List<String> entries = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                entries.add(String.valueOf(item));
            }
        }
        entries.sort(Comparator.naturalOrder());
        return List.copyOf(entries);
    }
}
