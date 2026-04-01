package com.mahitotsu.arachne.samples.streamingsteering;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import io.arachne.strands.agent.Agent;
import io.arachne.strands.agent.AgentResult;
import io.arachne.strands.agent.AgentStreamEvent;
import io.arachne.strands.spring.AgentFactory;

@Component
public class StreamingSteeringRunner implements ApplicationRunner {

    private final AgentFactory agentFactory;
    private final DemoStreamingSteeringModel demoModel;

    @SuppressWarnings("unused")
    StreamingSteeringRunner(AgentFactory agentFactory, DemoStreamingSteeringModel demoModel) {
        this.agentFactory = agentFactory;
        this.demoModel = demoModel;
    }

    @Override
    public void run(ApplicationArguments args) {
        PolicyLookupTool tool = new PolicyLookupTool();
        Agent agent = agentFactory.builder()
                .tools(tool)
                .steeringHandlers(new DemoSteeringHandler())
                .useDiscoveredTools(false)
                .build();
        String prompt = prompt(args);
        List<String> streamEvents = new ArrayList<>();

        AgentResult result = agent.stream(prompt, event -> {
            switch (event) {
                case AgentStreamEvent.TextDelta textDelta -> streamEvents.add("text=" + textDelta.delta());
                case AgentStreamEvent.ToolUseRequested toolUseRequested ->
                        streamEvents.add("toolUse=" + toolUseRequested.toolName() + " " + toolUseRequested.input());
                case AgentStreamEvent.ToolResultObserved toolResultObserved ->
                        streamEvents.add("toolResult="
                                + toolResultObserved.result().status().name().toLowerCase()
                                + " "
                                + toolResultObserved.result().content());
                case AgentStreamEvent.Retry retry -> streamEvents.add("retry=" + retry.guidance());
                case AgentStreamEvent.Complete complete -> streamEvents.add("complete=" + complete.result().stopReason());
            }
        });

        System.out.println("Arachne streaming and steering sample");
        System.out.println("request> " + prompt);
        for (int index = 0; index < streamEvents.size(); index++) {
            System.out.println("stream." + (index + 1) + "> " + streamEvents.get(index));
        }
        System.out.println("final.stopReason> " + result.stopReason());
        System.out.println("final.reply> " + result.text());
        System.out.println("tool.invocations> " + tool.invocations());
        System.out.println("conversation.guidancePresent> " + demoModel.sawGuidanceMessage(agent.getMessages()));
        System.out.println("model.invocations> " + demoModel.invocationCount());
    }

    private String prompt(ApplicationArguments args) {
        List<String> promptValues = args.getOptionValues("prompt");
        if (promptValues != null && !promptValues.isEmpty()) {
            return promptValues.getFirst();
        }
        return "Can I refund an unopened item?";
    }
}