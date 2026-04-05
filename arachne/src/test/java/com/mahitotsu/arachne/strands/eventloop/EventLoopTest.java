package com.mahitotsu.arachne.strands.eventloop;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

import com.mahitotsu.arachne.strands.agent.AgentState;
import com.mahitotsu.arachne.strands.hooks.DispatchingHookRegistry;
import com.mahitotsu.arachne.strands.hooks.HookProvider;
import com.mahitotsu.arachne.strands.hooks.NoOpHookRegistry;
import com.mahitotsu.arachne.strands.model.Model;
import com.mahitotsu.arachne.strands.model.ModelEvent;
import com.mahitotsu.arachne.strands.model.ToolSelection;
import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.tool.StructuredOutputTool;
import com.mahitotsu.arachne.strands.tool.Tool;
import com.mahitotsu.arachne.strands.tool.ToolResult;
import com.mahitotsu.arachne.strands.types.ContentBlock;
import com.mahitotsu.arachne.strands.types.Message;

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

        EventLoopResult result = loop.run(textModel("Hi there!"), messages, List.of(), new AgentState(), 0);

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

        EventLoopResult result = loop.run(model, messages, List.of(echoTool), new AgentState(), 0);

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

        EventLoopResult result = loop.run(model, messages, List.of(), new AgentState(), 0);

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

        assertThatThrownBy(() -> loop.run(infiniteToolModel, messages, List.of(), new AgentState(), 0))
                .isInstanceOf(EventLoopException.class)
                .hasMessageContaining("Max event-loop cycles exceeded");
    }

    @Test
    void structuredOutputRunForcesFinalToolBeforeToolLoopHitsMaxCycles() {
        StructuredOutputTool<WeatherSummary> structuredOutputTool = new StructuredOutputTool<>(WeatherSummary.class);
        StructuredOutputContext<WeatherSummary> structuredOutputContext = new StructuredOutputContext<>(structuredOutputTool);
        Tool noopTool = new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec("lookup", "lookup", null);
            }

            @Override
            public ToolResult invoke(Object input) {
                return ToolResult.success("lookup-id", "ok");
            }
        };
        List<Message> messages = new ArrayList<>();
        messages.add(Message.user("Return a structured summary"));
        int[] forcedCalls = {0};
        Model model = new Model() {
            @Override
            public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools) {
                throw new AssertionError("Expected the tool-selection-aware overload");
            }

            @Override
            public Iterable<ModelEvent> converse(
                    List<Message> messages,
                    List<ToolSpec> tools,
                    String systemPrompt,
                    ToolSelection toolSelection) {
                if (toolSelection != null && structuredOutputTool.toolName().equals(toolSelection.toolName())) {
                    forcedCalls[0]++;
                    return List.of(
                            new ModelEvent.ToolUse(
                                    "structured-1",
                                    structuredOutputTool.toolName(),
                                    java.util.Map.of("answer", "Tokyo", "confidence", 0.9)),
                            new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
                }
                return List.of(
                        new ModelEvent.ToolUse("lookup-1", "lookup", java.util.Map.of("query", "policy")),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }
        };

        EventLoopResult result = loop.run(
                model,
                messages,
                List.of(noopTool, structuredOutputTool),
                null,
                structuredOutputContext,
                new AgentState(),
                0);

        assertThat(result.stopReason()).isEqualTo("tool_use");
        assertThat(forcedCalls[0]).isEqualTo(1);
        assertThat(structuredOutputContext.requireValue().answer()).isEqualTo("Tokyo");
        assertThat(messages).anyMatch(message -> message.role() == Message.Role.USER
                && message.content().stream()
                        .filter(ContentBlock.Text.class::isInstance)
                        .map(ContentBlock.Text.class::cast)
                        .map(ContentBlock.Text::text)
                .anyMatch(text -> text.contains("structured_output tool")));
    }

    @Test
    void modelHooksCanRewriteSystemPromptAndAssistantMessage() {
        AgentState state = new AgentState();
        List<String> events = new ArrayList<>();
        HookProvider hookProvider = registrar -> registrar
                .beforeModelCall(event -> {
                    events.add("beforeModel:" + event.systemPrompt());
                    event.setSystemPrompt("rewritten-system");
                })
                .afterModelCall(event -> {
                    events.add("afterModel:" + event.stopReason());
                    event.setResponse(Message.assistant("Hooked reply"));
                })
                .messageAdded(event -> events.add("messageAdded:" + event.message().role()));

        EventLoop hookedLoop = new EventLoop(DispatchingHookRegistry.fromProviders(List.of(hookProvider)));
        List<Message> messages = new ArrayList<>();
        messages.add(Message.user("hello"));
        RecordingSystemPromptModel model = new RecordingSystemPromptModel();

        EventLoopResult result = hookedLoop.run(model, messages, List.of(), "original-system", state, 0);

        assertThat(result.text()).isEqualTo("Hooked reply");
        assertThat(model.systemPrompt()).isEqualTo("rewritten-system");
        assertThat(messages.getLast()).isEqualTo(Message.assistant("Hooked reply"));
        assertThat(events).containsExactly("beforeModel:original-system", "afterModel:end_turn", "messageAdded:ASSISTANT");
    }

    private static final class RecordingSystemPromptModel implements Model {

        private String systemPrompt;

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools) {
            throw new AssertionError("Expected system-prompt-aware overload");
        }

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools, String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return List.of(
                    new ModelEvent.TextDelta("Original reply"),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(3, 2)));
        }

        String systemPrompt() {
            return systemPrompt;
        }
    }

    private record WeatherSummary(String answer, double confidence) {
    }

    @Test
    void beforeToolCallCanGuideWithoutRunningTheTool() {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.user("pick the best path"));
        List<String> invocations = new ArrayList<>();
        Tool tool = new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec("planner", "planner", null);
            }

            @Override
            public ToolResult invoke(Object input) {
                invocations.add("planner");
                return ToolResult.success("tool-1", "executed");
            }
        };
        HookProvider hookProvider = registrar -> registrar.beforeToolCall(event -> event.guide("Use the existing itinerary instead."));
        EventLoop guidedLoop = new EventLoop(DispatchingHookRegistry.fromProviders(List.of(hookProvider)));
        Model model = toolThenTextModel("tool-1", "planner", java.util.Map.of("city", "Tokyo"), "Handled guidance");

        EventLoopResult result = guidedLoop.run(model, messages, List.of(tool), new AgentState(), 0);

        assertThat(result.text()).isEqualTo("Handled guidance");
        assertThat(invocations).isEmpty();
        Message toolResultMessage = messages.get(2);
        ContentBlock.ToolResult toolResult = (ContentBlock.ToolResult) toolResultMessage.content().getFirst();
        assertThat(toolResult.status()).isEqualTo("error");
        assertThat(toolResult.content()).isEqualTo("Use the existing itinerary instead.");
    }

    @Test
    void afterModelCallCanRetryWithGuidance() {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.user("answer carefully"));
        int[] calls = {0};
        Model model = (conversation, tools) -> {
            calls[0]++;
            if (calls[0] == 1) {
                return List.of(
                        new ModelEvent.TextDelta("Draft"),
                        new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
            }
            return List.of(
                    new ModelEvent.TextDelta("Final"),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        };
        HookProvider hookProvider = registrar -> registrar.afterModelCall(new java.util.function.Consumer<>() {
            private boolean guided;

            @Override
            public void accept(com.mahitotsu.arachne.strands.hooks.AfterModelCallEvent event) {
                if (!guided) {
                    guided = true;
                    event.retryWithGuidance("Be specific.");
                }
            }
        });
        EventLoop guidedLoop = new EventLoop(DispatchingHookRegistry.fromProviders(List.of(hookProvider)));

        EventLoopResult result = guidedLoop.run(model, messages, List.of(), new AgentState(), 0);

        assertThat(result.text()).isEqualTo("Final");
        assertThat(messages).hasSize(3);
        assertThat(messages.get(1)).isEqualTo(Message.user("Be specific."));
        assertThat(messages.get(2)).isEqualTo(Message.assistant("Final"));
    }
}
