package com.mahitotsu.arachne.strands.model;

import java.util.List;

import com.mahitotsu.arachne.strands.types.Message;

/**
 * Abstraction over an LLM provider.
 *
 * <p>Corresponds to {@code strands.models.Model} in the Python SDK.
 * Concrete implementations provide provider-specific integrations such as Bedrock.
 */
public interface Model {

    /**
     * Send a converse request and stream back events.
     *
     * @param messages conversation history
     * @param tools    tool definitions available to the model
     * @return an iterable of response events
     */
    Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools);

    /**
     * Send a converse request with an optional system prompt.
     *
     * <p>Implementations that do not use system prompts may rely on the default behavior.
     */
    default Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools, String systemPrompt) {
        return converse(messages, tools);
    }

    /**
     * Send a converse request with an optional system prompt and tool selection hint.
     */
    default Iterable<ModelEvent> converse(
            List<Message> messages,
            List<ToolSpec> tools,
            String systemPrompt,
            ToolSelection toolSelection) {
        return converse(messages, tools, systemPrompt);
    }
}
