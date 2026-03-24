package io.arachne.strands.model;

/**
 * A discriminated union of events that a {@link Model} emits during converse.
 *
 * <p>Sealed hierarchy allows exhaustive pattern-matching at call sites.
 */
public sealed interface ModelEvent
        permits ModelEvent.TextDelta, ModelEvent.ToolUse, ModelEvent.Metadata {

    Usage ZERO_USAGE = new Usage(0, 0, 0, 0);

    /** Incremental text token from the model. */
    record TextDelta(String delta) implements ModelEvent {}

    /** The model has decided to invoke a tool. */
    record ToolUse(String toolUseId, String name, Object input) implements ModelEvent {}

    /** Stop reason, token usage, etc. */
    record Metadata(String stopReason, Usage usage) implements ModelEvent {}

    record Usage(int inputTokens, int outputTokens, int cacheReadInputTokens, int cacheWriteInputTokens) {

        public Usage(int inputTokens, int outputTokens) {
            this(inputTokens, outputTokens, 0, 0);
        }

        public Usage plus(Usage other) {
            if (other == null) {
                return this;
            }
            return new Usage(
                    inputTokens + other.inputTokens,
                    outputTokens + other.outputTokens,
                    cacheReadInputTokens + other.cacheReadInputTokens,
                    cacheWriteInputTokens + other.cacheWriteInputTokens);
        }
    }
}
