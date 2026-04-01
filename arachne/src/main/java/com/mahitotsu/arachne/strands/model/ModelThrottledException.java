package com.mahitotsu.arachne.strands.model;

/**
 * Raised when the model provider throttles the current request.
 */
public class ModelThrottledException extends ModelRetryableException {

    public ModelThrottledException(String message) {
        super(message);
    }

    public ModelThrottledException(String message, Throwable cause) {
        super(message, cause);
    }
}