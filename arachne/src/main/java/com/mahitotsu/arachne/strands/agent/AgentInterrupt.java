package com.mahitotsu.arachne.strands.agent;

import java.util.Objects;

/**
 * Represents a paused tool-use step awaiting an external response.
 */
public record AgentInterrupt(
        String id,
        String name,
        Object reason,
        String toolUseId,
        String toolName,
        Object input,
        Object response
) {

    public AgentInterrupt {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(toolUseId, "toolUseId must not be null");
        Objects.requireNonNull(toolName, "toolName must not be null");
    }

    public AgentInterrupt withResponse(Object response) {
        return new AgentInterrupt(id, name, reason, toolUseId, toolName, input, response);
    }
}