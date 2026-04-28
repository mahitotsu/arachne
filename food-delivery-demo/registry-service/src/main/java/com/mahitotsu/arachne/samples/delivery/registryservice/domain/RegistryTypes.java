package com.mahitotsu.arachne.samples.delivery.registryservice.domain;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public final class RegistryTypes {

    private RegistryTypes() {
    }

    @Schema(description = "Service registration payload published to registry-service.")
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

        @Schema(description = "Natural-language registry discovery request.")
        public record RegistryDiscoverRequest(
            @Schema(description = "Natural-language query describing the desired capability.", example = "外部ETAを提供するサービスは？") String query,
            @Schema(description = "Whether to filter results to currently available services only.") Boolean availableOnly) {
    }

    public record RegistryDiscoveryDecision(List<String> selectedServiceNames, String summary) {
    }

    @Schema(description = "Registry discovery response with an agent summary and matching service descriptors.")
    public record RegistryDiscoverResponse(String service, String agent, String summary, List<RegistryServiceDescriptor> matches) {
    }

    @Schema(description = "Registered service descriptor returned by registry-service.")
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

    @Schema(description = "Aggregated health response across registered services.")
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