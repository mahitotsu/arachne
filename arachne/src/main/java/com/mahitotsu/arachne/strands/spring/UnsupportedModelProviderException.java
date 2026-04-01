package com.mahitotsu.arachne.strands.spring;

/**
 * Raised when a configured provider is outside the currently supported model set.
 */
public class UnsupportedModelProviderException extends AgentConfigurationException {

    public UnsupportedModelProviderException(String provider) {
        super("Unsupported model provider: " + provider + ". Arachne currently supports bedrock only.");
    }
}