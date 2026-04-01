package com.mahitotsu.arachne.strands.model;

/**
 * Base runtime exception for model invocation failures.
 */
public class ModelException extends RuntimeException {

    public ModelException(String message) {
        super(message);
    }

    public ModelException(String message, Throwable cause) {
        super(message, cause);
    }
}