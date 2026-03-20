package io.arachne.strands.eventloop;

import java.util.List;
import java.util.Objects;

import io.arachne.strands.agent.AgentInterrupt;

/**
 * Value returned from a completed event-loop run.
 */
public record EventLoopResult(
                /** Concatenated text from all TextDelta events in the final assistant turn. */
                String text,
                /** Raw stop reason string as returned by the model (e.g. "end_turn", "tool_use"). */
                String stopReason,
                int inputTokens,
                int outputTokens,
                List<AgentInterrupt> interrupts
) {

        public EventLoopResult {
                Objects.requireNonNull(text, "text must not be null");
                Objects.requireNonNull(stopReason, "stopReason must not be null");
                interrupts = List.copyOf(Objects.requireNonNull(interrupts, "interrupts must not be null"));
        }

        public EventLoopResult(String text, String stopReason, int inputTokens, int outputTokens) {
                this(text, stopReason, inputTokens, outputTokens, List.of());
        }

        public boolean interrupted() {
                return !interrupts.isEmpty();
        }
}
