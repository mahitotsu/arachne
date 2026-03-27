package io.arachne.strands.agent;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

import io.arachne.strands.agent.conversation.NoOpConversationManager;
import io.arachne.strands.agent.conversation.SlidingWindowConversationManager;
import io.arachne.strands.agent.conversation.SummarizingConversationManager;
import io.arachne.strands.eventloop.EventLoop;
import io.arachne.strands.hooks.DispatchingHookRegistry;
import io.arachne.strands.hooks.HookProvider;
import io.arachne.strands.hooks.NoOpHookRegistry;
import io.arachne.strands.model.Model;
import io.arachne.strands.model.ModelEvent;
import io.arachne.strands.model.StreamingModel;
import io.arachne.strands.model.ToolSelection;
import io.arachne.strands.model.ToolSpec;
import io.arachne.strands.session.InMemorySessionManager;
import io.arachne.strands.steering.Guide;
import io.arachne.strands.steering.ModelSteeringAction;
import io.arachne.strands.steering.Proceed;
import io.arachne.strands.steering.SteeringHandler;
import io.arachne.strands.tool.StructuredOutputException;
import io.arachne.strands.tool.Tool;
import io.arachne.strands.tool.ToolResult;
import io.arachne.strands.types.ContentBlock;
import io.arachne.strands.types.Message;
import jakarta.validation.constraints.NotBlank;

/**
 * Unit tests for {@link DefaultAgent}.
 */
class DefaultAgentTest {

    private static final class RecordingModel implements Model {

