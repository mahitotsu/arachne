package io.arachne.strands.eventloop;

import io.arachne.strands.hooks.NoOpHookRegistry;
import io.arachne.strands.model.Model;
import io.arachne.strands.model.ModelEvent;
import io.arachne.strands.model.ToolSpec;
import io.arachne.strands.tool.Tool;
import io.arachne.strands.tool.ToolResult;
import io.arachne.strands.types.ContentBlock;
import io.arachne.strands.types.Message;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link EventLoop}.
 * Uses a simple stub {@link Model} — no real Bedrock connection.
 */
class EventLoopTest {

    private final EventLoop loop = new EventLoop(new NoOpHookRegistry());

    // ── helper: model that returns a fixed text response ────────────────────

    private static Model textModel(String text) {
        return (messages, tools) -> List.of(
                new ModelEvent.TextDelta(text),
                new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(10, 5)));
    }

    // ── helper: model that first requests a tool, then returns text ─────────

    private static Model toolThenTextModel(
            String toolUseId, String toolName, Object toolInput,
            String finalText) {
        // First call → tool_use; second call → text
        int[] callCount = {0};
        return (messages, tools) -> {
            callCount[0]++;
            if (callCount[0] == 1) {
                return List.of(
                        new ModelEvent.ToolUse(toolUseId, toolName, toolInput),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(10, 5)));
            } else {
                return List.of(
                        new ModelEvent.TextDelta(finalText),
                        new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(15, 8)));
            }
        };
    }

    // ── tests ────────────────────────────────────────────────────────────────

    @Test
    void simpleTextResponse() {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.user("hello"));

        EventLoopResult result = loop.run(textModel("Hi there!"), messages, List.of(), 0);

        assertThat(result.text()).isEqualTo("Hi there!");
        assertThat(result.stopReason()).isEqualTo("end_turn");
        // assistant turn was appended to messages
        assertThat(messages).hasSize(2);
        assertThat(messages.get(1).role()).isEqualTo(Message.Role.ASSISTANT);
    }

    @Test
    void toolUseExecutedAndResultAppended() {
        // A tool that echoes its input
        Tool echoTool = new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec("echo", "Echoes the input", null);
            }

            @Override
            public ToolResult invoke(Object input) {
                return ToolResult.success("id-1", "Echo: " + input);
            }
        };

        List<Message> messages = new ArrayList<>();
        messages.add(Message.user("use the echo tool"));

        Model model = toolThenTextModel("id-1", "echo", "ping", "Done!");

        EventLoopResult result = loop.run(model, messages, List.of(echoTool), 0);

        assertThat(result.text()).isEqualTo("Done!");
        assertThat(result.stopReason()).isEqualTo("end_turn");

        // messages: user → assistant (tool_use) → user (tool_result) → assistant (text)
        assertThat(messages).hasSize(4);

        // The user message carrying the tool result
        Message toolResultMsg = messages.get(2);
        assertThat(toolResultMsg.role()).isEqualTo(Message.Role.USER);
        assertThat(toolResultMsg.content()).hasSize(1);
        assertThat(toolResultMsg.content().get(0)).isInstanceOf(ContentBlock.ToolResult.class);

        ContentBlock.ToolResult tr = (ContentBlock.ToolResult) toolResultMsg.content().get(0);
        assertThat(tr.toolUseId()).isEqualTo("id-1");
        assertThat(tr.content().toString()).contains("Echo: ping");
    }

    @Test
    void unknownToolReturnsError() {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.user("use unknown tool"));

        // Model requests a tool that is not registered
        Model model = toolThenTextModel("id-x", "nonexistent", null, "Fallback");

        EventLoopResult result = loop.run(model, messages, List.of(), 0);

        assertThat(result.text()).isEqualTo("Fallback");

        Message toolResultMsg = messages.get(2);
        ContentBlock.ToolResult tr = (ContentBlock.ToolResult) toolResultMsg.content().get(0);
        assertThat(tr.status()).isEqualTo("error");
    }

    @Test
    void maxCyclesThrowsException() {
        // Model always returns tool_use → infinite recursion guard
        Model infiniteToolModel = (messages, tools) -> List.of(
                new ModelEvent.ToolUse("id", "t", null),
                new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));

        List<Message> messages = new ArrayList<>();
        messages.add(Message.user("loop forever"));

        assertThatThrownBy(() -> loop.run(infiniteToolModel, messages, List.of(), 0))
                .isInstanceOf(EventLoopException.class)
                .hasMessageContaining("Max event-loop cycles exceeded");
    }
}
