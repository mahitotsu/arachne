package io.arachne.strands.agent;

import io.arachne.strands.eventloop.EventLoop;
import io.arachne.strands.hooks.NoOpHookRegistry;
import io.arachne.strands.model.Model;
import io.arachne.strands.model.ModelEvent;
import io.arachne.strands.types.Message;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DefaultAgent}.
 */
class DefaultAgentTest {

    private static Model stubModel(String reply) {
        return (messages, tools) -> List.of(
                new ModelEvent.TextDelta(reply),
                new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(10, 5)));
    }

    @Test
    void runReturnsAssistantText() {
        NoOpHookRegistry hooks = new NoOpHookRegistry();
        EventLoop eventLoop = new EventLoop(hooks);
        DefaultAgent agent = new DefaultAgent(stubModel("Hello!"), List.of(), eventLoop, hooks);

        AgentResult result = agent.run("Hi");

        assertThat(result.text()).isEqualTo("Hello!");
        assertThat(result.stopReason()).isEqualTo("end_turn");
    }

    @Test
    void multiTurnAccumulatesMessages() {
        NoOpHookRegistry hooks = new NoOpHookRegistry();
        EventLoop eventLoop = new EventLoop(hooks);
        DefaultAgent agent = new DefaultAgent(stubModel("Pong"), List.of(), eventLoop, hooks);

        agent.run("Ping 1");
        agent.run("Ping 2");

        // 2 user + 2 assistant messages
        assertThat(agent.getMessages()).hasSize(4);
    }
}
