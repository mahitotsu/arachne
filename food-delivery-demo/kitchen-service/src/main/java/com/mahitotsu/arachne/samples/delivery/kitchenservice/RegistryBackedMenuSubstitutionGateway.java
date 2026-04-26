package com.mahitotsu.arachne.samples.delivery.kitchenservice;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class RegistryBackedMenuSubstitutionGateway implements MenuSubstitutionGateway {

    private final RestClient restClient;
    private final ServiceEndpointResolver endpointResolver;
    private final String menuServiceName;
    private final String fallbackBaseUrl;

    RegistryBackedMenuSubstitutionGateway(
            RestClient.Builder restClientBuilder,
            ServiceEndpointResolver endpointResolver,
            @Value("${MENU_SERVICE_NAME:menu-service}") String menuServiceName,
            @Value("${MENU_SERVICE_BASE_URL:}") String fallbackBaseUrl) {
        this.restClient = restClientBuilder.build();
        this.endpointResolver = endpointResolver;
        this.menuServiceName = menuServiceName;
        this.fallbackBaseUrl = fallbackBaseUrl;
    }

    @Override
    public MenuSubstitutionResponse suggestSubstitutes(MenuSubstitutionRequest request, String accessToken) {
        return Objects.requireNonNull(restClient.post()
                .uri(endpointResolver.resolveUrl(menuServiceName, fallbackBaseUrl, "/internal/menu/substitutes"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(MenuSubstitutionResponse.class));
    }
}