package io.arachne.strands.hooks;

import java.util.List;
import java.util.Objects;

import io.arachne.strands.agent.AgentState;
import io.arachne.strands.types.Message;

public final class AfterModelCallEvent {

    private Message response;
    private String stopReason;
    private final List<Message> messages;
    private final AgentState state;
    private boolean retryRequested;
    private String retryGuidance;

    public AfterModelCallEvent(Message response, String stopReason, List<Message> messages, AgentState state) {
        this.response = Objects.requireNonNull(response, "response must not be null");
        this.stopReason = Objects.requireNonNull(stopReason, "stopReason must not be null");
        this.messages = Objects.requireNonNull(messages, "messages must not be null");
        this.state = Objects.requireNonNull(state, "state must not be null");
    }

    public Message response() {
        return response;
    }

    public void setResponse(Message response) {
        this.response = Objects.requireNonNull(response, "response must not be null");
    }

    public String stopReason() {
        return stopReason;
    }

    public void setStopReason(String stopReason) {
        this.stopReason = Objects.requireNonNull(stopReason, "stopReason must not be null");
    }

    public List<Message> messages() {
        return messages;
    }

    public AgentState state() {
        return state;
    }

    public boolean retryRequested() {
        return retryRequested;
    }

    public String retryGuidance() {
        return retryGuidance;
    }

    public void retryWithGuidance(String guidance) {
        this.retryRequested = true;
        this.retryGuidance = Objects.requireNonNull(guidance, "guidance must not be null");
    }
}