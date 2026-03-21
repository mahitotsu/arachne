package io.arachne.strands.spring;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import io.arachne.strands.skills.SkillParser;

class ClasspathSkillDiscovererTest {

    @Test
    void discoversSkillsFromResourcePattern(@TempDir Path tempDir) throws IOException {
        Path skillDir = Files.createDirectories(tempDir.resolve("skills/release-checklist"));
        Files.writeString(
                skillDir.resolve("SKILL.md"),
                """
                        ---
                        name: release-checklist
                        description: Use this skill when preparing a release.
                        ---
                        Run mvn test before merging.
                        """);

        ClasspathSkillDiscoverer discoverer = new ClasspathSkillDiscoverer(
                new PathMatchingResourcePatternResolver(),
                new SkillParser(),
                tempDir.toUri() + "skills/*/SKILL.md");

        assertThat(discoverer.discover())
                .extracting(io.arachne.strands.skills.Skill::name)
                .containsExactly("release-checklist");
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
}