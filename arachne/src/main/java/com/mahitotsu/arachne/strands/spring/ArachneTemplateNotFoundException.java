package com.mahitotsu.arachne.strands.spring;

/**
 * Raised when a configured rendering template resource cannot be resolved.
 */
public class ArachneTemplateNotFoundException extends ArachneTemplateRenderingException {

    public ArachneTemplateNotFoundException(String templateLocation) {
        super("Template resource not found: " + templateLocation);
    }
}