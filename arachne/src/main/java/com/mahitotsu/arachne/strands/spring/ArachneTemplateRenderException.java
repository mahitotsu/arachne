package com.mahitotsu.arachne.strands.spring;

/**
 * Raised when an existing rendering template cannot be rendered with the supplied typed model.
 */
public class ArachneTemplateRenderException extends ArachneTemplateRenderingException {

    public ArachneTemplateRenderException(String message) {
        super(message);
    }

    public ArachneTemplateRenderException(String message, Throwable cause) {
        super(message, cause);
    }
}