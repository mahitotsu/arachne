package io.arachne.strands.model;

/**
 * Provider-independent tool selection hint passed to the model.
 */
public record ToolSelection(String toolName) {

    public ToolSelection {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName must not be blank");
        }
    }

    public static ToolSelection force(String toolName) {
        return new ToolSelection(toolName);
    }
}