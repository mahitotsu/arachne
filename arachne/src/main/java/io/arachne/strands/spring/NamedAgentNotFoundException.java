package io.arachne.strands.spring;

/**
 * Raised when a named agent builder is requested for a configuration that does not exist.
 */
public class NamedAgentNotFoundException extends AgentConfigurationException {

    public NamedAgentNotFoundException(String agentName) {
        super("No named agent configuration found for: " + agentName);
    }
}