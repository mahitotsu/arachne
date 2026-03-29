package io.arachne.strands.eventloop;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.arachne.strands.agent.AgentState;
import io.arachne.strands.agent.AgentStreamEvent;
import io.arachne.strands.hooks.AfterModelCallEvent;
import io.arachne.strands.hooks.BeforeModelCallEvent;
import io.arachne.strands.hooks.HookRegistry;
import io.arachne.strands.hooks.MessageAddedEvent;
import io.arachne.strands.model.Model;
import io.arachne.strands.model.ModelEvent;
import io.arachne.strands.model.StreamingModel;
import io.arachne.strands.model.ToolSelection;
import io.arachne.strands.model.ToolSpec;
import io.arachne.strands.tool.StructuredOutputException;
import io.arachne.strands.tool.Tool;
import io.arachne.strands.tool.ToolExecutionInterruptedException;
import io.arachne.strands.tool.ToolExecutor;
import io.arachne.strands.tool.ToolResult;
import io.arachne.strands.types.ContentBlock;
import io.arachne.strands.types.Message;

/**
 * Synchronous model→tool→model event loop.
 *
 * <p>Corresponds to {@code strands.event_loop.event_loop_cycle} in the Python SDK.
 * This Java port keeps the core event loop synchronous (blocking); Phase 6 adds
 * callback-based streaming on top of the same loop, while reactive / virtual-thread
 * variants remain future work.
 *
 * <p>The loop:
 * <ol>
 *   <li>Calls {@link Model#converse} with the current conversation and tool specs.</li>
 *   <li>Assembles the assistant {@link Message} from the emitted {@link ModelEvent}s.</li>
 *   <li>If the model stopped due to {@code tool_use}, executes each requested tool,
 *       appends a {@code user} message containing all tool results, then recurses.</li>
 *   <li>Otherwise returns the {@link EventLoopResult}.</li>
 * </ol>
 */
public class EventLoop {

    /** Maximum recursive cycles to prevent infinite tool-use loops. */
    static final int MAX_CYCLES = 10;

    private final HookRegistry hooks;
    private final ToolExecutor toolExecutor;

    public EventLoop(HookRegistry hooks) {
        this(hooks, new ToolExecutor());
    }

    public EventLoop(HookRegistry hooks, ToolExecutor toolExecutor) {
        this.hooks = hooks;
        this.toolExecutor = toolExecutor;
    }

    /**
     * Run the event loop starting from the supplied (mutable) message list.
     *
     * <p>The list is modified in-place: each cycle appends the assistant reply,
     * and (when tools are invoked) a subsequent user message with tool results.
     *
     * @param model      model to call
     * @param messages   mutable conversation history; caller must have appended the user turn
     * @param tools      tools available to the model
     * @param cycleCount current recursion depth (pass 0 from outside)
     * @throws EventLoopException if {@code cycleCount} exceeds {@link #MAX_CYCLES}
     */
    public EventLoopResult run(
            Model model,
            List<Message> messages,
            List<Tool> tools,
            AgentState state,
            int cycleCount) {
        return run(model, messages, tools, null, null, state, cycleCount);
    }

    /**
     * Run the event loop starting from the supplied (mutable) message list.
     *
     * @param systemPrompt optional system prompt forwarded to the model provider
     */
    public EventLoopResult run(
            Model model,
            List<Message> messages,
            List<Tool> tools,
            String systemPrompt,
            AgentState state,
            int cycleCount) {
        return run(model, messages, tools, systemPrompt, null, state, cycleCount);
    }

    public EventLoopResult runStreaming(
            Model model,
            List<Message> messages,
            List<Tool> tools,
            String systemPrompt,
            AgentState state,
            int cycleCount,
            Consumer<AgentStreamEvent> eventConsumer) {
        return run(model, messages, tools, systemPrompt, null, state, cycleCount, eventConsumer);
    }

    public EventLoopResult run(
            Model model,
            List<Message> messages,
            List<Tool> tools,
            String systemPrompt,
            StructuredOutputContext<?> structuredOutputContext,
            AgentState state,
            int cycleCount) {
        return run(model, messages, tools, systemPrompt, structuredOutputContext, state, cycleCount, null);
    }

