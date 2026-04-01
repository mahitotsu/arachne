package com.mahitotsu.arachne.strands.tool;

/**
 * Raised when runtime validation of tool input fails.
 */
public class ToolValidationException extends RuntimeException {

    public ToolValidationException(String message) {
        super(message);
    }
}