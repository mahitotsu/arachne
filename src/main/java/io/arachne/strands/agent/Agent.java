package io.arachne.strands.agent;

import io.arachne.strands.model.Model;
import io.arachne.strands.tool.Tool;
import io.arachne.strands.types.Message;

import java.util.List;

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
}
