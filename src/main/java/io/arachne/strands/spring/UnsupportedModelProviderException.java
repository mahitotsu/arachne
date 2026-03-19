package io.arachne.strands.spring;

/**
 * Raised when a configured provider is not supported by the current implementation phase.
 */
public class UnsupportedModelProviderException extends AgentConfigurationException {

    public UnsupportedModelProviderException(String provider) {
        super("Unsupported model provider for Phase 1: " + provider);
    }
}