package io.arachne.strands.model;

/**
 * Raised when a model call fails in a way that can be retried safely.
 */
public class ModelRetryableException extends ModelException {

    public ModelRetryableException(String message) {
        super(message);
    }

    public ModelRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}