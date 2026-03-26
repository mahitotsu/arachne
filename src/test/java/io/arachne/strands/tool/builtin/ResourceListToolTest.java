package io.arachne.strands.tool.builtin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import io.arachne.strands.tool.ToolResult;

class ResourceListToolTest {

    @Test
    void listsAllowlistedFileResources(@TempDir Path tempDir) throws Exception {
        Path docsDir = Files.createDirectories(tempDir.resolve("docs"));
        Files.writeString(docsDir.resolve("a.txt"), "a");
        Files.writeString(docsDir.resolve("b.md"), "b");

        ResourceListTool tool = new ResourceListTool(
                new PathMatchingResourcePatternResolver(),
                new BuiltInResourceAccessPolicy(java.util.List.of(), java.util.List.of(tempDir.toString())));

        Object content = tool.invoke(Map.of("location", docsDir.toUri().toString())).content();

        assertThat(content)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("type", "resource_list")
                .containsEntry("location", docsDir.toUri().toString())
                .extractingByKey("resources")
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .containsExactlyInAnyOrder(
                        docsDir.resolve("a.txt").toUri().toString(),
                        docsDir.resolve("b.md").toUri().toString());
    }

    @Test
    void rejectsNonAllowlistedDirectories(@TempDir Path tempDir) throws Exception {
        Path docsDir = Files.createDirectories(tempDir.resolve("docs"));
        ResourceListTool tool = new ResourceListTool(
                new PathMatchingResourcePatternResolver(),
                new BuiltInResourceAccessPolicy(List.of(), List.of()));

        ToolResult result = tool.invoke(Map.of("location", docsDir.toUri().toString()));

        assertThat(result.status()).isEqualTo(ToolResult.ToolStatus.ERROR);
                assertThat(result.content()).isEqualTo("Resource location is not allowlisted: " + docsDir.toUri());
    }
}