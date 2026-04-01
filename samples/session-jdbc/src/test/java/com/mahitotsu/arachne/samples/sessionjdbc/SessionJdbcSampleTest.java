package com.mahitotsu.arachne.samples.sessionjdbc;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;

@ExtendWith(OutputCaptureExtension.class)
class SessionJdbcSampleTest {

    @TempDir
    Path tempDir;

    @Test
    void sampleRestoresMessagesAndStateAcrossApplicationRestarts(CapturedOutput output) {
        runSample();
        runSample();

        assertThat(output)
                .contains("Arachne JDBC session sample")
                .contains("sessionId> session-jdbc-test")
                .contains("restored.messages.before> 0")
                .contains("restored.runCount.before> 0")
                .contains("reply> Turn 1 stored for prompt: Remember that my destination is Kyoto.")
                .contains("persisted.messages.after> 2")
                .contains("persisted.runCount.after> 1")
                .contains("restored.messages.before> 2")
                .contains("restored.runCount.before> 1")
                .contains("reply> Turn 2 stored for prompt: Remember that my destination is Kyoto.")
                .contains("persisted.messages.after> 4")
                .contains("persisted.runCount.after> 2");
    }

    private void runSample() {
        try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(SessionJdbcApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.datasource.url=" + jdbcUrl(),
                        "--arachne.strands.agent.session.id=session-jdbc-test",
                        "--prompt=Remember that my destination is Kyoto.")) {
        }
    }

    private String jdbcUrl() {
        return "jdbc:h2:file:" + tempDir.resolve("session-demo").toAbsolutePath() + ";AUTO_SERVER=TRUE";
    }
}