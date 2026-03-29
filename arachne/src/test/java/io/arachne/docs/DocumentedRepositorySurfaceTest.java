package io.arachne.docs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class DocumentedRepositorySurfaceTest {

    private static final Path REPOSITORY_ROOT = Path.of("..");

        @Test
        void docsIdentifyProjectStatusAsCanonicalAvailabilitySource() throws IOException {
                String readme = Files.readString(REPOSITORY_ROOT.resolve("README.md"), StandardCharsets.UTF_8);
                String docsGuide = Files.readString(REPOSITORY_ROOT.resolve("docs/README.md"), StandardCharsets.UTF_8);
                String userGuide = Files.readString(REPOSITORY_ROOT.resolve("docs/user-guide.md"), StandardCharsets.UTF_8);

                assertThat(readme)
                                .contains("Treat [docs/project-status.md](docs/project-status.md) as the canonical source of truth for feature availability");

                assertThat(docsGuide)
                                .contains("`project-status.md`: canonical source of truth for shipped features, sample map, and current constraints")
                                .contains("`project-status.md` is the canonical source of truth for feature availability and current constraints");

                assertThat(userGuide)
                                .contains("Treat [docs/project-status.md](project-status.md) as the canonical source of truth for feature availability and current constraints.");
        }

    @Test
    void rootReadmeListsCurrentBuiltInToolSet() throws IOException {
                String readme = Files.readString(REPOSITORY_ROOT.resolve("README.md"), StandardCharsets.UTF_8);

        assertThat(readme)
                .contains("built-in tools: `calculator`, `current_time`, `resource_reader`, `resource_list`");
    }

    @Test
    void sampleCatalogExplicitlyExcludesConceptOnlyMarketplaceDirectory() throws IOException {
        String samplesCatalog = Files.readString(REPOSITORY_ROOT.resolve("samples/README.md"), StandardCharsets.UTF_8);
        String marketplaceReadme = Files.readString(
                REPOSITORY_ROOT.resolve("samples/marketplace-agent-platform/README.md"),
                StandardCharsets.UTF_8);
        String samplesPom = Files.readString(REPOSITORY_ROOT.resolve("samples/pom.xml"), StandardCharsets.UTF_8);

        assertThat(samplesCatalog)
                .contains("The concept-only `marketplace-agent-platform` directory is design material for a future sample.")
                .contains("not yet part of the runnable sample catalog")
                .contains("not yet included in the Maven sample reactor")
                .contains("`tested in sample reactor`")
                .contains("`compile-checked in sample reactor`")
                .contains("`concept-only`");

        assertThat(marketplaceReadme)
                .contains("intentionally concept-only")
                .contains("not yet part of the runnable sample catalog")
                .contains("not yet included in the Maven sample reactor");

        assertThat(samplesPom)
                .doesNotContain("<module>marketplace-agent-platform</module>");
    }

        @Test
        void readinessDocsExplainWhenToRefreshBedrockLiveEvidence() throws IOException {
                String readiness = Files.readString(REPOSITORY_ROOT.resolve("docs/closeout-and-readiness.md"), StandardCharsets.UTF_8);
                String repositoryFacts = Files.readString(REPOSITORY_ROOT.resolve("docs/repository-facts.md"), StandardCharsets.UTF_8);

                assertThat(readiness)
                                .contains("When a bounded task changes Bedrock-specific runtime behavior")
                                .contains("DomainSeparationBedrockIntegrationTest");

                assertThat(repositoryFacts)
                                .contains("DomainSeparationBedrockIntegrationTest")
                                .contains("rerun both commands or record the missing live evidence explicitly");
        }

        @Test
        void docsExplainHowToRefreshSampleReactorSnapshot() throws IOException {
                String readiness = Files.readString(REPOSITORY_ROOT.resolve("docs/closeout-and-readiness.md"), StandardCharsets.UTF_8);
                String repositoryFacts = Files.readString(REPOSITORY_ROOT.resolve("docs/repository-facts.md"), StandardCharsets.UTF_8);
                String samplesCatalog = Files.readString(REPOSITORY_ROOT.resolve("samples/README.md"), StandardCharsets.UTF_8);

                assertThat(readiness)
                                .contains("Sample Reactor Re-Entry Rule")
                                .contains("mvn -pl arachne -am install -DskipTests")
                                .contains("sample reactor resolves `io.arachne:arachne` from the local Maven repository");

                assertThat(repositoryFacts)
                                .contains("For sample-side verification, refresh the library snapshot before using the samples reactor")
                                .contains("mvn -pl arachne -am install -DskipTests")
                                .contains("samples/pom.xml")
                                .contains("local Maven repository");

                assertThat(samplesCatalog)
                                .contains("For sample-reactor verification or readiness checks")
                                .contains("mvn -pl arachne -am install -DskipTests")
                                .contains("stale local snapshot");
        }
}