package io.arachne.samples.sessionredis;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class SessionRedisSampleTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2-alpine")
            .withExposedPorts(6379);

    @Test
    void sampleRestoresMessagesAndStateAcrossApplicationRestarts(CapturedOutput output) {
        runSample();
        runSample();

        assertThat(output)
                .contains("Arachne Redis session sample")
                .contains("sessionId> session-redis-test")
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
        try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(SessionRedisApplication.class)
                .web(WebApplicationType.NONE)
                                .run(
                                                "--spring.data.redis.host=" + REDIS.getHost(),
                                                "--spring.data.redis.port=" + REDIS.getMappedPort(6379),
                                                "--arachne.strands.agent.session.id=session-redis-test",
                                                "--prompt=Remember that my destination is Kyoto.")) {
        }
    }
}