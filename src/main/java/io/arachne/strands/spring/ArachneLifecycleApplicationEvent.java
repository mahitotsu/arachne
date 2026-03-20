package io.arachne.strands.spring;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.ApplicationEvent;

import io.arachne.strands.model.ToolSpec;
import io.arachne.strands.tool.ToolResult;
import io.arachne.strands.types.Message;

/**
 * Observation-only Spring event emitted for Arachne lifecycle activity.
 */
public final class ArachneLifecycleApplicationEvent extends ApplicationEvent {

    private final String type;
    private final Object payload;

    public ArachneLifecycleApplicationEvent(Object source, String type, Object payload) {
        super(source);
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.payload = Objects.requireNonNull(payload, "payload must not be null");
    }

    public String type() {
        return type;
    }

    public Object payload() {
        return payload;
    }

    public record InvocationObservation(
            String phase,
            String prompt,
            String text,
            String stopReason,
            List<Message> messages,
            Map<String, Object> state
    ) {}

    public record MessageAddedObservation(
            Message message,
            List<Message> messages,
            Map<String, Object> state
    ) {}

    public record ModelCallObservation(
            String phase,
            List<Message> messages,
            List<ToolSpec> toolSpecs,
            String systemPrompt,
            Message response,
            String stopReason,
            Map<String, Object> state
    ) {}

    public record ToolCallObservation(
            String phase,
            String toolName,
            String toolUseId,
            Object input,
            ToolResult result,
            Map<String, Object> state
    ) {}
}