package com.mahitotsu.arachne.samples.delivery.orderservice.infrastructure;

import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.mahitotsu.arachne.samples.delivery.orderservice.config.OrderRegistryProperties;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.MenuSuggestionRequest;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.MenuSuggestionResponse;

@Component
public class RegistryBackedMenuGateway implements MenuGateway {

    private static final String DEFAULT_MENU_SERVICE_NAME = "menu-service";

    private final RestClient restClient;
    private final ServiceEndpointResolver endpointResolver;
    private final String menuServiceName;
    private final String fallbackBaseUrl;

    RegistryBackedMenuGateway(
            RestClient.Builder restClientBuilder,
            ServiceEndpointResolver endpointResolver,
            OrderRegistryProperties properties) {
        this.restClient = restClientBuilder.build();
        this.endpointResolver = endpointResolver;
        this.menuServiceName = properties.getDownstream().getMenu().getServiceName();
        String configuredBaseUrl = properties.getDownstream().getMenu().getBaseUrl();
        this.fallbackBaseUrl = configuredBaseUrl.isBlank()
                ? "http://" + menuServiceName + ":8080"
            : configuredBaseUrl;
    }

    @Override
    public MenuSuggestionResponse suggest(MenuSuggestionRequest request, String accessToken) {
        return Objects.requireNonNull(restClient.post()
                .uri(endpointResolver.resolveUrl(menuServiceName, fallbackBaseUrl, "/internal/menu/suggest"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(MenuSuggestionResponse.class));
    }
}