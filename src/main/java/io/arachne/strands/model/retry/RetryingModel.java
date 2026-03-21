package io.arachne.strands.model.retry;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import io.arachne.strands.model.Model;
import io.arachne.strands.model.ModelEvent;
import io.arachne.strands.model.StreamingModel;
import io.arachne.strands.model.ToolSelection;
import io.arachne.strands.model.ToolSpec;
import io.arachne.strands.types.Message;

/**
 * Model decorator that applies a retry strategy to model invocation calls.
 */
public class RetryingModel implements StreamingModel {

    private final Model delegate;
    private final ModelRetryStrategy retryStrategy;

    public RetryingModel(Model delegate, ModelRetryStrategy retryStrategy) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.retryStrategy = Objects.requireNonNull(retryStrategy, "retryStrategy must not be null");
    }

    @Override
    public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools) {
        return retryStrategy.execute(() -> delegate.converse(messages, tools));
    }

    @Override
    public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools, String systemPrompt) {
        return retryStrategy.execute(() -> delegate.converse(messages, tools, systemPrompt));
    }

    @Override
    public Iterable<ModelEvent> converse(
            List<Message> messages,
            List<ToolSpec> tools,
            String systemPrompt,
            ToolSelection toolSelection) {
        return retryStrategy.execute(() -> delegate.converse(messages, tools, systemPrompt, toolSelection));
    }

    @Override
    public void converseStream(
            List<Message> messages,
            List<ToolSpec> tools,
            String systemPrompt,
            ToolSelection toolSelection,
            Consumer<ModelEvent> eventConsumer) {
        if (delegate instanceof StreamingModel streamingModel) {
            streamingModel.converseStream(messages, tools, systemPrompt, toolSelection, eventConsumer);
            return;
        }
        StreamingModel.super.converseStream(messages, tools, systemPrompt, toolSelection, eventConsumer);
    }
}