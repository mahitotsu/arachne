package com.mahitotsu.arachne.strands.tool.builtin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.mahitotsu.arachne.strands.tool.ToolResult;

class ResourceReaderToolTest {

    @Test
    void readsAllowlistedFileResources(@TempDir Path tempDir) throws Exception {
        Path file = Files.writeString(tempDir.resolve("notes.txt"), "hello\n");
        ResourceReaderTool tool = new ResourceReaderTool(
                new PathMatchingResourcePatternResolver(),
                new BuiltInResourceAccessPolicy(java.util.List.of(), java.util.List.of(tempDir.toString())));

        Object content = tool.invoke(Map.of("location", file.toUri().toString())).content();

        assertThat(content)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("type", "resource")
                .containsEntry("encoding", "utf-8")
                .containsEntry("content", "hello\n");
    }

    @Test
    void rejectsNonAllowlistedResources(@TempDir Path tempDir) throws Exception {
        Path file = Files.writeString(tempDir.resolve("notes.txt"), "hello\n");
        ResourceReaderTool tool = new ResourceReaderTool(
                new PathMatchingResourcePatternResolver(),
                new BuiltInResourceAccessPolicy(java.util.List.of(), java.util.List.of()));

        ToolResult result = tool.invoke(Map.of("location", file.toUri().toString()));

        assertThat(result.status()).isEqualTo(ToolResult.ToolStatus.ERROR);
        assertThat(result.content()).isEqualTo("Resource location is not allowlisted: " + file.toUri());
    }

    @Test
    void rejectsClasspathResourcesOutsideAllowlistedRoots() {
        ResourceReaderTool tool = new ResourceReaderTool(
                new PathMatchingResourcePatternResolver(),
                new BuiltInResourceAccessPolicy(java.util.List.of("classpath:/docs/"), java.util.List.of()));

        ToolResult result = tool.invoke(Map.of("location", "classpath:/application.yml"));

        assertThat(result.status()).isEqualTo(ToolResult.ToolStatus.ERROR);
        assertThat(result.content()).isEqualTo("Resource location is not allowlisted: classpath:/application.yml");
    }
}