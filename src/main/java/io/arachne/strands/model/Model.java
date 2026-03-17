package io.arachne.strands.model;

import io.arachne.strands.types.Message;

import java.util.List;

/**
 * Abstraction over an LLM provider.
 *
 * <p>Corresponds to {@code strands.models.Model} in the Python SDK.
 * Concrete implementations: BedrockModel, OpenAIModel, etc.
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
}
