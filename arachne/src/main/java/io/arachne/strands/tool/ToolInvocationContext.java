package io.arachne.strands.tool;

import java.util.Objects;

import io.arachne.strands.agent.AgentState;

/**
 * Logical metadata for a single tool invocation.
 *
 * <p>This contract is intentionally limited to tool-call metadata and agent state.
 * It does not model thread-local or framework execution-context propagation.
 */
public record ToolInvocationContext(
        String toolName,
        String toolUseId,
        Object input,
        AgentState state) {

    public ToolInvocationContext {
        Objects.requireNonNull(toolName, "toolName must not be null");
        Objects.requireNonNull(state, "state must not be null");
    }

    public static ToolInvocationContext direct(String toolName, Object input) {
        return new ToolInvocationContext(toolName, null, input, new AgentState());
    }
}
