package io.arachne.strands.skills;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import io.arachne.strands.agent.Agent;
import io.arachne.strands.model.Model;
import io.arachne.strands.model.ModelEvent;
import io.arachne.strands.model.ToolSelection;
import io.arachne.strands.model.ToolSpec;
import io.arachne.strands.spring.AgentFactory;
import io.arachne.strands.spring.ArachneProperties;
import io.arachne.strands.types.Message;

class AgentSkillsPluginTest {

    @Test
    void builderSkillsInjectInstructionsIntoSystemPrompt() {
        RecordingSystemPromptModel model = new RecordingSystemPromptModel();
        ArachneProperties properties = new ArachneProperties();
        properties.getAgent().setSystemPrompt("Base system prompt");

        Agent agent = new AgentFactory(properties, model)
                .builder()
                .skills(new Skill(
                        "release-checklist",
                        "Use this skill when preparing a release.",
                        "Run mvn test before merging.",
                        List.of("git_status"),
                        java.util.Map.of(),
                        "java-21",
                        "Apache-2.0",
                        "/skills/release-checklist/SKILL.md"))
                .build();

        assertThat(agent.run("prepare release").text()).isEqualTo("ok");
        assertThat(model.systemPrompt()).contains("Base system prompt");
        assertThat(model.systemPrompt()).contains("<active_skills>");
        assertThat(model.systemPrompt()).contains("<name>release-checklist</name>");
        assertThat(model.systemPrompt()).contains("Run mvn test before merging.");
        assertThat(model.systemPrompt()).contains("<tool>git_status</tool>");
    }

    @Test
    void noSkillsLeavesSystemPromptUntouched() {
        RecordingSystemPromptModel model = new RecordingSystemPromptModel();
        ArachneProperties properties = new ArachneProperties();
        properties.getAgent().setSystemPrompt("Base system prompt");

        Agent agent = new AgentFactory(properties, model).builder().build();

        assertThat(agent.run("hello").text()).isEqualTo("ok");
        assertThat(model.systemPrompt()).isEqualTo("Base system prompt");
    }

    private static final class RecordingSystemPromptModel implements Model {

        private String systemPrompt;

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools) {
            throw new AssertionError("Expected the system-prompt-aware overload");
        }

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools, String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return List.of(
                    new ModelEvent.TextDelta("ok"),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        }

        @Override
        public Iterable<ModelEvent> converse(
                List<Message> messages,
                List<ToolSpec> tools,
                String systemPrompt,
                ToolSelection toolSelection) {
            return converse(messages, tools, systemPrompt);
        }

        String systemPrompt() {
            return systemPrompt;
        }
    }
}