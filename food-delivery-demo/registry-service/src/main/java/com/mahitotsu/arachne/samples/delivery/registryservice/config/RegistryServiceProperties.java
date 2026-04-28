package com.mahitotsu.arachne.samples.delivery.registryservice.config;

import java.net.URI;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "delivery.registry")
@Validated
public class RegistryServiceProperties {

    private boolean seedDefaults = true;

    @NotBlank
    private String endpoint = "http://registry-service:8080";

    public boolean isSeedDefaults() {
        return seedDefaults;
    }

    public void setSeedDefaults(boolean seedDefaults) {
        this.seedDefaults = seedDefaults;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = normalize(endpoint);
    }

    @AssertTrue(message = "delivery.registry.endpoint must be an absolute http(s) URL")
    public boolean isEndpointValid() {
        return isHttpUrl(endpoint);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isHttpUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            return uri.isAbsolute() && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}