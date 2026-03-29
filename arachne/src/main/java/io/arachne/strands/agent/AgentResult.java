package io.arachne.strands.agent;

import java.util.List;
import java.util.Objects;

import io.arachne.strands.model.ModelEvent;
import io.arachne.strands.types.Message;

/**
 * Result returned from {@link Agent#run(String)}.
 */
public final class AgentResult {

        @FunctionalInterface
        interface ResumeHandler {
                AgentResult resume(List<InterruptResponse> responses);
        }

        private final String text;
        private final List<Message> messages;
        private final Object stopReason;
        private final Metrics metrics;
        private final List<AgentInterrupt> interrupts;
        private final ResumeHandler resumeHandler;

        public record Metrics(ModelEvent.Usage usage) {

                public static final Metrics EMPTY = new Metrics(ModelEvent.ZERO_USAGE);

                public Metrics {
                        Objects.requireNonNull(usage, "usage must not be null");
                }
        }

        public AgentResult(String text, List<Message> messages, Object stopReason) {
                this(text, messages, stopReason, Metrics.EMPTY, List.of(), null);
        }

        public AgentResult(String text, List<Message> messages, Object stopReason, Metrics metrics) {
                this(text, messages, stopReason, metrics, List.of(), null);
        }

        AgentResult(
                        String text,
                        List<Message> messages,
                        Object stopReason,
                        Metrics metrics,
                        List<AgentInterrupt> interrupts,
                        ResumeHandler resumeHandler) {
                this.text = Objects.requireNonNull(text, "text must not be null");
                this.messages = List.copyOf(Objects.requireNonNull(messages, "messages must not be null"));
                this.stopReason = stopReason;
                this.metrics = metrics == null ? Metrics.EMPTY : metrics;
                this.interrupts = List.copyOf(Objects.requireNonNull(interrupts, "interrupts must not be null"));
                this.resumeHandler = resumeHandler;
        }

        public String text() {
                return text;
        }

        public List<Message> messages() {
                return messages;
        }

        public Object stopReason() {
                return stopReason;
        }

        public Metrics metrics() {
                return metrics;
        }

        public List<AgentInterrupt> interrupts() {
                return interrupts;
        }

        public boolean interrupted() {
                return !interrupts.isEmpty();
        }

        public AgentResult resume(InterruptResponse... responses) {
                return resume(List.of(responses));
        }

        public AgentResult resume(List<InterruptResponse> responses) {
                if (resumeHandler == null || interrupts.isEmpty()) {
                        throw new IllegalStateException("This result does not have any pending interrupts to resume.");
                }
                return resumeHandler.resume(List.copyOf(responses));
        }
}
