package com.mahitotsu.arachne.strands.tool.builtin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuiltInResourceAccessPolicyTest {

    @Test
    void defaultsClasspathAllowlistToClasspathRoot() {
        BuiltInResourceAccessPolicy policy = new BuiltInResourceAccessPolicy(List.of(), List.of());

        assertThat(policy.isAllowed("classpath:/application.yml")).isTrue();
    }

    @Test
    void normalizesClasspathAllowlistEntriesAndRejectsSiblings() {
        BuiltInResourceAccessPolicy policy = new BuiltInResourceAccessPolicy(List.of("classpath:docs"), List.of());

        assertThat(policy.isAllowed("classpath:/docs/reference.md")).isTrue();
        assertThat(policy.isAllowed("classpath:/other/reference.md")).isFalse();
    }

    @Test
    void allowsOnlyFilesUnderAllowlistedRoots(@TempDir Path tempDir) throws Exception {
        Path docsDir = Files.createDirectories(tempDir.resolve("docs"));
        Path allowlistedFile = Files.writeString(docsDir.resolve("guide.txt"), "guide");
        Path siblingFile = Files.writeString(tempDir.resolve("secret.txt"), "secret");
        BuiltInResourceAccessPolicy policy = new BuiltInResourceAccessPolicy(List.of(), List.of(docsDir.toString()));

        assertThat(policy.isAllowed(allowlistedFile.toUri().toString())).isTrue();
        assertThat(policy.isAllowed(siblingFile.toUri().toString())).isFalse();
    }

    @Test
    void normalizesClasspathStarAndDirectorySuffixes() {
        BuiltInResourceAccessPolicy policy = new BuiltInResourceAccessPolicy(List.of("classpath*:/docs"), List.of());

        assertThat(policy.normalizeResourceLocation("classpath:docs/file.txt")).isEqualTo("classpath:/docs/file.txt");
        assertThat(policy.normalizeDirectoryLocation("classpath*:/docs")).isEqualTo("classpath:/docs/");
        assertThat(policy.isAllowed("classpath:/docs/file.txt")).isTrue();
    }
}