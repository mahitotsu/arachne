package io.arachne.docs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ByteArrayResource;

class DocumentedYamlExamplesTest {

    private static final Pattern YAML_FENCE = Pattern.compile("```yaml\\R(.*?)\\R```", Pattern.DOTALL);

    @Test
    void documentedArachneYamlExamplesRemainParseable() throws IOException {
        List<Path> markdownFiles = List.of(
                Path.of("README.md"),
                Path.of("docs/user-guide.md"),
                Path.of("samples/conversation-basics/README.md"),
                Path.of("samples/tool-delegation/README.md"),
                Path.of("samples/tool-execution-context/README.md"),
                Path.of("samples/session-redis/README.md"),
                Path.of("samples/session-jdbc/README.md"),
                Path.of("samples/approval-workflow/README.md"),
                Path.of("samples/skill-activation/README.md"),
                Path.of("samples/streaming-steering/README.md"));

        int validatedBlocks = 0;
        for (Path markdownFile : markdownFiles) {
            String content = Files.readString(markdownFile, StandardCharsets.UTF_8);
            Matcher matcher = YAML_FENCE.matcher(content);
            while (matcher.find()) {
                String yamlBlock = matcher.group(1);
                if (!yamlBlock.contains("arachne:")) {
                    continue;
                }
                validatedBlocks++;
                int lineNumber = lineNumberAt(content, matcher.start(1));
                assertThatCode(() -> parseYaml(yamlBlock))
                        .as("YAML block in %s at line %s should parse", markdownFile, lineNumber)
                        .doesNotThrowAnyException();
            }
        }

        assertThat(validatedBlocks)
                .as("expected to validate at least one documented Arachne YAML block")
                .isGreaterThan(0);
    }

    private static void parseYaml(String yamlBlock) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        byte[] yamlBytes = Objects.requireNonNull(yamlBlock.getBytes(StandardCharsets.UTF_8));
        factory.setResources(new ByteArrayResource(yamlBytes));
        assertThat(factory.getObject()).isNotNull();
    }

    private static int lineNumberAt(String content, int index) {
        int lineNumber = 1;
        for (int cursor = 0; cursor < index; cursor++) {
            if (content.charAt(cursor) == '\n') {
                lineNumber++;
            }
        }
        return lineNumber;
    }
}
