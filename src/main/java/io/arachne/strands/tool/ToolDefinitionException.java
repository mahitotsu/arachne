package io.arachne.strands.tool;

/**
 * Raised when a tool definition cannot be exposed safely to the model.
 */
public class ToolDefinitionException extends RuntimeException {

    public ToolDefinitionException(String message) {
        super(message);
    }

    public ToolDefinitionException(String message, Throwable cause) {
        super(message, cause);
    }
}