package com.mahitotsu.arachne.samples.delivery.menuservice.infrastructure;

import static com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuTypes.*;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.mahitotsu.arachne.samples.delivery.menuservice.config.MenuServiceProperties;

@Component
public class RegistryServiceEndpointResolver implements ServiceEndpointResolver {

    private final RestClient registryRestClient;
    private final ConcurrentMap<String, String> cachedBaseUrls = new ConcurrentHashMap<>();

    RegistryServiceEndpointResolver(
            RestClient.Builder restClientBuilder,
            MenuServiceProperties properties) {
        String registryBaseUrl = properties.getRegistry().getBaseUrl();
        this.registryRestClient = registryBaseUrl.isBlank() ? null : restClientBuilder.baseUrl(registryBaseUrl).build();
    }

    @Override
    public String resolveUrl(String serviceName, String fallbackBaseUrl, String requestPath) {
        return joinUrl(resolveBaseUrl(serviceName, fallbackBaseUrl), requestPath);
    }

    public void clearCache() {
        cachedBaseUrls.clear();
    }

    private String resolveBaseUrl(String serviceName, String fallbackBaseUrl) {
        if (StringUtils.hasText(serviceName)) {
            String cachedBaseUrl = cachedBaseUrls.get(serviceName);
            if (StringUtils.hasText(cachedBaseUrl)) {
                return cachedBaseUrl;
            }
            String discoveredBaseUrl = discoverBaseUrl(serviceName);
            if (StringUtils.hasText(discoveredBaseUrl)) {
                cachedBaseUrls.put(serviceName, discoveredBaseUrl);
                return discoveredBaseUrl;
            }
        }
        return fallbackBaseUrl;
    }

    private String discoverBaseUrl(String serviceName) {
        if (registryRestClient == null || !StringUtils.hasText(serviceName)) {
            return "";
        }
        try {
            RegistryServiceDescriptorPayload[] response = registryRestClient.get()
                    .uri("/registry/services")
                    .retrieve()
                    .body(RegistryServiceDescriptorPayload[].class);
            if (response == null) {
                return "";
            }
            return List.of(response).stream()
                    .filter(Objects::nonNull)
                    .filter(service -> serviceName.equalsIgnoreCase(service.serviceName()))
                    .filter(service -> "AVAILABLE".equalsIgnoreCase(service.status()))
                    .map(RegistryServiceDescriptorPayload::endpoint)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElse("");
        } catch (Exception ignored) {
            return "";
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
}