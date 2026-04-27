package com.mahitotsu.arachne.samples.delivery.supportservice;

import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class SupportStatusGateway {

    private final RestClient restClient;

    SupportStatusGateway(
            RestClient.Builder restClientBuilder,
            @Value("${DELIVERY_REGISTRY_BASE_URL:}") String registryBaseUrl) {
        this.restClient = registryBaseUrl.isBlank() ? null : restClientBuilder.baseUrl(registryBaseUrl).build();
    }

    List<ServiceHealthSummary> currentStatuses() {
        if (restClient == null) {
            return fallback();
        }
        try {
            RegistryHealthResponsePayload response = restClient.get()
                    .uri("/registry/health")
                    .retrieve()
                    .body(RegistryHealthResponsePayload.class);
            if (response == null || response.services() == null) {
                return fallback();
            }
            return response.services().stream()
                    .filter(Objects::nonNull)
                    .map(service -> new ServiceHealthSummary(
                            service.serviceName(),
                            service.status(),
                            Objects.requireNonNullElse(service.healthEndpoint(), "")))
                    .toList();
        } catch (Exception ignored) {
            return fallback();
        }
    }

    private List<ServiceHealthSummary> fallback() {
        return List.of(
                new ServiceHealthSummary("support-service", "AVAILABLE", ""),
                new ServiceHealthSummary("registry-service", "UNKNOWN", ""));
    }
}