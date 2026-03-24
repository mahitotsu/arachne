package io.arachne.strands.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.arachne.strands.agent.conversation.ConversationManager;
import io.arachne.strands.agent.conversation.NoOpConversationManager;
import io.arachne.strands.eventloop.EventLoop;
import io.arachne.strands.eventloop.EventLoopResult;
import io.arachne.strands.eventloop.StructuredOutputContext;
import io.arachne.strands.hooks.AfterInvocationEvent;
import io.arachne.strands.hooks.BeforeInvocationEvent;
import io.arachne.strands.hooks.HookRegistry;
import io.arachne.strands.hooks.MessageAddedEvent;
import io.arachne.strands.model.Model;
import io.arachne.strands.session.AgentSession;
import io.arachne.strands.session.SessionManager;
import io.arachne.strands.tool.BeanValidationSupport;
import io.arachne.strands.tool.StructuredOutputTool;
import io.arachne.strands.tool.Tool;
import io.arachne.strands.types.ContentBlock;
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
    private List<AgentInterrupt> pendingInterrupts = List.of();

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
        ensureNoPendingInterrupts();

        // ── hook callsite: BeforeInvocation ─────────────────────────────────
        BeforeInvocationEvent beforeInvocationEvent = hooks.onBeforeInvocation(
            new BeforeInvocationEvent(prompt, messages, state));

        addMessage(Message.user(beforeInvocationEvent.prompt()));

        EventLoopResult loopResult = eventLoop.run(model, messages, tools, systemPrompt, state, 0);
        if (loopResult.interrupted()) {
            pendingInterrupts = loopResult.interrupts();
            persistSession();
        } else {
            pendingInterrupts = List.of();
            conversationManager.applyManagement(messages);
            persistSession();
        }

        // ── hook callsite: AfterInvocation ───────────────────────────────────
        AfterInvocationEvent afterInvocationEvent = hooks.onAfterInvocation(new AfterInvocationEvent(
            loopResult.text(),
            messages,
            loopResult.stopReason(),
            state));

        return new AgentResult(
            afterInvocationEvent.text(),
            List.copyOf(afterInvocationEvent.messages()),
            afterInvocationEvent.stopReason(),
            new AgentResult.Metrics(loopResult.usage()),
            pendingInterrupts,
            this::resume);
    }

    @Override
    public <T> T run(String prompt, Class<T> outputType) {
        ensureNoPendingInterrupts();

        BeforeInvocationEvent beforeInvocationEvent = hooks.onBeforeInvocation(
            new BeforeInvocationEvent(prompt, messages, state));

        addMessage(Message.user(beforeInvocationEvent.prompt()));

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
                state,
                0);

        if (loopResult.interrupted()) {
            pendingInterrupts = loopResult.interrupts();
            persistSession();
            hooks.onAfterInvocation(new AfterInvocationEvent(
                loopResult.text(),
                messages,
                loopResult.stopReason(),
                state));
            throw new IllegalStateException(
                    "Structured output invocation was interrupted. Use Agent#run(String) and AgentResult.resume(...) instead.");
        }

        pendingInterrupts = List.of();
        conversationManager.applyManagement(messages);
        persistSession();

        hooks.onAfterInvocation(new AfterInvocationEvent(
            loopResult.text(),
            messages,
            loopResult.stopReason(),
            state));

        return structuredOutputContext.requireValue();
    }

    @Override
    public AgentResult stream(String prompt, Consumer<AgentStreamEvent> eventConsumer) {
        Objects.requireNonNull(eventConsumer, "eventConsumer must not be null");
        ensureNoPendingInterrupts();

        BeforeInvocationEvent beforeInvocationEvent = hooks.onBeforeInvocation(
                new BeforeInvocationEvent(prompt, messages, state));

        addMessage(Message.user(beforeInvocationEvent.prompt()));

        EventLoopResult loopResult = eventLoop.runStreaming(model, messages, tools, systemPrompt, state, 0, eventConsumer);
        AgentResult result = completeInvocation(loopResult);
        eventConsumer.accept(new AgentStreamEvent.Complete(result));
        return result;
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

    private void addMessage(Message message) {
        messages.add(message);
        hooks.onMessageAdded(new MessageAddedEvent(message, messages, state));
    }

    private AgentResult resume(List<InterruptResponse> responses) {
        if (pendingInterrupts.isEmpty()) {
            throw new IllegalStateException("This agent does not have any pending interrupts to resume.");
        }

        Map<String, InterruptResponse> responsesById = new LinkedHashMap<>();
        for (InterruptResponse response : responses) {
            InterruptResponse previous = responsesById.put(response.interruptId(), response);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate interrupt response: " + response.interruptId());
            }
        }

        List<ContentBlock.ToolResult> resumeBlocks = new ArrayList<>(pendingInterrupts.size());
        for (AgentInterrupt interrupt : pendingInterrupts) {
            InterruptResponse response = responsesById.remove(interrupt.id());
            if (response == null) {
                throw new IllegalArgumentException("Missing interrupt response for: " + interrupt.id());
            }
            resumeBlocks.add(new ContentBlock.ToolResult(interrupt.toolUseId(), response.response(), "success"));
        }
        if (!responsesById.isEmpty()) {
            throw new IllegalArgumentException("Unknown interrupt response ids: " + responsesById.keySet());
        }

        pendingInterrupts = List.of();
        addMessage(new Message(Message.Role.USER, List.copyOf(resumeBlocks)));

        EventLoopResult loopResult = eventLoop.run(model, messages, tools, systemPrompt, state, 0);
        if (loopResult.interrupted()) {
            pendingInterrupts = loopResult.interrupts();
            persistSession();
            return new AgentResult(
                    loopResult.text(),
                    List.copyOf(messages),
                    loopResult.stopReason(),
                    new AgentResult.Metrics(loopResult.usage()),
                    pendingInterrupts,
                    this::resume);
        }

        conversationManager.applyManagement(messages);
        persistSession();
            return new AgentResult(
                loopResult.text(),
                List.copyOf(messages),
                loopResult.stopReason(),
                new AgentResult.Metrics(loopResult.usage()));
    }

    private AgentResult completeInvocation(EventLoopResult loopResult) {
        if (loopResult.interrupted()) {
            pendingInterrupts = loopResult.interrupts();
            persistSession();
        } else {
            pendingInterrupts = List.of();
            conversationManager.applyManagement(messages);
            persistSession();
        }

        AfterInvocationEvent afterInvocationEvent = hooks.onAfterInvocation(new AfterInvocationEvent(
                loopResult.text(),
                messages,
                loopResult.stopReason(),
                state));

        return new AgentResult(
                afterInvocationEvent.text(),
                List.copyOf(afterInvocationEvent.messages()),
                afterInvocationEvent.stopReason(),
            new AgentResult.Metrics(loopResult.usage()),
                pendingInterrupts,
                this::resume);
    }

    private void ensureNoPendingInterrupts() {
        if (!pendingInterrupts.isEmpty()) {
            throw new IllegalStateException("This agent has pending interrupts. Resume the last result before starting a new invocation.");
        }
    }
}
