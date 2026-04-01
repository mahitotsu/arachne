package com.mahitotsu.arachne.strands.model;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import com.mahitotsu.arachne.strands.types.Message;

/**
 * Optional model extension that can push {@link ModelEvent}s as they arrive.
 */
public interface StreamingModel extends Model {

    default void converseStream(List<Message> messages, List<ToolSpec> tools, Consumer<ModelEvent> eventConsumer) {
        Objects.requireNonNull(eventConsumer, "eventConsumer must not be null");
        converse(messages, tools).forEach(eventConsumer);
    }

    default void converseStream(
            List<Message> messages,
            List<ToolSpec> tools,
            String systemPrompt,
            Consumer<ModelEvent> eventConsumer) {
        Objects.requireNonNull(eventConsumer, "eventConsumer must not be null");
        converse(messages, tools, systemPrompt).forEach(eventConsumer);
    }

    default void converseStream(
            List<Message> messages,
            List<ToolSpec> tools,
            String systemPrompt,
            ToolSelection toolSelection,
            Consumer<ModelEvent> eventConsumer) {
        Objects.requireNonNull(eventConsumer, "eventConsumer must not be null");
        converse(messages, tools, systemPrompt, toolSelection).forEach(eventConsumer);
    }
}