        private String systemPrompt;

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools) {
            throw new AssertionError("DefaultAgent should call the system-prompt-aware overload");
        }

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools, String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return List.of(
                    new ModelEvent.TextDelta("Hello!"),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(10, 5)));
        }
    }

    private static Model stubModel(String reply) {
        return (messages, tools) -> List.of(
                new ModelEvent.TextDelta(reply),
                new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(10, 5)));
    }

    private static Model structuredOutputModel() {
        return new Model() {
            private int calls;

            @Override
            public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools) {
                throw new AssertionError("Structured output should use the extended overloads");
            }

            @Override
            public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools, String systemPrompt) {
                calls++;
                return List.of(
                        new ModelEvent.TextDelta("Plain text draft"),
                        new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(10, 5)));
            }

            @Override
            public Iterable<ModelEvent> converse(
                    List<Message> messages,
                    List<ToolSpec> tools,
                    String systemPrompt,
                    ToolSelection toolSelection) {
                if (toolSelection == null) {
                    return converse(messages, tools, systemPrompt);
                }
                if (calls == 1) {
                    calls++;
                    return List.of(
                            new ModelEvent.ToolUse(
                                    "structured-1",
                                    toolSelection.toolName(),
                                    java.util.Map.of("answer", "Tokyo", "confidence", 0.9)),
                            new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(12, 4)));
                }
                return List.of(
                        new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(13, 2)));
            }
        };
    }

    private static Model invalidStructuredOutputModel() {
        return new Model() {
            private int calls;

            @Override
            public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools) {
                throw new AssertionError("Structured output should use the extended overloads");
            }

            @Override
            public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools, String systemPrompt) {
                calls++;
                return List.of(
                        new ModelEvent.TextDelta("Draft"),
                        new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(10, 5)));
            }

            @Override
            public Iterable<ModelEvent> converse(
                    List<Message> messages,
                    List<ToolSpec> tools,
                    String systemPrompt,
                    ToolSelection toolSelection) {
                if (toolSelection == null) {
                    return converse(messages, tools, systemPrompt);
                }
                if (calls == 1) {
                    calls++;
                    return List.of(
                            new ModelEvent.ToolUse(
                                    "structured-1",
                                    toolSelection.toolName(),
                                    java.util.Map.of("answer", "  ", "confidence", 0.9)),
                            new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(12, 4)));
                }
                return List.of(new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(13, 2)));
            }
        };
    }

    @Test
    void runReturnsAssistantText() {
        NoOpHookRegistry hooks = new NoOpHookRegistry();
        EventLoop eventLoop = new EventLoop(hooks);
        DefaultAgent agent = new DefaultAgent(stubModel("Hello!"), List.of(), eventLoop, hooks);

        AgentResult result = agent.run("Hi");

        assertThat(result.text()).isEqualTo("Hello!");
        assertThat(result.stopReason()).isEqualTo("end_turn");
        assertThat(result.metrics().usage()).isEqualTo(new ModelEvent.Usage(10, 5, 0, 0));
    }

    @Test
    void runDispatchesInvocationHooksAndMessageAddedEvents() {
        List<String> events = new java.util.ArrayList<>();
        HookProvider hookProvider = registrar -> registrar
                .beforeInvocation(event -> {
                    events.add("beforeInvocation:" + event.prompt());
                    event.setPrompt("rewritten");
                })
                .afterInvocation(event -> {
                    events.add("afterInvocation:" + event.text());
                    event.setText(event.text() + "!");
                })
                .messageAdded(event -> events.add("messageAdded:" + event.message().role()));
        DispatchingHookRegistry hooks = DispatchingHookRegistry.fromProviders(List.of(hookProvider));
        EventLoop eventLoop = new EventLoop(hooks);
        DefaultAgent agent = new DefaultAgent(stubModel("model reply"), List.of(), eventLoop, hooks);

        AgentResult result = agent.run("hello");

        assertThat(result.text()).isEqualTo("model reply!");
        assertThat(agent.getMessages().getFirst()).isEqualTo(Message.user("rewritten"));
        assertThat(events).containsExactly(
                "beforeInvocation:hello",
                "messageAdded:USER",
                "messageAdded:ASSISTANT",
                "afterInvocation:model reply");
    }

    @Test
    void runReturnsInterruptsAndResumeContinuesWithToolResults() {
        HookProvider interruptingHook = registrar -> registrar.beforeToolCall(event -> event.interrupt("approval", "need approval"));
        DispatchingHookRegistry hooks = DispatchingHookRegistry.fromProviders(List.of(interruptingHook));
        EventLoop eventLoop = new EventLoop(hooks);
        DefaultAgent agent = new DefaultAgent(toolUseThenResumeModel(), List.of(stubTool()), eventLoop, hooks);

        AgentResult interrupted = agent.run("book the trip");

        assertThat(interrupted.interrupted()).isTrue();
        assertThat(interrupted.stopReason()).isEqualTo("interrupt");
        assertThat(interrupted.interrupts()).singleElement().satisfies(interrupt -> {
            assertThat(interrupt.name()).isEqualTo("approval");
            assertThat(interrupt.reason()).isEqualTo("need approval");
            assertThat(interrupt.toolUseId()).isEqualTo("tool-1");
        });
        assertThat(agent.getMessages()).hasSize(2);
        assertThat(agent.getMessages().getLast().content()).anyMatch(ContentBlock.ToolUse.class::isInstance);

        AgentResult resumed = interrupted.resume(new InterruptResponse("tool-1", "approved"));

        assertThat(resumed.interrupted()).isFalse();
        assertThat(resumed.text()).isEqualTo("Approval result: approved");
        assertThat(agent.getMessages()).hasSize(4);
        Message resumedToolResult = agent.getMessages().get(2);
        assertThat(resumedToolResult.role()).isEqualTo(Message.Role.USER);
        ContentBlock.ToolResult toolResult = (ContentBlock.ToolResult) resumedToolResult.content().getFirst();
        assertThat(toolResult.toolUseId()).isEqualTo("tool-1");
        assertThat(toolResult.content()).isEqualTo("approved");
    }

    @Test
    void resumeRejectsMissingInterruptResponse() {
        HookProvider interruptingHook = registrar -> registrar.beforeToolCall(event -> event.interrupt("need approval"));
        DispatchingHookRegistry hooks = DispatchingHookRegistry.fromProviders(List.of(interruptingHook));
        EventLoop eventLoop = new EventLoop(hooks);
        DefaultAgent agent = new DefaultAgent(toolUseThenResumeModel(), List.of(stubTool()), eventLoop, hooks);

        AgentResult interrupted = agent.run("book the trip");

        assertThatThrownBy(() -> interrupted.resume(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing interrupt response");
    }

    @Test
    void resumeDoesNotRedispatchInvocationHooks() {
        List<String> events = new java.util.ArrayList<>();
        HookProvider hookProvider = registrar -> registrar
                .beforeInvocation(event -> events.add("before:" + event.prompt()))
                .afterInvocation(event -> events.add("after:" + event.stopReason()));
        HookProvider interruptingHook = registrar -> registrar.beforeToolCall(event -> event.interrupt("need approval"));
        DispatchingHookRegistry hooks = DispatchingHookRegistry.fromProviders(List.of(hookProvider, interruptingHook));
        EventLoop eventLoop = new EventLoop(hooks);
        DefaultAgent agent = new DefaultAgent(toolUseThenResumeModel(), List.of(stubTool()), eventLoop, hooks);

        AgentResult interrupted = agent.run("book the trip");
        AgentResult resumed = interrupted.resume(new InterruptResponse("tool-1", "approved"));

        assertThat(interrupted.stopReason()).isEqualTo("interrupt");
        assertThat(resumed.stopReason()).isEqualTo("end_turn");
        assertThat(events).containsExactly(
                "before:book the trip",
                "after:interrupt");
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

    @Test
    void runPassesSystemPromptToModel() {
        NoOpHookRegistry hooks = new NoOpHookRegistry();
        EventLoop eventLoop = new EventLoop(hooks);
        RecordingModel model = new RecordingModel();
        DefaultAgent agent = new DefaultAgent(model, List.of(), eventLoop, hooks, "You are terse.");

        AgentResult result = agent.run("Hi");

        assertThat(result.text()).isEqualTo("Hello!");
        assertThat(model.systemPrompt).isEqualTo("You are terse.");
    }

    @Test
    void runCanReturnStructuredOutput() {
        NoOpHookRegistry hooks = new NoOpHookRegistry();
        EventLoop eventLoop = new EventLoop(hooks);
        DefaultAgent agent = new DefaultAgent(structuredOutputModel(), List.of(), eventLoop, hooks);

        WeatherSummary result = agent.run("東京の天気を返してください", WeatherSummary.class);

        assertThat(result.answer()).isEqualTo("Tokyo");
        assertThat(result.confidence()).isEqualTo(0.9);
        assertThat(agent.getMessages()).hasSize(5);
    }

    @Test
    void runRejectsInvalidStructuredOutput() {
        NoOpHookRegistry hooks = new NoOpHookRegistry();
        EventLoop eventLoop = new EventLoop(hooks);
        DefaultAgent agent = new DefaultAgent(invalidStructuredOutputModel(), List.of(), eventLoop, hooks);

        assertThatThrownBy(() -> agent.run("東京の天気を返してください", ValidatedWeatherSummary.class))
                .isInstanceOf(StructuredOutputException.class)
                .hasMessageContaining("answer");
    }

    @Test
    void streamEmitsEventsInDeterministicOrder() {
        NoOpHookRegistry hooks = new NoOpHookRegistry();
        EventLoop eventLoop = new EventLoop(hooks);
        Tool tool = new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec("echo", "echo", null);
            }

            @Override
            public ToolResult invoke(Object input) {
                return ToolResult.success("tool-1", "tool:" + ((java.util.Map<?, ?>) input).get("value"));
            }
        };
        DefaultAgent agent = new DefaultAgent(streamingToolModel(), List.of(tool), eventLoop, hooks);
        List<String> events = new java.util.ArrayList<>();

        AgentResult result = agent.stream("hello", event -> {
            switch (event) {
                case AgentStreamEvent.TextDelta textDelta -> events.add("text:" + textDelta.delta());
                case AgentStreamEvent.ToolUseRequested toolUseRequested -> events.add("toolUse:" + toolUseRequested.toolName());
                case AgentStreamEvent.ToolResultObserved toolResultObserved ->
                        events.add("toolResult:" + toolResultObserved.result().status().name().toLowerCase());
                case AgentStreamEvent.Retry retry -> events.add("retry:" + retry.guidance());
                case AgentStreamEvent.Complete complete -> events.add("complete:" + complete.result().stopReason());
            }
        });

        assertThat(result.text()).isEqualTo("done");
        assertThat(events).containsExactly(
                "text:thinking",
                "toolUse:echo",
                "toolResult:success",
                "text:done",
                "complete:end_turn");
    }

    @Test
    void streamEmitsRetryEventWhenModelSteeringGuidesRetry() {
        DispatchingHookRegistry hooks = DispatchingHookRegistry.fromProviders(List.of(new SteeringHandler() {
            private int calls;

            @Override
            protected ModelSteeringAction steerAfterModel(io.arachne.strands.hooks.AfterModelCallEvent event) {
                calls++;
                if (calls == 1) {
                    return new Guide("Try again with a concrete answer.");
                }
                return new Proceed("accept");
            }
        }));
        EventLoop eventLoop = new EventLoop(hooks);
        DefaultAgent agent = new DefaultAgent(streamingRetryModel(), List.of(), eventLoop, hooks);
        List<String> events = new java.util.ArrayList<>();

        AgentResult result = agent.stream("hello", event -> {
            switch (event) {
                case AgentStreamEvent.TextDelta textDelta -> events.add("text:" + textDelta.delta());
                case AgentStreamEvent.ToolUseRequested toolUseRequested -> toolUseRequested.toolName();
                case AgentStreamEvent.ToolResultObserved toolResultObserved -> toolResultObserved.result();
                case AgentStreamEvent.Retry retry -> events.add("retry:" + retry.guidance());
                case AgentStreamEvent.Complete complete -> events.add("complete:" + complete.result().text());
            }
        });

        assertThat(result.text()).isEqualTo("final");
        assertThat(events).containsExactly(
                "text:draft",
                "retry:Try again with a concrete answer.",
                "text:final",
                "complete:final");
        assertThat(agent.getMessages()).hasSize(3);
        assertThat(agent.getMessages().get(1)).isEqualTo(Message.user("Try again with a concrete answer."));
        assertThat(agent.getMessages().get(2)).isEqualTo(Message.assistant("final"));
    }

    @Test
    void streamPropagatesStreamingModelFailure() {
        NoOpHookRegistry hooks = new NoOpHookRegistry();
        EventLoop eventLoop = new EventLoop(hooks);
        DefaultAgent agent = new DefaultAgent(failingStreamingModel(), List.of(), eventLoop, hooks);
        List<String> events = new java.util.ArrayList<>();

        assertThatThrownBy(() -> agent.stream("hello", event -> {
            if (event instanceof AgentStreamEvent.TextDelta textDelta) {
                events.add(textDelta.delta());
            }
        }))
                .isInstanceOf(io.arachne.strands.model.ModelException.class)
                .hasMessageContaining("boom");
        assertThat(events).containsExactly("partial");
    }

    @Test
    void streamFallsBackToNonStreamingModelEvents() {
        NoOpHookRegistry hooks = new NoOpHookRegistry();
        EventLoop eventLoop = new EventLoop(hooks);
        List<String> calls = new java.util.ArrayList<>();
        Model model = new Model() {
            @Override
            public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools) {
                throw new AssertionError("Expected tool-selection-aware overload");
            }

            @Override
            public Iterable<ModelEvent> converse(
                    List<Message> messages,
                    List<ToolSpec> tools,
                    String systemPrompt,
                    ToolSelection toolSelection) {
                calls.add(systemPrompt == null ? "" : systemPrompt);
                return List.of(
                        new ModelEvent.TextDelta("fallback"),
                        new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(2, 1)));
            }
        };
        DefaultAgent agent = new DefaultAgent(model, List.of(), eventLoop, hooks, "fallback-system");
        List<String> events = new java.util.ArrayList<>();

        AgentResult result = agent.stream("hello", event -> {
            switch (event) {
                case AgentStreamEvent.TextDelta textDelta -> events.add("text:" + textDelta.delta());
                case AgentStreamEvent.ToolUseRequested toolUseRequested -> events.add("toolUse:" + toolUseRequested.toolName());
                case AgentStreamEvent.ToolResultObserved toolResultObserved ->
                        events.add("toolResult:" + toolResultObserved.result().status().name().toLowerCase());
                case AgentStreamEvent.Retry retry -> events.add("retry:" + retry.guidance());
                case AgentStreamEvent.Complete complete -> events.add("complete:" + complete.result().text());
            }
        });

        assertThat(result.text()).isEqualTo("fallback");
        assertThat(result.metrics().usage()).isEqualTo(new ModelEvent.Usage(2, 1, 0, 0));
        assertThat(events).containsExactly("text:fallback", "complete:fallback");
        assertThat(calls).containsExactly("fallback-system");
    }

    @Test
    void runAggregatesUsageAcrossToolLoopAndExposesCacheMetrics() {
        NoOpHookRegistry hooks = new NoOpHookRegistry();
        EventLoop eventLoop = new EventLoop(hooks);
        DefaultAgent agent = new DefaultAgent(toolUseWithCacheMetricsModel(), List.of(stubTool()), eventLoop, hooks);

        AgentResult result = agent.run("book the trip");

        assertThat(result.text()).isEqualTo("Approval result: {city=Tokyo}");
        assertThat(result.metrics().usage()).isEqualTo(new ModelEvent.Usage(7, 4, 25, 14));
    }

            @Test
            void runAppliesConversationManagementAfterInvocation() {
            NoOpHookRegistry hooks = new NoOpHookRegistry();
            EventLoop eventLoop = new EventLoop(hooks);
            DefaultAgent agent = new DefaultAgent(
                stubModel("Pong"),
                List.of(),
                eventLoop,
                hooks,
                null,
                io.arachne.strands.tool.BeanValidationSupport.defaultValidator(),
                new SlidingWindowConversationManager(2),
                null,
                null,
                new AgentState());

            agent.run("Ping 1");
            agent.run("Ping 2");

            assertThat(agent.getMessages()).hasSize(2);
            assertThat(agent.getMessages().getFirst().content().getFirst()).isEqualTo(io.arachne.strands.types.ContentBlock.text("Ping 2"));
            }

            @Test
            void restoresMessagesAndStateFromSessionStorage() {
            NoOpHookRegistry hooks = new NoOpHookRegistry();
            EventLoop eventLoop = new EventLoop(hooks);
            InMemorySessionManager sessionManager = new InMemorySessionManager();
            DefaultAgent firstAgent = new DefaultAgent(
                stubModel("Pong"),
                List.of(),
                eventLoop,
                hooks,
                null,
                io.arachne.strands.tool.BeanValidationSupport.defaultValidator(),
                new NoOpConversationManager(),
                sessionManager,
                "demo-session",
                new AgentState());

            firstAgent.getState().put("city", "Tokyo");
            firstAgent.run("Ping 1");

            DefaultAgent restoredAgent = new DefaultAgent(
                stubModel("Pong"),
                List.of(),
                eventLoop,
                hooks,
                null,
                io.arachne.strands.tool.BeanValidationSupport.defaultValidator(),
                new NoOpConversationManager(),
                sessionManager,
                "demo-session",
                new AgentState());

            assertThat(restoredAgent.getMessages()).hasSize(2);
            assertThat(restoredAgent.getState().get("city")).isEqualTo("Tokyo");
            }

            @Test
            void restoresSummarizingConversationManagerStateFromSessionStorage() {
            NoOpHookRegistry hooks = new NoOpHookRegistry();
            EventLoop eventLoop = new EventLoop(hooks);
            InMemorySessionManager sessionManager = new InMemorySessionManager();
            SummarizingConversationManager manager = new SummarizingConversationManager(stubModel("summary"), 4, 2);
            DefaultAgent firstAgent = new DefaultAgent(
                stubModel("Pong"),
                List.of(),
                eventLoop,
                hooks,
                null,
                io.arachne.strands.tool.BeanValidationSupport.defaultValidator(),
                manager,
                sessionManager,
                "summary-session",
                new AgentState());

            firstAgent.run("Ping 1");
            firstAgent.run("Ping 2");
            firstAgent.run("Ping 3");

            SummarizingConversationManager restoredManager = new SummarizingConversationManager(stubModel("summary"), 4, 2);
            DefaultAgent restoredAgent = new DefaultAgent(
                stubModel("Pong"),
                List.of(),
                eventLoop,
                hooks,
                null,
                io.arachne.strands.tool.BeanValidationSupport.defaultValidator(),
                restoredManager,
                sessionManager,
                "summary-session",
                new AgentState());

            assertThat(restoredManager.getSummary()).isNotBlank();
            assertThat(restoredAgent.getMessages().getFirst().content().getFirst())
                .isEqualTo(io.arachne.strands.types.ContentBlock.text(
                    SummarizingConversationManager.SUMMARY_MESSAGE_PREFIX + "summary"));
            }

    record WeatherSummary(String answer, double confidence) {
    }

    record ValidatedWeatherSummary(@NotBlank String answer, double confidence) {
    }

    private static Model toolUseThenResumeModel() {
        return new Model() {
            private int calls;

            @Override
            public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools) {
                calls++;
                if (calls == 1) {
                    return List.of(
                            new ModelEvent.ToolUse("tool-1", "approvalTool", java.util.Map.of("city", "Tokyo")),
                            new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(3, 2)));
                }
                Message lastMessage = messages.getLast();
                ContentBlock.ToolResult toolResult = (ContentBlock.ToolResult) lastMessage.content().getFirst();
                return List.of(
                        new ModelEvent.TextDelta("Approval result: " + toolResult.content()),
                        new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(4, 2)));
            }
        };
    }

    private static Model toolUseWithCacheMetricsModel() {
        return new Model() {
            private int calls;

            @Override
            public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools) {
                calls++;
                if (calls == 1) {
                    return List.of(
                            new ModelEvent.ToolUse("tool-1", "approvalTool", java.util.Map.of("city", "Tokyo")),
                            new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(3, 2, 20, 10)));
                }
                Message lastMessage = messages.getLast();
                ContentBlock.ToolResult toolResult = (ContentBlock.ToolResult) lastMessage.content().getFirst();
                return List.of(
                        new ModelEvent.TextDelta("Approval result: " + toolResult.content()),
                        new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(4, 2, 5, 4)));
            }
        };
    }

    private static Tool stubTool() {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec("approvalTool", "approval", null);
            }

            @Override
            public ToolResult invoke(Object input) {
                return ToolResult.success("tool-1", input);
            }
        };
    }

    private static StreamingModel streamingToolModel() {
        return new StreamingModel() {
            private int calls;

            @Override
            public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools) {
                throw new AssertionError("Expected streaming path");
            }

            @Override
            public void converseStream(
                    List<Message> messages,
                    List<ToolSpec> tools,
                    String systemPrompt,
                    ToolSelection toolSelection,
                    java.util.function.Consumer<ModelEvent> eventConsumer) {
                calls++;
                if (calls == 1) {
                    eventConsumer.accept(new ModelEvent.TextDelta("thinking"));
                    eventConsumer.accept(new ModelEvent.ToolUse("tool-1", "echo", java.util.Map.of("value", "a")));
                    eventConsumer.accept(new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
                    return;
                }
                eventConsumer.accept(new ModelEvent.TextDelta("done"));
                eventConsumer.accept(new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
            }
        };
    }

    private static StreamingModel streamingRetryModel() {
        return new StreamingModel() {
            private int calls;

            @Override
            public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools) {
                throw new AssertionError("Expected streaming path");
            }

            @Override
            public void converseStream(
                    List<Message> messages,
                    List<ToolSpec> tools,
                    String systemPrompt,
                    ToolSelection toolSelection,
                    java.util.function.Consumer<ModelEvent> eventConsumer) {
                calls++;
                if (calls == 1) {
                    eventConsumer.accept(new ModelEvent.TextDelta("draft"));
                    eventConsumer.accept(new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
                    return;
                }
                eventConsumer.accept(new ModelEvent.TextDelta("final"));
                eventConsumer.accept(new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
            }
        };
    }

    private static StreamingModel failingStreamingModel() {
        return new StreamingModel() {
            @Override
            public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools) {
                throw new AssertionError("Expected streaming path");
            }

            @Override
            public void converseStream(
                    List<Message> messages,
                    List<ToolSpec> tools,
                    String systemPrompt,
                    ToolSelection toolSelection,
                    java.util.function.Consumer<ModelEvent> eventConsumer) {
                eventConsumer.accept(new ModelEvent.TextDelta("partial"));
                throw new io.arachne.strands.model.ModelException("boom");
            }
        };
    }
}
