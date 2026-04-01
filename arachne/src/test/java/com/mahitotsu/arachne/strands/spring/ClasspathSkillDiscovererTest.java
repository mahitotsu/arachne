package com.mahitotsu.arachne.strands.spring;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.mahitotsu.arachne.strands.skills.SkillParser;

class ClasspathSkillDiscovererTest {

    @Test
    void discoversSkillsFromResourcePattern(@TempDir Path tempDir) throws IOException {
        Path skillDir = Files.createDirectories(tempDir.resolve("skills/release-checklist"));
                Files.createDirectories(skillDir.resolve("scripts"));
                Files.createDirectories(skillDir.resolve("references"));
                Files.createDirectories(skillDir.resolve("assets"));
        Files.writeString(
                skillDir.resolve("SKILL.md"),
                """
                        ---
                        name: release-checklist
                        description: Use this skill when preparing a release.
                        ---
                        Run mvn test before merging.
                        """);
        Files.writeString(skillDir.resolve("scripts/release-check.sh"), "#!/usr/bin/env bash");
        Files.writeString(skillDir.resolve("references/release-template.md"), "# release template");
        Files.writeString(skillDir.resolve("assets/release-banner.txt"), "banner");

        ClasspathSkillDiscoverer discoverer = new ClasspathSkillDiscoverer(
                new PathMatchingResourcePatternResolver(),
                new SkillParser(),
                tempDir.toUri() + "skills/*/SKILL.md");

        assertThat(discoverer.discover())
                .singleElement()
                .satisfies(skill -> {
                    assertThat(skill.name()).isEqualTo("release-checklist");
                    assertThat(skill.resourceFiles())
                            .containsExactly(
                                    "scripts/release-check.sh",
                                    "references/release-template.md",
                                    "assets/release-banner.txt");
                });
    }

    @Test
    void skipsMalformedSkillDocuments(@TempDir Path tempDir) throws IOException {
        Path brokenSkillDir = Files.createDirectories(tempDir.resolve("skills/broken-skill"));
        Files.writeString(brokenSkillDir.resolve("SKILL.md"), "name: broken-skill\nbody");

        ClasspathSkillDiscoverer discoverer = new ClasspathSkillDiscoverer(
                new PathMatchingResourcePatternResolver(),
                new SkillParser(),
                tempDir.toUri() + "skills/*/SKILL.md");

        assertThat(discoverer.discover()).isEmpty();
    }

    @Test
    void duplicateSkillNamesCollapseToDeterministicSingleEntry(@TempDir Path tempDir) throws IOException {
        Path earlySkillDir = Files.createDirectories(tempDir.resolve("a/skills/release-checklist"));
        Files.writeString(
                earlySkillDir.resolve("SKILL.md"),
                """
                        ---
                        name: release-checklist
                        description: First copy
                        ---
                        Use the first instructions.
                        """);
        Path laterSkillDir = Files.createDirectories(tempDir.resolve("z/skills/release-checklist"));
        Files.writeString(
                laterSkillDir.resolve("SKILL.md"),
                """
                        ---
                        name: release-checklist
                        description: Second copy
                        ---
                        Use the second instructions.
                        """);

        ClasspathSkillDiscoverer discoverer = new ClasspathSkillDiscoverer(
                new PathMatchingResourcePatternResolver(),
                new SkillParser(),
                tempDir.toUri() + "*/skills/*/SKILL.md");

        assertThat(discoverer.discover())
                .singleElement()
                .satisfies(skill -> {
                    assertThat(skill.name()).isEqualTo("release-checklist");
                    assertThat(skill.description()).isEqualTo("Second copy");
                    assertThat(skill.instructions()).isEqualTo("Use the second instructions.");
                    assertThat(skill.location()).contains("/z/skills/release-checklist/SKILL.md");
                });
    }
}