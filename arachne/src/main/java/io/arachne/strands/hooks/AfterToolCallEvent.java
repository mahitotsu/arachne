package io.arachne.strands.hooks;

import java.util.Objects;

import io.arachne.strands.agent.AgentState;
import io.arachne.strands.tool.ToolResult;

public final class AfterToolCallEvent {

    private final String toolName;
    private final String toolUseId;
    private ToolResult result;
    private final AgentState state;

    public AfterToolCallEvent(String toolName, String toolUseId, ToolResult result, AgentState state) {
        this.toolName = Objects.requireNonNull(toolName, "toolName must not be null");
        this.toolUseId = Objects.requireNonNull(toolUseId, "toolUseId must not be null");
        this.result = Objects.requireNonNull(result, "result must not be null");
        this.state = Objects.requireNonNull(state, "state must not be null");
    }

    public String toolName() {
        return toolName;
    }

    public String toolUseId() {
        return toolUseId;
    }

    public ToolResult result() {
        return result;
    }

    public void setResult(ToolResult result) {
        this.result = Objects.requireNonNull(result, "result must not be null");
    }

    public AgentState state() {
        return state;
    }
}