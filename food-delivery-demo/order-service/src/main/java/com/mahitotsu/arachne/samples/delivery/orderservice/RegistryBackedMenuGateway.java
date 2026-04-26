package com.mahitotsu.arachne.samples.delivery.orderservice;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class RegistryBackedMenuGateway implements MenuGateway {

    private static final String MENU_CAPABILITY_QUERY = "メニュー提案";

    private final RestClient restClient;
    private final ServiceEndpointResolver endpointResolver;
    private final String fallbackBaseUrl;

    RegistryBackedMenuGateway(
            RestClient.Builder restClientBuilder,
            ServiceEndpointResolver endpointResolver,
            @Value("${MENU_SERVICE_BASE_URL:}") String fallbackBaseUrl) {
        this.restClient = restClientBuilder.build();
        this.endpointResolver = endpointResolver;
        this.fallbackBaseUrl = fallbackBaseUrl;
    }

    @Override
    public MenuSuggestionResponse suggest(MenuSuggestionRequest request, String accessToken) {
        return Objects.requireNonNull(restClient.post()
                .uri(endpointResolver.resolveUrl(MENU_CAPABILITY_QUERY, fallbackBaseUrl, "/internal/menu/suggest"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(MenuSuggestionResponse.class));
    }
}