    private EventLoopResult run(
            Model model,
            List<Message> messages,
            List<Tool> tools,
            String systemPrompt,
            StructuredOutputContext<?> structuredOutputContext,
            AgentState state,
            int cycleCount,
            Consumer<AgentStreamEvent> eventConsumer) {
        model = java.util.Objects.requireNonNull(model, "model must not be null");
        if (cycleCount >= MAX_CYCLES) {
            throw new EventLoopException("Max event-loop cycles exceeded: " + MAX_CYCLES);
        }

        List<ToolSpec> toolSpecs = tools.stream().map(Tool::spec).toList();
        ToolSelection toolSelection = null;
        if (structuredOutputContext != null
                && structuredOutputContext.forceAttempted()
                && !structuredOutputContext.isSatisfied()) {
            toolSelection = ToolSelection.force(structuredOutputContext.forcedToolName());
        }

        // ── hook callsite: BeforeModelCall ──────────────────────────────────
        BeforeModelCallEvent beforeModelCallEvent = hooks.onBeforeModelCall(
            new BeforeModelCallEvent(messages, toolSpecs, systemPrompt, toolSelection, state));

        List<ModelEvent> events = collectModelEvents(model, beforeModelCallEvent, eventConsumer);

        // Accumulate text deltas and tool-use requests from the model response
        StringBuilder textBuilder = new StringBuilder();
        List<ContentBlock.ToolUse> toolUseBlocks = new ArrayList<>();
        String stopReason = "end_turn";
        ModelEvent.Usage usage = ModelEvent.ZERO_USAGE;

        for (ModelEvent event : events) {
            switch (event) {
                case ModelEvent.TextDelta td -> textBuilder.append(td.delta());
                case ModelEvent.ToolUse tu -> toolUseBlocks.add(new ContentBlock.ToolUse(tu.toolUseId(), tu.name(), tu.input()));
                case ModelEvent.Metadata m -> {
                    stopReason = m.stopReason();
                    usage = m.usage();
                }
            }
        }

        // Build the assistant content list: text first, then tool-use blocks
        List<ContentBlock> assistantContent = new ArrayList<>();
        String text = textBuilder.toString();
        if (!text.isBlank()) {
            assistantContent.add(ContentBlock.text(text));
        }
        assistantContent.addAll(toolUseBlocks);

        Message assistantMessage = new Message(Message.Role.ASSISTANT, List.copyOf(assistantContent));

        // ── hook callsite: AfterModelCall ────────────────────────────────────
        AfterModelCallEvent afterModelCallEvent = hooks.onAfterModelCall(
            new AfterModelCallEvent(assistantMessage, stopReason, messages, state));
        assistantMessage = afterModelCallEvent.response();
        stopReason = afterModelCallEvent.stopReason();
        text = assistantMessage.content().stream()
            .filter(ContentBlock.Text.class::isInstance)
            .map(ContentBlock.Text.class::cast)
            .map(ContentBlock.Text::text)
            .collect(java.util.stream.Collectors.joining());

        if (afterModelCallEvent.retryRequested()) {
            if (eventConsumer != null) {
                eventConsumer.accept(new AgentStreamEvent.Retry(afterModelCallEvent.retryGuidance()));
            }
            Message guidanceMessage = Message.user(afterModelCallEvent.retryGuidance());
            messages.add(guidanceMessage);
            hooks.onMessageAdded(new MessageAddedEvent(guidanceMessage, messages, state));
                EventLoopResult retried = run(
                    model,
                    messages,
                    tools,
                    beforeModelCallEvent.systemPrompt(),
                    structuredOutputContext,
                    state,
                    cycleCount + 1,
                    eventConsumer);
                return new EventLoopResult(retried.text(), retried.stopReason(), usage.plus(retried.usage()), retried.interrupts());
        }

        messages.add(assistantMessage);
        hooks.onMessageAdded(new MessageAddedEvent(assistantMessage, messages, state));

        if ("tool_use".equals(stopReason)) {
            List<ContentBlock.ToolUse> requestedToolUseBlocks = assistantMessage.content().stream()
                .filter(ContentBlock.ToolUse.class::isInstance)
                .map(ContentBlock.ToolUse.class::cast)
                .toList();
            List<ContentBlock> toolResultBlocks = new ArrayList<>();

            try {
                for (ToolResult result : toolExecutor.execute(tools, requestedToolUseBlocks, hooks, state)) {
                    if (eventConsumer != null) {
                        eventConsumer.accept(new AgentStreamEvent.ToolResultObserved(result));
                    }
                    toolResultBlocks.add(
                            new ContentBlock.ToolResult(
                                    result.toolUseId(),
                                    result.content(),
                                    result.status().name().toLowerCase()));
                }
            } catch (ToolExecutionInterruptedException interruptedException) {
                return new EventLoopResult(text, "interrupt", usage, interruptedException.interrupts());
            }

            Message toolResultMessage = new Message(Message.Role.USER, List.copyOf(toolResultBlocks));
            messages.add(toolResultMessage);
            hooks.onMessageAdded(new MessageAddedEvent(toolResultMessage, messages, state));

            if (structuredOutputContext != null && structuredOutputContext.isSatisfied()) {
                return new EventLoopResult(text, stopReason, usage);
            }

            // Recurse for the next model turn
                EventLoopResult next = run(
                    model,
                    messages,
                    tools,
                    beforeModelCallEvent.systemPrompt(),
                    structuredOutputContext,
                    state,
                    cycleCount + 1,
                    eventConsumer);
                return new EventLoopResult(next.text(), next.stopReason(), usage.plus(next.usage()), next.interrupts());
        }

        if (structuredOutputContext != null && !structuredOutputContext.isSatisfied()) {
            if (structuredOutputContext.forceAttempted()) {
                throw new StructuredOutputException(
                        "The model failed to invoke the structured output tool even after it was forced.");
            }
            structuredOutputContext.markForceAttempted();
                Message forcePromptMessage = Message.user(structuredOutputContext.forcePrompt());
                messages.add(forcePromptMessage);
                hooks.onMessageAdded(new MessageAddedEvent(forcePromptMessage, messages, state));
                EventLoopResult forced = run(
                    model,
                    messages,
                    tools,
                    beforeModelCallEvent.systemPrompt(),
                    structuredOutputContext,
                    state,
                    cycleCount + 1,
                    eventConsumer);
                return new EventLoopResult(forced.text(), forced.stopReason(), usage.plus(forced.usage()), forced.interrupts());
        }

        return new EventLoopResult(text, stopReason, usage);
    }

