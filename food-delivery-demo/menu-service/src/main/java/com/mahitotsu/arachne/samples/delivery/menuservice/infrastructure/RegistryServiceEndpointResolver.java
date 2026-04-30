package com.mahitotsu.arachne.samples.delivery.menuservice.infrastructure;

import static com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuTypes.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.mahitotsu.arachne.samples.delivery.menuservice.config.MenuServiceProperties;

@Component
public class RegistryServiceEndpointResolver implements ServiceEndpointResolver {

    private final RestClient registryRestClient;
    private final DownstreamObservationSupport observationSupport;
    private final ConcurrentMap<String, RegistryEndpointMetadata> cachedEndpoints = new ConcurrentHashMap<>();

    RegistryServiceEndpointResolver(
            RestClient.Builder restClientBuilder,
            DownstreamObservationSupport observationSupport,
            MenuServiceProperties properties) {
        String registryBaseUrl = properties.getRegistry().getBaseUrl();
        this.registryRestClient = registryBaseUrl.isBlank() ? null : restClientBuilder.baseUrl(registryBaseUrl).build();
        this.observationSupport = observationSupport;
    }

    @Override
    public String resolveUrl(String capabilityQuery, String requestPath) {
        RegistryEndpointMetadata endpoint = resolveEndpoint(capabilityQuery);
        if (endpoint != null && StringUtils.hasText(endpoint.endpoint())) {
            return joinUrl(endpoint.endpoint(), endpoint.requestPathOr(requestPath));
        }
        return "";
    }

    public void clearCache() {
        cachedEndpoints.clear();
    }

    private RegistryEndpointMetadata resolveEndpoint(String capabilityQuery) {
        if (!StringUtils.hasText(capabilityQuery)) {
            return null;
        }
        RegistryEndpointMetadata cachedEndpoint = cachedEndpoints.get(capabilityQuery);
        if (cachedEndpoint != null && StringUtils.hasText(cachedEndpoint.endpoint())) {
            return cachedEndpoint;
        }
        RegistryEndpointMetadata discoveredEndpoint = discoverEndpoint(capabilityQuery);
        if (discoveredEndpoint != null && StringUtils.hasText(discoveredEndpoint.endpoint())) {
            cachedEndpoints.put(capabilityQuery, discoveredEndpoint);
            return discoveredEndpoint;
        }
        return null;
    }

    private RegistryEndpointMetadata discoverEndpoint(String capabilityQuery) {
        if (registryRestClient == null || !StringUtils.hasText(capabilityQuery)) {
            return null;
        }
        try {
            RegistryDiscoverResponsePayload response = observationSupport.observe(
                    "delivery.menu.registry.lookup",
                    "registry-service",
                    "resolve-endpoint",
                    () -> registryRestClient.post()
                            .uri("/registry/discover")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(new RegistryDiscoverRequestPayload(capabilityQuery, true))
                            .retrieve()
                            .body(RegistryDiscoverResponsePayload.class));
            if (response == null || response.matches() == null) {
                return null;
            }
            return response.matches().stream()
                    .filter(java.util.Objects::nonNull)
                    .filter(service -> "AVAILABLE".equalsIgnoreCase(service.status()))
                    .map(service -> new RegistryEndpointMetadata(service.endpoint(), service.requestPath()))
                    .filter(service -> StringUtils.hasText(service.endpoint()))
                    .findFirst()
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
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

    private record RegistryEndpointMetadata(String endpoint, String requestPath) {
        private String requestPathOr(String fallbackRequestPath) {
            return StringUtils.hasText(requestPath) ? requestPath : fallbackRequestPath;
        }
    }
}