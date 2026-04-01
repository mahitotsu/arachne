package com.mahitotsu.arachne.docs;

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
    void repositoryDocsExplainMarketplaceProductTrackBoundary() throws IOException {
        String readme = Files.readString(REPOSITORY_ROOT.resolve("README.md"), StandardCharsets.UTF_8);
        String docsGuide = Files.readString(REPOSITORY_ROOT.resolve("docs/README.md"), StandardCharsets.UTF_8);
        String repositoryFacts = Files.readString(REPOSITORY_ROOT.resolve("docs/repository-facts.md"), StandardCharsets.UTF_8);
        String samplesCatalog = Files.readString(REPOSITORY_ROOT.resolve("samples/README.md"), StandardCharsets.UTF_8);
        String marketplaceReadme = Files.readString(
                REPOSITORY_ROOT.resolve("marketplace-agent-platform/README.md"),
                StandardCharsets.UTF_8);
        String rootPom = Files.readString(REPOSITORY_ROOT.resolve("pom.xml"), StandardCharsets.UTF_8);
        String samplesPom = Files.readString(REPOSITORY_ROOT.resolve("samples/pom.xml"), StandardCharsets.UTF_8);

        assertThat(readme)
                .contains("`marketplace-agent-platform/`: independent multi-module product track");

        assertThat(docsGuide)
                .contains("the independent `marketplace-agent-platform/` product track lives at the repository root");

        assertThat(repositoryFacts)
                .contains("marketplace-agent-platform/pom.xml")
                .contains("marketplace-agent-platform")
                .contains("root-level product track");

        assertThat(samplesCatalog)
                .contains("`marketplace-agent-platform` has moved out of `samples/`")
                .contains("repository root as its own multi-module product track")
                .contains("no longer part of the samples catalog");

        assertThat(marketplaceReadme)
                .contains("source of truth for what is implemented today")
                .contains("Treat the other `docs/*.md` files as concept, requirements, architecture, API, contract, and skill-boundary references")
                .contains("opt-in Arachne-native workflow path in `workflow-service`")
                .contains("finance-control pause/resume runs through Arachne-native interrupt handling");

        assertThat(rootPom)
                .contains("<module>marketplace-agent-platform</module>");

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
                                .contains("sample reactor resolves `com.mahitotsu.arachne:arachne` from the local Maven repository");

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