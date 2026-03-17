package io.arachne.strands.agent;

import java.util.ArrayList;
import java.util.List;

import io.arachne.strands.eventloop.EventLoop;
import io.arachne.strands.eventloop.EventLoopResult;
import io.arachne.strands.hooks.HookRegistry;
import io.arachne.strands.model.Model;
import io.arachne.strands.tool.Tool;
import io.arachne.strands.types.Message;

/**
 * Default {@link Agent} implementation.
 *
 * <p>Holds the conversation history across multiple {@link #run} calls,
 * delegates the model↔tool cycle to {@link EventLoop},
 * and fires lifecycle hooks via {@link HookRegistry}.
 *
 * <p>Corresponds to {@code strands.agent.Agent} (the concrete class) in the Python SDK.
 *
 * <p>Instances are created via {@link io.arachne.strands.spring.AgentFactory.Builder#build()}.
 */
public class DefaultAgent implements Agent {

    private final Model model;
    private final List<Tool> tools;
    private final EventLoop eventLoop;
    private final HookRegistry hooks;
    private final String systemPrompt;

    /** Mutable conversation history. Growing across multiple run() calls = multi-turn conversation. */
    private final List<Message> messages = new ArrayList<>();

    public DefaultAgent(
            Model model,
            List<Tool> tools,
            EventLoop eventLoop,
            HookRegistry hooks) {
        this(model, tools, eventLoop, hooks, null);
    }

    public DefaultAgent(
            Model model,
            List<Tool> tools,
            EventLoop eventLoop,
            HookRegistry hooks,
            String systemPrompt) {
        this.model = model;
        this.tools = List.copyOf(tools);
        this.eventLoop = eventLoop;
        this.hooks = hooks;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public AgentResult run(String prompt) {
        // ── hook callsite: BeforeInvocation ─────────────────────────────────
        hooks.onBeforeInvocation(prompt);

        messages.add(Message.user(prompt));

        EventLoopResult loopResult = eventLoop.run(model, messages, tools, systemPrompt, 0);

        // ── hook callsite: AfterInvocation ───────────────────────────────────
        hooks.onAfterInvocation(loopResult.text());

        return new AgentResult(loopResult.text(), List.copyOf(messages), loopResult.stopReason());
    }

    @Override
    public Model getModel() {
        return model;
    }

    @Override
    public List<Tool> getTools() {
        return tools;
    }

    @Override
    public List<Message> getMessages() {
        return List.copyOf(messages);
    }
}
