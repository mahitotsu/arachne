package com.mahitotsu.arachne.samples.delivery.registryservice.domain;

import java.util.List;

public final class RegistryTypes {

    private RegistryTypes() {
    }

    public record RegistryRegistration(
            String serviceName,
            String endpoint,
            String capability,
            String agentName,
            String systemPrompt,
            List<SkillPayload> skills,
            List<SkillPayload> tools,
            String requestMethod,
            String requestPath,
            String healthEndpoint,
            AvailabilityStatus status) {
    }

    public record SkillPayload(String name, String content) {
    }

    public record RegistryDiscoverRequest(String query, Boolean availableOnly) {
    }

    public record RegistryDiscoverResponse(String service, String agent, String summary, List<RegistryServiceDescriptor> matches) {
    }

    public record RegistryServiceDescriptor(
            String serviceName,
            String endpoint,
            String capability,
            String agentName,
            String systemPrompt,
            List<SkillPayload> skills,
            List<SkillPayload> tools,
            String requestMethod,
            String requestPath,
            AvailabilityStatus status) {
    }

    public record RegistryHealthResponse(List<RegistryHealthEntry> services) {
    }

    public record RegistryHealthEntry(String serviceName, AvailabilityStatus status, String healthEndpoint) {
    }

    public record RankedDescriptor(RegistryServiceDescriptor descriptor, int score) {
    }

    public enum AvailabilityStatus {
        AVAILABLE,
        NOT_AVAILABLE
    }
}