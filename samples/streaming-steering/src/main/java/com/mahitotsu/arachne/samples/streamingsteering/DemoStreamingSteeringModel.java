package com.mahitotsu.arachne.samples.streamingsteering;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mahitotsu.arachne.strands.model.ModelEvent;
import com.mahitotsu.arachne.strands.model.StreamingModel;
import com.mahitotsu.arachne.strands.model.ToolSelection;
import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.types.ContentBlock;
import com.mahitotsu.arachne.strands.types.Message;

final class DemoStreamingSteeringModel implements StreamingModel {

    private final List<String> systemPrompts = new ArrayList<>();
    private int calls;

    @Override
    public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools) {
        throw new AssertionError("Expected the streaming path for the streaming and steering sample");
    }

    @Override
    public void converseStream(
            List<Message> messages,
            List<ToolSpec> tools,
            String systemPrompt,
            ToolSelection toolSelection,
            java.util.function.Consumer<ModelEvent> eventConsumer) {
        calls++;
        systemPrompts.add(systemPrompt == null ? "" : systemPrompt);

        if (calls == 1) {
            eventConsumer.accept(new ModelEvent.TextDelta("Checking the refund guidance..."));
            eventConsumer.accept(new ModelEvent.ToolUse("tool-1", "policy_lookup", Map.of("topic", "refunds")));
            eventConsumer.accept(new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            return;
        }

        if (calls == 2) {
            eventConsumer.accept(new ModelEvent.TextDelta("The risky live lookup was blocked."));
            eventConsumer.accept(new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
            return;
        }

        eventConsumer.accept(new ModelEvent.TextDelta("Cached refund policy: unopened items can be returned within 30 days with the original receipt."));
        eventConsumer.accept(new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
    }

    int invocationCount() {
        return systemPrompts.size();
    }

    boolean sawGuidanceMessage(List<Message> messages) {
        return messages.stream()
                .filter(message -> message.role() == Message.Role.USER)
                .flatMap(message -> message.content().stream())
                .filter(ContentBlock.Text.class::isInstance)
                .map(ContentBlock.Text.class::cast)
                .map(ContentBlock.Text::text)
                .anyMatch(text -> text.contains("Provide the cached refund policy summary directly."));
    }
}