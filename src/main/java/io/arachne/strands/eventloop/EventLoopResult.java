package io.arachne.strands.eventloop;

/**
 * Value returned from a completed event-loop run.
 */
public record EventLoopResult(
        /** Concatenated text from all TextDelta events in the final assistant turn. */
        String text,
        /** Raw stop reason string as returned by the model (e.g. "end_turn", "tool_use"). */
        String stopReason,
        int inputTokens,
        int outputTokens
) {}
