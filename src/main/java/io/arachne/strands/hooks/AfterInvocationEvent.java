package io.arachne.strands.hooks;

import java.util.List;
import java.util.Objects;

import io.arachne.strands.agent.AgentState;
import io.arachne.strands.types.Message;

public final class AfterInvocationEvent {

    private String text;
    private final List<Message> messages;
    private String stopReason;
    private final AgentState state;

    public AfterInvocationEvent(String text, List<Message> messages, String stopReason, AgentState state) {
        this.text = Objects.requireNonNull(text, "text must not be null");
        this.messages = Objects.requireNonNull(messages, "messages must not be null");
        this.stopReason = Objects.requireNonNull(stopReason, "stopReason must not be null");
        this.state = Objects.requireNonNull(state, "state must not be null");
    }

    public String text() {
        return text;
    }

    public void setText(String text) {
        this.text = Objects.requireNonNull(text, "text must not be null");
    }

    public List<Message> messages() {
        return messages;
    }

    public String stopReason() {
        return stopReason;
    }

    public void setStopReason(String stopReason) {
        this.stopReason = Objects.requireNonNull(stopReason, "stopReason must not be null");
    }

    public AgentState state() {
        return state;
    }
}