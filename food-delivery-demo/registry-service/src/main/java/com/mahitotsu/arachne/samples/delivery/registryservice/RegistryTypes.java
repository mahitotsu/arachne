package com.mahitotsu.arachne.samples.delivery.registryservice;

import java.util.List;

record RegistryRegistration(
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

record SkillPayload(String name, String content) {
}

record RegistryDiscoverRequest(String query, Boolean availableOnly) {
}

record RegistryDiscoverResponse(String service, String agent, String summary, List<RegistryServiceDescriptor> matches) {
}

record RegistryServiceDescriptor(
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

record RegistryHealthResponse(List<RegistryHealthEntry> services) {
}

record RegistryHealthEntry(String serviceName, AvailabilityStatus status, String healthEndpoint) {
}

record RankedDescriptor(RegistryServiceDescriptor descriptor, int score) {
}

enum AvailabilityStatus {
    AVAILABLE,
    NOT_AVAILABLE
}