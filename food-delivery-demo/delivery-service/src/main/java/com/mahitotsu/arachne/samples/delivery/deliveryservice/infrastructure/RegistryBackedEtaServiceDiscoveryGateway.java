package com.mahitotsu.arachne.samples.delivery.deliveryservice.infrastructure;

import static com.mahitotsu.arachne.samples.delivery.deliveryservice.domain.DeliveryTypes.*;

import java.util.List;
import java.util.Objects;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.mahitotsu.arachne.samples.delivery.deliveryservice.config.DeliveryServiceProperties;

@Component
public class RegistryBackedEtaServiceDiscoveryGateway implements EtaServiceDiscoveryGateway {

    private final RestClient restClient;

    RegistryBackedEtaServiceDiscoveryGateway(
            RestClient.Builder restClientBuilder,
            DeliveryServiceProperties properties) {
        String registryBaseUrl = properties.getRegistry().getBaseUrl();
        this.restClient = registryBaseUrl.isBlank() ? null : restClientBuilder.baseUrl(registryBaseUrl).build();
    }

    @Override
    public List<EtaServiceTarget> discoverAvailableEtaServices(String query) {
        if (restClient == null) {
            return List.of();
        }
        RegistryDiscoverResponsePayload response = restClient.post()
                .uri("/registry/discover")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegistryDiscoverRequestPayload(query, true))
                .retrieve()
                .body(RegistryDiscoverResponsePayload.class);
        if (response == null || response.matches() == null) {
            return List.of();
        }
        return response.matches().stream()
                .filter(Objects::nonNull)
                .filter(match -> "AVAILABLE".equalsIgnoreCase(match.status()))
                .map(match -> new EtaServiceTarget(match.serviceName(), joinUrl(match.endpoint(), match.requestPath())))
                .filter(target -> StringUtils.hasText(target.url()))
                .toList();
    }

    private String joinUrl(String endpoint, String requestPath) {
        if (!StringUtils.hasText(endpoint)) {
            return "";
        }
        if (!StringUtils.hasText(requestPath)) {
            return endpoint;
        }
        if (requestPath.startsWith("http://") || requestPath.startsWith("https://")) {
            return requestPath;
        }
        if (endpoint.endsWith("/") && requestPath.startsWith("/")) {
            return endpoint.substring(0, endpoint.length() - 1) + requestPath;
        }
        if (!endpoint.endsWith("/") && !requestPath.startsWith("/")) {
            return endpoint + "/" + requestPath;
        }
        return endpoint + requestPath;
    }
}