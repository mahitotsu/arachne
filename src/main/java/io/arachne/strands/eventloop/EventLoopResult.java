package io.arachne.strands.eventloop;

import java.util.List;
import java.util.Objects;

import io.arachne.strands.agent.AgentInterrupt;
import io.arachne.strands.model.ModelEvent;

/**
 * Value returned from a completed event-loop run.
 */
public final class EventLoopResult {

    private final String text;
    private final String stopReason;
    private final ModelEvent.Usage usage;
    private final List<AgentInterrupt> interrupts;

    public EventLoopResult(
            String text,
            String stopReason,
            ModelEvent.Usage usage,
            List<AgentInterrupt> interrupts) {
        this.text = Objects.requireNonNull(text, "text must not be null");
        this.stopReason = Objects.requireNonNull(stopReason, "stopReason must not be null");
        this.usage = Objects.requireNonNull(usage, "usage must not be null");
        this.interrupts = List.copyOf(Objects.requireNonNull(interrupts, "interrupts must not be null"));
    }

    public EventLoopResult(String text, String stopReason, ModelEvent.Usage usage) {
        this(text, stopReason, usage, List.of());
    }

    public String text() {
        return text;
    }

    public String stopReason() {
        return stopReason;
    }

    public ModelEvent.Usage usage() {
        return usage;
    }

    public int inputTokens() {
        return usage.inputTokens();
    }

    public int outputTokens() {
        return usage.outputTokens();
    }

    public List<AgentInterrupt> interrupts() {
        return interrupts;
    }

    public boolean interrupted() {
        return !interrupts.isEmpty();
    }
}
