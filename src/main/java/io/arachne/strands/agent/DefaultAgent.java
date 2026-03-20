package io.arachne.strands.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.arachne.strands.agent.conversation.ConversationManager;
import io.arachne.strands.agent.conversation.NoOpConversationManager;
import io.arachne.strands.eventloop.EventLoop;
import io.arachne.strands.eventloop.EventLoopResult;
import io.arachne.strands.eventloop.StructuredOutputContext;
import io.arachne.strands.hooks.HookRegistry;
import io.arachne.strands.model.Model;
import io.arachne.strands.session.AgentSession;
import io.arachne.strands.session.SessionManager;
import io.arachne.strands.tool.BeanValidationSupport;
import io.arachne.strands.tool.StructuredOutputTool;
import io.arachne.strands.tool.Tool;
import io.arachne.strands.types.Message;
import jakarta.validation.Validator;

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
    private final Validator validator;
    private final ObjectMapper objectMapper;
    private final ConversationManager conversationManager;
    private final SessionManager sessionManager;
    private final String sessionId;
    private final AgentState state;

    /** Mutable conversation history. Growing across multiple run() calls = multi-turn conversation. */
    private final List<Message> messages = new ArrayList<>();

    public DefaultAgent(
            Model model,
            List<Tool> tools,
            EventLoop eventLoop,
            HookRegistry hooks) {
        this(model, tools, eventLoop, hooks, null, BeanValidationSupport.defaultValidator());
    }

    public DefaultAgent(
            Model model,
            List<Tool> tools,
            EventLoop eventLoop,
            HookRegistry hooks,
            String systemPrompt) {
        this(model, tools, eventLoop, hooks, systemPrompt, BeanValidationSupport.defaultValidator());
    }

    public DefaultAgent(
            Model model,
            List<Tool> tools,
            EventLoop eventLoop,
            HookRegistry hooks,
            String systemPrompt,
            Validator validator) {
        this(
                model,
                tools,
                eventLoop,
                hooks,
                systemPrompt,
                validator,
                new ObjectMapper(),
                new NoOpConversationManager(),
                null,
                null,
                new AgentState());
    }

            public DefaultAgent(
                Model model,
                List<Tool> tools,
                EventLoop eventLoop,
                HookRegistry hooks,
                String systemPrompt,
                Validator validator,
                ConversationManager conversationManager,
                SessionManager sessionManager,
                String sessionId,
                AgentState state) {
            this(
                model,
                tools,
                eventLoop,
                hooks,
                systemPrompt,
                validator,
                new ObjectMapper(),
                conversationManager,
                sessionManager,
                sessionId,
                state);
            }

    public DefaultAgent(
            Model model,
            List<Tool> tools,
            EventLoop eventLoop,
            HookRegistry hooks,
            String systemPrompt,
            Validator validator,
                ObjectMapper objectMapper,
            ConversationManager conversationManager,
            SessionManager sessionManager,
            String sessionId,
            AgentState state) {
        this.model = model;
        this.tools = List.copyOf(tools);
        this.eventLoop = eventLoop;
        this.hooks = hooks;
        this.systemPrompt = systemPrompt;
        this.validator = validator;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.conversationManager = conversationManager;
        this.sessionManager = sessionManager;
        this.sessionId = sessionId;
        this.state = state == null ? new AgentState() : state;
        restoreSession();
    }

    @Override
    public AgentResult run(String prompt) {
        // ── hook callsite: BeforeInvocation ─────────────────────────────────
        hooks.onBeforeInvocation(prompt);

        messages.add(Message.user(prompt));

        EventLoopResult loopResult = eventLoop.run(model, messages, tools, systemPrompt, 0);
        conversationManager.applyManagement(messages);
        persistSession();

        // ── hook callsite: AfterInvocation ───────────────────────────────────
        hooks.onAfterInvocation(loopResult.text());

        return new AgentResult(loopResult.text(), List.copyOf(messages), loopResult.stopReason());
    }

    @Override
    public <T> T run(String prompt, Class<T> outputType) {
        hooks.onBeforeInvocation(prompt);

        messages.add(Message.user(prompt));

        StructuredOutputTool<T> structuredOutputTool = new StructuredOutputTool<>(
            outputType,
            new io.arachne.strands.schema.JsonSchemaGenerator(objectMapper),
            objectMapper,
            validator);
        StructuredOutputContext<T> structuredOutputContext = new StructuredOutputContext<>(structuredOutputTool);
        List<Tool> invocationTools = new ArrayList<>(tools);
        invocationTools.add(structuredOutputTool);

        EventLoopResult loopResult = eventLoop.run(
                model,
                messages,
                List.copyOf(invocationTools),
                systemPrompt,
                structuredOutputContext,
                0);

        conversationManager.applyManagement(messages);
        persistSession();

        hooks.onAfterInvocation(loopResult.text());

        return structuredOutputContext.requireValue();
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

    @Override
    public AgentState getState() {
        return state;
    }

    private void restoreSession() {
        if (sessionManager == null || sessionId == null || sessionId.isBlank()) {
            return;
        }
        AgentSession session = sessionManager.load(sessionId);
        if (session == null) {
            return;
        }
        messages.clear();
        messages.addAll(session.messages());
        state.replaceWith(session.state());
        conversationManager.restore(session.conversationManagerState());
    }

    private void persistSession() {
        if (sessionManager == null || sessionId == null || sessionId.isBlank()) {
            return;
        }
        sessionManager.save(
                sessionId,
                new AgentSession(List.copyOf(messages), state.get(), conversationManager.getState()));
    }
}
