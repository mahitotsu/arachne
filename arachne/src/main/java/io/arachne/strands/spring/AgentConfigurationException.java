package io.arachne.strands.spring;

/**
 * Base runtime exception for agent configuration resolution failures.
 */
public class AgentConfigurationException extends RuntimeException {

    public AgentConfigurationException(String message) {
        super(message);
    }

    public AgentConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}