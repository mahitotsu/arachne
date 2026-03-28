package io.arachne.strands.model.bedrock;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.arachne.strands.agent.Agent;
import io.arachne.strands.agent.AgentResult;
import io.arachne.strands.agent.AgentStreamEvent;
import io.arachne.strands.agent.DefaultAgent;
import io.arachne.strands.eventloop.EventLoop;
import io.arachne.strands.hooks.NoOpHookRegistry;
import io.arachne.strands.types.Message;

/**
 * Integration test for {@link BedrockModel} against the real AWS Bedrock service.
 *
 * <p><b>Opt-in only</b> — requires valid AWS credentials and Bedrock model access
 * in the configured region. Enable by setting:
 * <ul>
 *   <li>System property {@code -Darachne.integration.bedrock=true}</li>
 *   <li>Optional system property {@code -Darachne.integration.bedrock.region=...}</li>
 *   <li>Optional system property {@code -Darachne.integration.bedrock.model-id=...}</li>
 * </ul>
 *
 * <p>Run with:
 * {@code mvn -Dtest=BedrockModelIntegrationTest -Darachne.integration.bedrock=true test}
 */
@Tag("integration")
@EnabledIfSystemProperty(named = "arachne.integration.bedrock", matches = "true")
class BedrockModelIntegrationTest {

    /**
     * Smoke test: agent.run("hello") should return a non-empty response.
     * No tools are configured — tests the simple end_turn path.
     */
    @Test
    void helloWorldReturnsSomeText() {
        Agent agent = createAgent();

        AgentResult result = agent.run("Say hello in one sentence.");

        assertThat(result.text()).isNotBlank();
        assertThat(result.stopReason()).isEqualTo("end_turn");

        // Conversation history: 1 user + 1 assistant
        assertThat(result.messages()).hasSize(2);
        assertThat(result.messages().get(0).role()).isEqualTo(Message.Role.USER);
        assertThat(result.messages().get(1).role()).isEqualTo(Message.Role.ASSISTANT);
    }

    /**
     * Smoke test: agent.stream("hello") should emit text deltas and a terminal complete event.
     * No tools are configured — tests the simple streaming end_turn path.
     */
    @Test
    void helloWorldStreamingEmitsTextAndCompleteEvent() {
        Agent agent = createAgent();
        List<String> events = new CopyOnWriteArrayList<>();

        AgentResult result = agent.stream("Say hello in one sentence.", event -> {
            switch (event) {
                case AgentStreamEvent.TextDelta textDelta -> events.add("text:" + textDelta.delta());
                case AgentStreamEvent.ToolUseRequested ignored -> events.add("toolUse");
                case AgentStreamEvent.ToolResultObserved ignored -> events.add("toolResult");
                case AgentStreamEvent.Retry retry -> events.add("retry:" + retry.guidance());
                case AgentStreamEvent.Complete complete -> events.add("complete:" + complete.result().stopReason());
            }
        });

        assertThat(result.text()).isNotBlank();
        assertThat(result.stopReason()).isEqualTo("end_turn");
        assertThat(events).anyMatch(event -> event.startsWith("text:"));
        assertThat(events.getLast()).isEqualTo("complete:end_turn");
        assertThat(events).noneMatch(event -> event.equals("toolUse") || event.equals("toolResult"));
        assertThat(result.messages()).hasSize(2);
        assertThat(result.messages().get(0).role()).isEqualTo(Message.Role.USER);
        assertThat(result.messages().get(1).role()).isEqualTo(Message.Role.ASSISTANT);
    }

    private Agent createAgent() {
        String region = System.getProperty("arachne.integration.bedrock.region", BedrockModel.DEFAULT_REGION);
        String modelId = System.getProperty(
                "arachne.integration.bedrock.model-id",
                BedrockModel.DEFAULT_MODEL_ID);

        BedrockModel model = new BedrockModel(modelId, region);
        NoOpHookRegistry hooks = new NoOpHookRegistry();
        EventLoop eventLoop = new EventLoop(hooks);
        return new DefaultAgent(model, List.of(), eventLoop, hooks);
    }
}
