package com.mahitotsu.arachne.strands.hooks;

import java.util.List;
import java.util.Objects;

import com.mahitotsu.arachne.strands.agent.AgentState;
import com.mahitotsu.arachne.strands.model.ToolSelection;
import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.types.Message;

public final class BeforeModelCallEvent {

    private final List<Message> messages;
    private final List<ToolSpec> toolSpecs;
    private String systemPrompt;
    private ToolSelection toolSelection;
    private final AgentState state;

    public BeforeModelCallEvent(
            List<Message> messages,
            List<ToolSpec> toolSpecs,
            String systemPrompt,
            ToolSelection toolSelection,
            AgentState state) {
        this.messages = Objects.requireNonNull(messages, "messages must not be null");
        this.toolSpecs = List.copyOf(Objects.requireNonNull(toolSpecs, "toolSpecs must not be null"));
        this.systemPrompt = systemPrompt;
        this.toolSelection = toolSelection;
        this.state = Objects.requireNonNull(state, "state must not be null");
    }

    public List<Message> messages() {
        return messages;
    }

    public List<ToolSpec> toolSpecs() {
        return toolSpecs;
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public ToolSelection toolSelection() {
        return toolSelection;
    }

    public void setToolSelection(ToolSelection toolSelection) {
        this.toolSelection = toolSelection;
    }

    public AgentState state() {
        return state;
    }
}