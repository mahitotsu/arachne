package com.mahitotsu.arachne.strands.hooks;

import java.util.List;
import java.util.Objects;

import com.mahitotsu.arachne.strands.agent.AgentState;
import com.mahitotsu.arachne.strands.model.ModelEvent;
import com.mahitotsu.arachne.strands.types.Message;

public final class AfterInvocationEvent {

    private String text;
    private final List<Message> messages;
    private String stopReason;
    private final ModelEvent.Usage usage;
    private final AgentState state;

    public AfterInvocationEvent(
            String text,
            List<Message> messages,
            String stopReason,
            ModelEvent.Usage usage,
            AgentState state) {
        this.text = Objects.requireNonNull(text, "text must not be null");
        this.messages = Objects.requireNonNull(messages, "messages must not be null");
        this.stopReason = Objects.requireNonNull(stopReason, "stopReason must not be null");
        this.usage = Objects.requireNonNull(usage, "usage must not be null");
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

    public ModelEvent.Usage usage() {
        return usage;
    }

    public AgentState state() {
        return state;
    }
}