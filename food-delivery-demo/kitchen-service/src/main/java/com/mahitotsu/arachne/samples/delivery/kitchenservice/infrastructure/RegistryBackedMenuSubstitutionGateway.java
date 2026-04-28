package com.mahitotsu.arachne.samples.delivery.kitchenservice.infrastructure;

import static com.mahitotsu.arachne.samples.delivery.kitchenservice.domain.KitchenTypes.*;

import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.mahitotsu.arachne.samples.delivery.kitchenservice.config.KitchenServiceProperties;

@Component
public class RegistryBackedMenuSubstitutionGateway implements MenuSubstitutionGateway {

    private static final String DEFAULT_MENU_SERVICE_NAME = "menu-service";

    private final RestClient restClient;
    private final ServiceEndpointResolver endpointResolver;
    private final String menuServiceName;
    private final String fallbackBaseUrl;

    RegistryBackedMenuSubstitutionGateway(
            RestClient.Builder restClientBuilder,
            ServiceEndpointResolver endpointResolver,
            KitchenServiceProperties properties) {
        this.restClient = restClientBuilder.build();
        this.endpointResolver = endpointResolver;
        this.menuServiceName = properties.getDownstream().getMenu().getServiceName();
        String configuredBaseUrl = properties.getDownstream().getMenu().getBaseUrl();
        this.fallbackBaseUrl = configuredBaseUrl.isBlank()
                ? "http://" + menuServiceName + ":8080"
            : configuredBaseUrl;
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