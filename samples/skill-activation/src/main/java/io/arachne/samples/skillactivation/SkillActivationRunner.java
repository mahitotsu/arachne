package io.arachne.samples.skillactivation;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import io.arachne.strands.agent.Agent;
import io.arachne.strands.spring.AgentFactory;

@Component
public class SkillActivationRunner implements ApplicationRunner {

    private final AgentFactory agentFactory;
    private final DemoSkillsModel demoSkillsModel;

    public SkillActivationRunner(AgentFactory agentFactory, DemoSkillsModel demoSkillsModel) {
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

        Agent restoredAgent = agentFactory.builder().build();
        String secondRequest = "What should I do next?";
        System.out.println("second.request> " + secondRequest);
        System.out.println("second.reply> " + restoredAgent.run(secondRequest).text());
        System.out.println("restored.loadedSkills> " + restoredAgent.getState().get("arachne.skills.loaded"));

        List<String> prompts = demoSkillsModel.systemPrompts();
        System.out.println("prompt.catalogPresent> " + prompts.getFirst().contains("<available_skills>"));
        System.out.println("prompt.activeSkillPresentAfterRestore> " + prompts.getLast().contains("<active_skills>"));
    }
}