package io.arachne.strands.model.bedrock;

import io.arachne.strands.agent.Agent;
import io.arachne.strands.agent.AgentResult;
import io.arachne.strands.agent.DefaultAgent;
import io.arachne.strands.eventloop.EventLoop;
import io.arachne.strands.hooks.NoOpHookRegistry;
import io.arachne.strands.types.Message;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link BedrockModel} against the real AWS Bedrock service.
 *
 * <p><b>Disabled by default</b> — requires valid AWS credentials and Bedrock model access
 * in the configured region. Enable by removing {@code @Disabled} and setting:
 * <ul>
 *   <li>Environment variable {@code AWS_REGION} (or configure your default profile)</li>
 *   <li>Model access enabled for {@value BedrockModel#DEFAULT_MODEL_ID} in the target region</li>
 * </ul>
 *
 * <p>Run with: {@code mvn test -Dgroups=integration}
 */
@Disabled("Requires real AWS credentials and Bedrock model access")
@Tag("integration")
class BedrockModelIntegrationTest {

    /**
     * Smoke test: agent.run("hello") should return a non-empty response.
     * No tools are configured — tests the simple end_turn path.
     */
    @Test
    void helloWorldReturnsSomeText() {
        BedrockModel model = new BedrockModel();
        NoOpHookRegistry hooks = new NoOpHookRegistry();
        EventLoop eventLoop = new EventLoop(hooks);
        Agent agent = new DefaultAgent(model, List.of(), eventLoop, hooks);

        AgentResult result = agent.run("Say hello in one sentence.");

        assertThat(result.text()).isNotBlank();
        assertThat(result.stopReason()).isEqualTo("end_turn");

        // Conversation history: 1 user + 1 assistant
        assertThat(result.messages()).hasSize(2);
        assertThat(result.messages().get(0).role()).isEqualTo(Message.Role.USER);
        assertThat(result.messages().get(1).role()).isEqualTo(Message.Role.ASSISTANT);
    }
}
