package com.mahitotsu.arachne.samples.delivery.registryservice.domain;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public final class RegistryTypes {

    private RegistryTypes() {
    }

    @Schema(description = "registry-service に送る service 登録 payload です。")
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

        @Schema(description = "自然言語の registry discovery 要求です。")
        public record RegistryDiscoverRequest(
            @Schema(description = "欲しい capability を説明する自然言語 query。", example = "外部ETAを提供するサービスは？") String query,
            @Schema(description = "現在利用可能な service のみに絞り込むかどうか。") Boolean availableOnly) {
    }

    public record RegistryDiscoveryDecision(List<String> selectedServiceNames, String summary) {
    }

    @Schema(description = "agent 要約と一致した service descriptor を含む registry discovery 応答です。")
    public record RegistryDiscoverResponse(String service, String agent, String summary, List<RegistryServiceDescriptor> matches) {
    }

    @Schema(description = "registry-service が返す登録済み service descriptor です。")
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

    @Schema(description = "登録済み service 全体の集約 health 応答です。")
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