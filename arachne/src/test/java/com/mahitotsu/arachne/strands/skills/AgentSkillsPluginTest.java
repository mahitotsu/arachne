package com.mahitotsu.arachne.strands.skills;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import com.mahitotsu.arachne.strands.agent.Agent;
import com.mahitotsu.arachne.strands.model.Model;
import com.mahitotsu.arachne.strands.model.ModelEvent;
import com.mahitotsu.arachne.strands.model.ToolSelection;
import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.spring.AgentFactory;
import com.mahitotsu.arachne.strands.spring.ArachneProperties;
import com.mahitotsu.arachne.strands.types.ContentBlock;
import com.mahitotsu.arachne.strands.types.Message;

class AgentSkillsPluginTest {

    @Test
    void builderSkillsExposeCatalogAndActivationTool() {
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
                        "java-25",
                        "Apache-2.0",
                        "/skills/release-checklist/SKILL.md"))
                .build();

        assertThat(agent.run("prepare release").text()).isEqualTo("ok");
        assertThat(agent.getTools()).extracting(tool -> tool.spec().name()).contains("activate_skill", "read_skill_resource");
        assertThat(model.systemPrompt()).contains("Base system prompt");
        assertThat(model.systemPrompt()).contains("<available_skills>");
        assertThat(model.systemPrompt()).contains("<name>release-checklist</name>");
        assertThat(model.systemPrompt()).doesNotContain("Run mvn test before merging.");
        assertThat(model.systemPrompt()).contains("<tool>git_status</tool>");
        assertThat(model.systemPrompt()).contains("read_skill_resource");
    }

    @Test
    void activatedSkillBecomesActiveOnLaterTurns() {
        Skill skill = new Skill(
                "release-checklist",
                "Use this skill when preparing a release.",
                "Run mvn test before merging.",
                List.of("git_status"),
                Map.of(),
                null,
                null,
                null,
                List.of(
                    "scripts/release-check.sh",
                    "references/release-template.md",
                    "assets/release-banner.txt"));
        DelayedSkillModel model = new DelayedSkillModel();
        ArachneProperties properties = new ArachneProperties();
        properties.getAgent().setSystemPrompt("Base system prompt");

        Agent agent = new AgentFactory(properties, model)
                .builder()
                .skills(skill)
                .build();

        assertThat(agent.run("prepare release").text()).isEqualTo("loaded");
        assertThat(agent.run("what should I do now?").text()).isEqualTo("reused");

        assertThat(model.toolNames().getFirst()).contains("activate_skill");
        assertThat(model.systemPrompts()).hasSize(3);
        assertThat(model.systemPrompts().get(0)).contains("<available_skills>");
        assertThat(model.systemPrompts().get(0)).doesNotContain("Run mvn test before merging.");
        assertThat(model.systemPrompts().get(0)).doesNotContain("<active_skills>");
        assertThat(model.systemPrompts().get(1)).doesNotContain("Run mvn test before merging.");
        assertThat(model.systemPrompts().get(1)).doesNotContain("<active_skills>");
        assertThat(model.systemPrompts().get(2)).contains("<active_skills>");
        assertThat(model.systemPrompts().get(2)).contains("Run mvn test before merging.");
        assertThat(model.systemPrompts().get(2)).contains("<resource>scripts/release-check.sh</resource>");
        assertThat(model.systemPrompts().get(2)).contains("<resource>references/release-template.md</resource>");
        assertThat(model.systemPrompts().get(2)).contains("<resource>assets/release-banner.txt</resource>");
        assertThat(model.systemPrompts().get(2)).contains("read_skill_resource");
    }

    @Test
    void alreadyLoadedSkillActivationIsShortCircuitedWithoutDuplicatePromptInjection() {
        Skill skill = new Skill(
                "release-checklist",
                "Use this skill when preparing a release.",
                "Run mvn test before merging.",
                List.of("git_status"),
                Map.of(),
                null,
                null,
                null);
        DuplicateActivationModel model = new DuplicateActivationModel();
        Agent agent = new AgentFactory(new ArachneProperties(), model)
                .builder()
                .skills(skill)
                .build();

        assertThat(agent.run("prepare release").text()).isEqualTo("loaded");
        assertThat(agent.run("prepare release again").text()).isEqualTo("already loaded");

        Message duplicateToolResultMessage = agent.getMessages().get(agent.getMessages().size() - 2);
        ContentBlock.ToolResult duplicateToolResult = (ContentBlock.ToolResult) duplicateToolResultMessage.content().getFirst();

        assertThat(duplicateToolResult.content())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("type", "skill_activation")
                .containsEntry("name", "release-checklist")
                .containsEntry("alreadyLoaded", Boolean.TRUE);

        assertThat(model.systemPrompts()).hasSize(4);
        assertThat(model.systemPrompts().get(2)).contains("<active_skills>");
        assertThat(model.systemPrompts().get(2)).contains("Run mvn test before merging.");
        assertThat(countOccurrences(model.systemPrompts().get(3), "<active_skills>")).isEqualTo(1);
        assertThat(countOccurrences(model.systemPrompts().get(3), "Run mvn test before merging.")).isEqualTo(1);
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

    private static final class DelayedSkillModel implements Model {

        private final java.util.ArrayList<String> systemPrompts = new java.util.ArrayList<>();
        private final java.util.ArrayList<List<String>> toolNames = new java.util.ArrayList<>();
        private int calls;

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools) {
            throw new AssertionError("Expected the system-prompt-aware overload");
        }

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools, String systemPrompt) {
            return converse(messages, tools, systemPrompt, null);
        }

        @Override
        public Iterable<ModelEvent> converse(
                List<Message> messages,
                List<ToolSpec> tools,
                String systemPrompt,
                ToolSelection toolSelection) {
            systemPrompts.add(systemPrompt);
            toolNames.add(tools.stream().map(ToolSpec::name).toList());
            calls++;

            if (calls == 1) {
                return List.of(
                        new ModelEvent.ToolUse("skill-1", "activate_skill", Map.of("name", "release-checklist")),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }
            if (calls == 2) {
                return List.of(
                        new ModelEvent.TextDelta("loaded"),
                        new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
            }
            return List.of(
                    new ModelEvent.TextDelta("reused"),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        }

        List<String> systemPrompts() {
            return List.copyOf(systemPrompts);
        }

        List<List<String>> toolNames() {
            return List.copyOf(toolNames);
        }
    }

    private static final class DuplicateActivationModel implements Model {

        private final java.util.ArrayList<String> systemPrompts = new java.util.ArrayList<>();
        private int calls;

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools) {
            throw new AssertionError("Expected the system-prompt-aware overload");
        }

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools, String systemPrompt) {
            return converse(messages, tools, systemPrompt, null);
        }

        @Override
        public Iterable<ModelEvent> converse(
                List<Message> messages,
                List<ToolSpec> tools,
                String systemPrompt,
                ToolSelection toolSelection) {
            systemPrompts.add(systemPrompt);
            calls++;
            if (calls == 1 || calls == 3) {
                return List.of(
                        new ModelEvent.ToolUse("skill-" + calls, "activate_skill", Map.of("name", "release-checklist")),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }
            if (calls == 2) {
                return List.of(
                        new ModelEvent.TextDelta("loaded"),
                        new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
            }
            return List.of(
                    new ModelEvent.TextDelta("already loaded"),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        }

        List<String> systemPrompts() {
            return List.copyOf(systemPrompts);
        }
    }

    private static int countOccurrences(String text, String token) {
        int count = 0;
        int index = 0;
        while (index >= 0) {
            index = text.indexOf(token, index);
            if (index >= 0) {
                count++;
                index += token.length();
            }
        }
        return count;
    }
}