    private List<ModelEvent> collectModelEvents(
            Model model,
            BeforeModelCallEvent beforeModelCallEvent,
            Consumer<AgentStreamEvent> eventConsumer) {
        List<ModelEvent> events = new ArrayList<>();
        Consumer<ModelEvent> collector = event -> {
            events.add(event);
            emitModelEvent(event, eventConsumer);
        };

        if (eventConsumer != null && model instanceof StreamingModel streamingModel) {
            streamingModel.converseStream(
                    beforeModelCallEvent.messages(),
                    beforeModelCallEvent.toolSpecs(),
                    beforeModelCallEvent.systemPrompt(),
                    beforeModelCallEvent.toolSelection(),
                    collector);
            return List.copyOf(events);
        }

        Iterable<ModelEvent> modelResponse = java.util.Objects.requireNonNull(model, "model must not be null").converse(
            beforeModelCallEvent.messages(),
            beforeModelCallEvent.toolSpecs(),
            beforeModelCallEvent.systemPrompt(),
            beforeModelCallEvent.toolSelection());
        Iterable<ModelEvent> responseEvents = java.util.Objects.requireNonNull(modelResponse, "model events must not be null");
        for (ModelEvent event : responseEvents) {
            collector.accept(event);
        }
        return List.copyOf(events);
    }

    private static void emitModelEvent(ModelEvent event, Consumer<AgentStreamEvent> eventConsumer) {
        if (eventConsumer == null) {
            return;
        }
        if (event instanceof ModelEvent.TextDelta td) {
            eventConsumer.accept(new AgentStreamEvent.TextDelta(td.delta()));
            return;
        }
        if (event instanceof ModelEvent.ToolUse tu) {
            eventConsumer.accept(new AgentStreamEvent.ToolUseRequested(tu.toolUseId(), tu.name(), tu.input()));
        }
    }
}
