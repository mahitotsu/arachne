package io.arachne.samples.skillactivation;

import java.util.List;
import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import io.arachne.strands.agent.Agent;
import io.arachne.strands.spring.AgentFactory;
import io.arachne.strands.types.ContentBlock;

@Component
public class SkillActivationRunner implements ApplicationRunner {

    private final AgentFactory agentFactory;
    private final DemoSkillsModel demoSkillsModel;

    SkillActivationRunner(AgentFactory agentFactory, DemoSkillsModel demoSkillsModel) {
        this.agentFactory = agentFactory;
        this.demoSkillsModel = demoSkillsModel;
    }

    @Override
    public void run(ApplicationArguments args) {
        Agent firstAgent = agentFactory.builder().build();
        String firstRequest = "Prepare today's release.";

        System.out.println("Arachne skill activation sample");
        System.out.println("tools> " + String.join(", ", firstAgent.getTools().stream().map(tool -> tool.spec().name()).toList()));
        System.out.println("first.request> " + firstRequest);
        System.out.println("first.reply> " + firstAgent.run(firstRequest).text());
        System.out.println("state.loadedSkills> " + firstAgent.getState().get("arachne.skills.loaded"));
        System.out.println("first.resourceReads> " + observedToolTypes(firstAgent));
        System.out.println("first.referencePath> " + observedResourcePath(firstAgent));

        Agent restoredAgent = agentFactory.builder().build();
        String secondRequest = "What should I do next?";
        System.out.println("second.request> " + secondRequest);
        System.out.println("second.reply> " + restoredAgent.run(secondRequest).text());
        System.out.println("restored.loadedSkills> " + restoredAgent.getState().get("arachne.skills.loaded"));

        List<String> prompts = demoSkillsModel.systemPrompts();
        System.out.println("prompt.catalogPresent> " + prompts.getFirst().contains("<available_skills>"));
        System.out.println("prompt.activeSkillPresentAfterRestore> " + prompts.getLast().contains("<active_skills>"));
        System.out.println("prompt.resourceListPresentAfterRestore> " + prompts.getLast().contains("<resources>"));
    }

    private String observedToolTypes(Agent agent) {
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

    private String observedResourcePath(Agent agent) {
        return agent.getMessages().stream()
                .flatMap(message -> message.content().stream())
                .filter(ContentBlock.ToolResult.class::isInstance)
                .map(ContentBlock.ToolResult.class::cast)
                .map(ContentBlock.ToolResult::content)
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .filter(content -> "skill_resource".equals(content.get("type")))
                .map(content -> String.valueOf(content.get("path")))
                .findFirst()
                .orElse("none");
    }
}