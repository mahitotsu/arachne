package io.arachne.samples.phase6streamingsteering;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.arachne.strands.model.ModelEvent;
import io.arachne.strands.model.StreamingModel;
import io.arachne.strands.model.ToolSelection;
import io.arachne.strands.model.ToolSpec;
import io.arachne.strands.types.ContentBlock;
import io.arachne.strands.types.Message;

final class DemoStreamingSteeringModel implements StreamingModel {

    private final List<String> systemPrompts = new ArrayList<>();
    private int calls;

    @Override
    public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools) {
        throw new AssertionError("Expected the streaming path for the Phase 6 sample");
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