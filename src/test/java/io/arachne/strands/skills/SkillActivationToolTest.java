package io.arachne.strands.skills;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import io.arachne.strands.tool.ToolResult;

class SkillActivationToolTest {

    @Test
    void returnsStructuredSkillPayloadForKnownSkill() {
        SkillActivationTool tool = new SkillActivationTool(List.of(
                new Skill(
                        "release-checklist",
                        "Use this skill when preparing a release.",
                        "Run mvn test before merging.",
                        List.of("git_status"),
                        Map.of(),
                        "java-21",
                        "Apache-2.0",
                        "/skills/release-checklist/SKILL.md")));

        Object content = tool.invoke(Map.of("name", "release-checklist")).content();

        assertThat(content)
                .isInstanceOf(Map.class)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("type", "skill_activation")
                .containsEntry("name", "release-checklist")
                .containsEntry("instructions", "Run mvn test before merging.")
                .containsEntry("alreadyLoaded", Boolean.FALSE);
    }

    @Test
    void rejectsUnknownSkillNames() {
        SkillActivationTool tool = new SkillActivationTool(List.of(
                new Skill("release-checklist", "Use this skill when preparing a release.", "Run mvn test before merging.")));
        ToolResult result = tool.invoke(Map.of("name", "missing"));

        assertThat(result.status()).isEqualTo(ToolResult.ToolStatus.ERROR);
        assertThat(result.content()).isEqualTo("Unknown skill: missing");
    }
}