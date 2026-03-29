package io.arachne.docs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class DocumentedRepositorySurfaceTest {

    @Test
    void rootReadmeListsCurrentBuiltInToolSet() throws IOException {
        String readme = Files.readString(Path.of("README.md"), StandardCharsets.UTF_8);

        assertThat(readme)
                .contains("built-in tools: `calculator`, `current_time`, `resource_reader`, `resource_list`");
    }

    @Test
    void sampleCatalogExplicitlyExcludesConceptOnlyMarketplaceDirectory() throws IOException {
        String samplesCatalog = Files.readString(Path.of("samples/README.md"), StandardCharsets.UTF_8);
        String marketplaceReadme = Files.readString(
                Path.of("samples/marketplace-agent-platform/README.md"),
                StandardCharsets.UTF_8);
        String samplesPom = Files.readString(Path.of("samples/pom.xml"), StandardCharsets.UTF_8);

        assertThat(samplesCatalog)
                .contains("The concept-only `marketplace-agent-platform` directory is design material for a future sample.")
                .contains("not yet part of the runnable sample catalog")
                .contains("not yet included in the Maven sample reactor");

        assertThat(marketplaceReadme)
                .contains("intentionally concept-only")
                .contains("not yet part of the runnable sample catalog")
                .contains("not yet included in the Maven sample reactor");

        assertThat(samplesPom)
                .doesNotContain("<module>marketplace-agent-platform</module>");
    }
}