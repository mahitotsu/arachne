package com.mahitotsu.arachne.strands.skills;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mahitotsu.arachne.strands.tool.ToolResult;

class SkillResourceToolTest {

    @Test
    void returnsStructuredPayloadForKnownTextResource(@TempDir Path tempDir) throws Exception {
        Path skillDir = Files.createDirectories(tempDir.resolve("release-checklist"));
        Files.writeString(skillDir.resolve("SKILL.md"), "---\nname: release-checklist\ndescription: Release guidance\n---\nRun tests\n");
        Files.createDirectories(skillDir.resolve("references"));
        Files.writeString(skillDir.resolve("references/release-template.md"), "# Template\n- Run tests\n");

        SkillResourceTool tool = new SkillResourceTool(List.of(new Skill(
                "release-checklist",
                "Release guidance",
                "Run tests",
                List.of(),
                Map.of(),
                null,
                null,
                skillDir.resolve("SKILL.md").toUri().toString(),
                List.of("references/release-template.md"))));

        Object content = tool.invoke(Map.of("name", "release-checklist", "path", "references/release-template.md")).content();

        assertThat(content)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("type", "skill_resource")
                .containsEntry("name", "release-checklist")
                .containsEntry("path", "references/release-template.md")
                .containsEntry("encoding", "utf-8")
                .containsEntry("mediaType", "text/plain")
                .containsEntry("content", "# Template\n- Run tests\n");
    }

    @Test
    void rejectsUnknownResourcePaths() {
        SkillResourceTool tool = new SkillResourceTool(List.of(new Skill(
                "release-checklist",
                "Release guidance",
                "Run tests",
                List.of(),
                Map.of(),
                null,
                null,
                "/skills/release-checklist/SKILL.md",
                List.of("references/release-template.md"))));

        ToolResult result = tool.invoke(Map.of("name", "release-checklist", "path", "references/missing.md"));

        assertThat(result.status()).isEqualTo(ToolResult.ToolStatus.ERROR);
        assertThat(result.content()).isEqualTo("Unknown skill resource: references/missing.md");
    }
}