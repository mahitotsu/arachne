package com.mahitotsu.arachne.strands.hooks;

import java.util.List;
import java.util.Objects;

import com.mahitotsu.arachne.strands.agent.AgentState;
import com.mahitotsu.arachne.strands.types.Message;

public final class BeforeInvocationEvent {

    private String prompt;
    private final List<Message> messages;
    private final AgentState state;

    public BeforeInvocationEvent(String prompt, List<Message> messages, AgentState state) {
        this.prompt = Objects.requireNonNull(prompt, "prompt must not be null");
        this.messages = Objects.requireNonNull(messages, "messages must not be null");
        this.state = Objects.requireNonNull(state, "state must not be null");
    }

    public String prompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = Objects.requireNonNull(prompt, "prompt must not be null");
    }

    public List<Message> messages() {
        return messages;
    }

    public AgentState state() {
        return state;
    }
}