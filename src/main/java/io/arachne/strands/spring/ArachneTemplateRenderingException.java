package io.arachne.strands.spring;

/**
 * Base runtime exception for Spring-managed structured-output rendering failures.
 */
public class ArachneTemplateRenderingException extends RuntimeException {

    public ArachneTemplateRenderingException(String message) {
        super(message);
    }

    public ArachneTemplateRenderingException(String message, Throwable cause) {
        super(message, cause);
    }
}