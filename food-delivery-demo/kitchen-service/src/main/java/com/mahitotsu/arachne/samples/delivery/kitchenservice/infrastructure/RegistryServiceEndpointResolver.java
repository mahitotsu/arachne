package com.mahitotsu.arachne.samples.delivery.kitchenservice.infrastructure;

import static com.mahitotsu.arachne.samples.delivery.kitchenservice.domain.KitchenTypes.*;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class RegistryServiceEndpointResolver implements ServiceEndpointResolver {

    private final RestClient registryRestClient;
    private final ConcurrentMap<String, RegistryEndpointMetadata> cachedEndpoints = new ConcurrentHashMap<>();

    RegistryServiceEndpointResolver(
            RestClient.Builder restClientBuilder,
            @Value("${DELIVERY_REGISTRY_BASE_URL:}") String registryBaseUrl) {
        this.registryRestClient = registryBaseUrl.isBlank() ? null : restClientBuilder.baseUrl(registryBaseUrl).build();
    }

    @Override
    public String resolveUrl(String capabilityQuery, String fallbackBaseUrl, String fallbackRequestPath) {
        RegistryEndpointMetadata endpoint = resolveEndpoint(capabilityQuery);
        if (endpoint != null && StringUtils.hasText(endpoint.endpoint())) {
            return joinUrl(endpoint.endpoint(), endpoint.requestPathOr(fallbackRequestPath));
        }
        return joinUrl(fallbackBaseUrl, fallbackRequestPath);
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
            RegistryServiceDescriptorPayload[] response = registryRestClient.get()
                    .uri("/registry/services")
                    .retrieve()
                    .body(RegistryServiceDescriptorPayload[].class);
            if (response == null) {
                return null;
            }
            List<String> queryTokens = tokenize(capabilityQuery);
            return List.of(response).stream()
                    .filter(Objects::nonNull)
                    .filter(service -> "AVAILABLE".equalsIgnoreCase(service.status()))
                    .filter(service -> matchesAllTokens(service, queryTokens))
                    .max(Comparator.comparingInt(service -> score(service, queryTokens)))
                    .map(service -> new RegistryEndpointMetadata(service.endpoint(), service.requestPath()))
                    .filter(service -> StringUtils.hasText(service.endpoint()))
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private int score(RegistryServiceDescriptorPayload descriptor, List<String> queryTokens) {
        if (queryTokens.isEmpty()) {
            return 0;
        }
        String searchable = searchableContent(descriptor);
        int score = 0;
        for (String token : queryTokens) {
            if (searchable.contains(token)) {
                score++;
            }
        }
        return score;
    }

    private boolean matchesAllTokens(RegistryServiceDescriptorPayload descriptor, List<String> queryTokens) {
        return !queryTokens.isEmpty() && score(descriptor, queryTokens) == queryTokens.size();
    }

    private String searchableContent(RegistryServiceDescriptorPayload descriptor) {
        return normalize(String.join(" ",
                Objects.requireNonNullElse(descriptor.serviceName(), ""),
                Objects.requireNonNullElse(descriptor.capability(), ""),
                Objects.requireNonNullElse(descriptor.agentName(), ""),
                Objects.requireNonNullElse(descriptor.requestPath(), "")));
    }

    private List<String> tokenize(String query) {
        return Arrays.stream(normalize(query).split("\\s+"))
                .filter(StringUtils::hasText)
                .toList();
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}、。・]+", " ").trim();
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