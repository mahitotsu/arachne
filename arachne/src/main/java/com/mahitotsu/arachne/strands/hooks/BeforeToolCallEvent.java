package com.mahitotsu.arachne.strands.hooks;

import java.util.Objects;

import com.mahitotsu.arachne.strands.agent.AgentInterrupt;
import com.mahitotsu.arachne.strands.agent.AgentState;
import com.mahitotsu.arachne.strands.tool.ToolResult;

public final class BeforeToolCallEvent {

    private String toolName;
    private final String toolUseId;
    private Object input;
    private ToolResult overrideResult;
    private AgentInterrupt interrupt;
    private final AgentState state;

    public BeforeToolCallEvent(String toolName, String toolUseId, Object input, AgentState state) {
        this.toolName = Objects.requireNonNull(toolName, "toolName must not be null");
        this.toolUseId = Objects.requireNonNull(toolUseId, "toolUseId must not be null");
        this.input = input;
        this.state = Objects.requireNonNull(state, "state must not be null");
    }

    public String toolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = Objects.requireNonNull(toolName, "toolName must not be null");
    }

    public String toolUseId() {
        return toolUseId;
    }

    public Object input() {
        return input;
    }

    public void setInput(Object input) {
        this.input = input;
    }

    public ToolResult overrideResult() {
        return overrideResult;
    }

    public void skipWith(ToolResult overrideResult) {
        if (interrupt != null) {
            throw new IllegalStateException("Cannot override a tool result after an interrupt was requested.");
        }
        this.overrideResult = Objects.requireNonNull(overrideResult, "overrideResult must not be null");
    }

    public void guide(String guidance) {
        skipWith(ToolResult.error(toolUseId, Objects.requireNonNull(guidance, "guidance must not be null")));
    }

    public AgentInterrupt interrupt() {
        return interrupt;
    }

    public void interrupt(Object reason) {
        interrupt(toolName, reason);
    }

    public void interrupt(String name, Object reason) {
        if (overrideResult != null) {
            throw new IllegalStateException("Cannot interrupt a tool call after an override result was provided.");
        }
        this.interrupt = new AgentInterrupt(
            toolUseId,
            Objects.requireNonNull(name, "name must not be null"),
            reason,
            toolUseId,
            toolName,
            input,
            null);
    }

    public AgentState state() {
        return state;
    }
}