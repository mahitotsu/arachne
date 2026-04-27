package com.mahitotsu.arachne.samples.delivery.supportservice.infrastructure;

import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.mahitotsu.arachne.samples.delivery.supportservice.domain.ServiceHealthSummary;

@Component
public class SupportStatusGateway {

    private final RestClient restClient;

    SupportStatusGateway(
            RestClient.Builder restClientBuilder,
            @Value("${DELIVERY_REGISTRY_BASE_URL:}") String registryBaseUrl) {
        this.restClient = registryBaseUrl.isBlank() ? null : restClientBuilder.baseUrl(registryBaseUrl).build();
    }

    public List<ServiceHealthSummary> currentStatuses() {
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

    private record RegistryHealthResponsePayload(List<RegistryHealthEntryPayload> services) {
    }

    private record RegistryHealthEntryPayload(String serviceName, String status, String healthEndpoint) {
    }
}