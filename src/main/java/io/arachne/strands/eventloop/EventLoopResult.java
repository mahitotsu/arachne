package io.arachne.strands.eventloop;

import java.util.List;
import java.util.Objects;

import io.arachne.strands.agent.AgentInterrupt;

/**
 * Value returned from a completed event-loop run.
 */
public final class EventLoopResult {

    private final String text;
    private final String stopReason;
    private final int inputTokens;
    private final int outputTokens;
    private final List<AgentInterrupt> interrupts;

    public EventLoopResult(
            String text,
            String stopReason,
            int inputTokens,
            int outputTokens,
            List<AgentInterrupt> interrupts) {
        this.text = Objects.requireNonNull(text, "text must not be null");
        this.stopReason = Objects.requireNonNull(stopReason, "stopReason must not be null");
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.interrupts = List.copyOf(Objects.requireNonNull(interrupts, "interrupts must not be null"));
    }

    public EventLoopResult(String text, String stopReason, int inputTokens, int outputTokens) {
        this(text, stopReason, inputTokens, outputTokens, List.of());
    }

    public String text() {
        return text;
    }

    public String stopReason() {
        return stopReason;
    }

    public int inputTokens() {
        return inputTokens;
    }

    public int outputTokens() {
        return outputTokens;
    }

    public List<AgentInterrupt> interrupts() {
        return interrupts;
    }

    public boolean interrupted() {
        return !interrupts.isEmpty();
    }
}
