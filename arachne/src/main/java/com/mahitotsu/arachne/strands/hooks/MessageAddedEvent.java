package com.mahitotsu.arachne.strands.hooks;

import java.util.List;
import java.util.Objects;

import com.mahitotsu.arachne.strands.agent.AgentState;
import com.mahitotsu.arachne.strands.types.Message;

public final class MessageAddedEvent {

    private final Message message;
    private final List<Message> messages;
    private final AgentState state;

    public MessageAddedEvent(Message message, List<Message> messages, AgentState state) {
        this.message = Objects.requireNonNull(message, "message must not be null");
        this.messages = Objects.requireNonNull(messages, "messages must not be null");
        this.state = Objects.requireNonNull(state, "state must not be null");
    }

    public Message message() {
        return message;
    }

    public List<Message> messages() {
        return messages;
    }

    public AgentState state() {
        return state;
    }
}