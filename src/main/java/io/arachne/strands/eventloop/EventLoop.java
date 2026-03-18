package io.arachne.strands.eventloop;

import java.util.ArrayList;
import java.util.List;

import io.arachne.strands.hooks.HookRegistry;
import io.arachne.strands.model.Model;
import io.arachne.strands.model.ModelEvent;
import io.arachne.strands.model.ToolSelection;
import io.arachne.strands.model.ToolSpec;
import io.arachne.strands.tool.StructuredOutputException;
import io.arachne.strands.tool.Tool;
import io.arachne.strands.tool.ToolExecutor;
import io.arachne.strands.tool.ToolResult;
import io.arachne.strands.types.ContentBlock;
import io.arachne.strands.types.Message;

/**
 * Synchronous model→tool→model event loop.
 *
 * <p>Corresponds to {@code strands.event_loop.event_loop_cycle} in the Python SDK.
 * This Java port is intentionally synchronous (blocking); reactive / virtual-thread
 * variants are planned for Phase 6.
 *
 * <p>The loop:
 * <ol>
 *   <li>Calls {@link Model#converse} with the current conversation and tool specs.</li>
 *   <li>Assembles the assistant {@link Message} from the emitted {@link ModelEvent}s.</li>
 *   <li>If the model stopped due to {@code tool_use}, executes each requested tool,
 *       appends a {@code user} message containing all tool results, then recurses.</li>
 *   <li>Otherwise returns the {@link EventLoopResult}.</li>
 * </ol>
 *
 * <p>Hook callsites are present but delegate to {@link HookRegistry} no-ops in Phase 1.
 * Phase 4 will wire in real hook dispatch.
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
            int cycleCount) {
        return run(model, messages, tools, null, null, cycleCount);
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
            int cycleCount) {
        return run(model, messages, tools, systemPrompt, null, cycleCount);
    }

    public EventLoopResult run(
            Model model,
            List<Message> messages,
            List<Tool> tools,
            String systemPrompt,
            StructuredOutputContext<?> structuredOutputContext,
            int cycleCount) {
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
        hooks.onBeforeModelCall(messages, toolSpecs);

        Iterable<ModelEvent> events = model.converse(messages, toolSpecs, systemPrompt, toolSelection);

        // Accumulate text deltas and tool-use requests from the model response
        StringBuilder textBuilder = new StringBuilder();
        List<ContentBlock.ToolUse> toolUseBlocks = new ArrayList<>();
        String stopReason = "end_turn";
        int inputTokens = 0;
        int outputTokens = 0;

        for (ModelEvent event : events) {
            switch (event) {
                case ModelEvent.TextDelta td -> textBuilder.append(td.delta());
                case ModelEvent.ToolUse tu ->
                        toolUseBlocks.add(new ContentBlock.ToolUse(tu.toolUseId(), tu.name(), tu.input()));
                case ModelEvent.Metadata m -> {
                    stopReason = m.stopReason();
                    inputTokens = m.usage().inputTokens();
                    outputTokens = m.usage().outputTokens();
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
        messages.add(assistantMessage);

        // ── hook callsite: AfterModelCall ────────────────────────────────────
        hooks.onAfterModelCall(assistantMessage, stopReason);

        if ("tool_use".equals(stopReason)) {
            List<ContentBlock> toolResultBlocks = new ArrayList<>();

            for (ToolResult result : toolExecutor.execute(tools, toolUseBlocks, hooks)) {
                toolResultBlocks.add(
                        new ContentBlock.ToolResult(
                                result.toolUseId(),
                                result.content(),
                                result.status().name().toLowerCase()));
            }

            messages.add(new Message(Message.Role.USER, List.copyOf(toolResultBlocks)));

            if (structuredOutputContext != null && structuredOutputContext.isSatisfied()) {
                return new EventLoopResult(text, stopReason, inputTokens, outputTokens);
            }

            // Recurse for the next model turn
            return run(model, messages, tools, systemPrompt, structuredOutputContext, cycleCount + 1);
        }

        if (structuredOutputContext != null && !structuredOutputContext.isSatisfied()) {
            if (structuredOutputContext.forceAttempted()) {
                throw new StructuredOutputException(
                        "The model failed to invoke the structured output tool even after it was forced.");
            }
            structuredOutputContext.markForceAttempted();
            messages.add(Message.user(structuredOutputContext.forcePrompt()));
            return run(model, messages, tools, systemPrompt, structuredOutputContext, cycleCount + 1);
        }

        return new EventLoopResult(text, stopReason, inputTokens, outputTokens);
    }
}
