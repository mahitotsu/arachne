package io.arachne.samples.builtintools;

import java.util.List;
import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import io.arachne.strands.agent.Agent;
import io.arachne.strands.spring.AgentFactory;
import io.arachne.strands.types.ContentBlock;

@Component
public class BuiltInToolsRunner implements ApplicationRunner {

    private final AgentFactory agentFactory;

    BuiltInToolsRunner(AgentFactory agentFactory) {
        this.agentFactory = agentFactory;
    }

    @Override
    public void run(ApplicationArguments args) {
        Agent defaultAgent = agentFactory.builder().build();
        Agent readerAgent = agentFactory.builder("reader").build();
        Agent strictAgent = agentFactory.builder("strict").build();

        System.out.println("Arachne built-in tools sample");
        System.out.println("default.tools> " + toolNames(defaultAgent));
        System.out.println("reader.tools> " + toolNames(readerAgent));
        System.out.println("strict.tools> " + toolNames(strictAgent));

        System.out.println("default.reply> " + defaultAgent.run("Run default profile.").text());
        System.out.println("default.toolResults> " + observedToolResultTypes(defaultAgent));

        System.out.println("reader.reply> " + readerAgent.run("Run reader profile.").text());
        System.out.println("reader.toolResults> " + observedToolResultTypes(readerAgent));
    }

    private String toolNames(Agent agent) {
        List<String> names = agent.getTools().stream().map(tool -> tool.spec().name()).toList();
        return names.isEmpty() ? "(none)" : String.join(", ", names);
    }

    private String observedToolResultTypes(Agent agent) {
        return agent.getMessages().stream()
                .flatMap(message -> message.content().stream())
                .filter(ContentBlock.ToolResult.class::isInstance)
                .map(ContentBlock.ToolResult.class::cast)
                .map(ContentBlock.ToolResult::content)
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(content -> String.valueOf(content.get("type")))
                .distinct()
                .toList()
                .toString();
    }
}