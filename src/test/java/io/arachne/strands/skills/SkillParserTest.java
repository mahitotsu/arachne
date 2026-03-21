package io.arachne.strands.skills;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class SkillParserTest {

    private final SkillParser parser = new SkillParser();

    @Test
    void parsesSkillMarkdownWithOptionalFields() {
        Skill skill = parser.parse("""
                ---
                name: release-checklist
                description: Use this skill when preparing a release.
                allowed-tools: [git_status, git_log]
                compatibility: java-21
                license: Apache-2.0
                metadata:
                  owner: platform
                  tags:
                    - release
                    - qa
                ---
                # Release checklist

                1. Review the diff.
                2. Run the tests.
                """);

        assertThat(skill.name()).isEqualTo("release-checklist");
        assertThat(skill.description()).isEqualTo("Use this skill when preparing a release.");
        assertThat(skill.allowedTools()).containsExactly("git_status", "git_log");
        assertThat(skill.compatibility()).isEqualTo("java-21");
        assertThat(skill.license()).isEqualTo("Apache-2.0");
        assertThat(skill.metadata()).containsEntry("owner", "platform");
        assertThat(skill.metadata().get("tags")).isEqualTo(List.of("release", "qa"));
        assertThat(skill.instructions()).contains("# Release checklist");
    }

    @Test
    void retriesYamlParsingWhenDescriptionContainsColon() {
        Skill skill = parser.parse("""
                ---
                name: pdf-processing
                description: Use this skill when: the user needs help with PDFs
                ---
                Read the PDF instructions.
                """);

        assertThat(skill.description()).isEqualTo("Use this skill when: the user needs help with PDFs");
        assertThat(skill.instructions()).isEqualTo("Read the PDF instructions.");
    }

    @Test
    void rejectsMissingFrontmatterDelimiter() {
        assertThatThrownBy(() -> parser.parse("name: no-frontmatter\ntext"))
                .isInstanceOf(SkillParseException.class)
                .hasMessageContaining("must start with a YAML frontmatter block");
    }

    @Test
    void rejectsMissingRequiredFields() {
        assertThatThrownBy(() -> parser.parse("""
                ---
                name: missing-description
                ---
                body
                """))
                .isInstanceOf(SkillParseException.class)
                .hasMessageContaining("description");
    }

    @Test
    void rejectsUnsupportedAllowedToolsShape() {
        assertThatThrownBy(() -> parser.parse("""
                ---
                name: invalid-tools
                description: Invalid allowed-tools example.
                allowed-tools:
                  name: tool-a
                ---
                body
                """))
                .isInstanceOf(SkillParseException.class)
                .hasMessageContaining("allowed-tools");
    }

    @Test
    void skillModelCopiesCollectionsDefensively() {
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("owner", "platform");
        Skill skill = new Skill("release-checklist", "Release guidance", "Run tests", List.of("git_status"), metadata, null, null, null);
        metadata.put("owner", "mutated");

        assertThat(skill.metadata()).containsEntry("owner", "platform");
        assertThatThrownBy(() -> skill.allowedTools().add("other"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}