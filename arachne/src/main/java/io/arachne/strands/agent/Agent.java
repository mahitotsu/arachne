package io.arachne.strands.agent;

import java.util.List;
import java.util.function.Consumer;

import io.arachne.strands.model.Model;
import io.arachne.strands.tool.Tool;
import io.arachne.strands.types.Message;

/**
 * Core agent that orchestrates an LLM model with a set of tools.
 *
 * <p>Corresponds to {@code strands.agent.Agent} in the Python SDK.
 */
public interface Agent {

    /**
     * Send a prompt and receive a response, invoking tools as needed.
     */
    AgentResult run(String prompt);

    /**
     * Send a prompt and require the final answer as structured output.
     */
    <T> T run(String prompt, Class<T> outputType);

    /**
     * Send a prompt and subscribe to incremental runtime events.
     */
    AgentResult stream(String prompt, Consumer<AgentStreamEvent> eventConsumer);

    /**
     * Resume a previously interrupted invocation.
     */
    AgentResult resume(InterruptResponse... responses);

    /**
     * Resume a previously interrupted invocation.
     */
    AgentResult resume(List<InterruptResponse> responses);

    /**
     * The pending interrupts currently attached to this agent session.
     */
    List<AgentInterrupt> getPendingInterrupts();

    /**
     * The model this agent is bound to.
     */
    Model getModel();

    /**
     * The tools available to this agent.
     */
    List<Tool> getTools();

    /**
     * The conversation history accumulated so far.
     */
    List<Message> getMessages();

    /**
     * Session-scoped state available to this agent.
     */
    AgentState getState();
}
