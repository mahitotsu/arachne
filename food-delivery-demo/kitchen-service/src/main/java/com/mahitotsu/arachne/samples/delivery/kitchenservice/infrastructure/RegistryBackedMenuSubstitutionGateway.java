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

    private final RestClient restClient;
    private final ServiceEndpointResolver endpointResolver;
    private final String menuCapabilityQuery;

    RegistryBackedMenuSubstitutionGateway(
            RestClient.Builder restClientBuilder,
            ServiceEndpointResolver endpointResolver,
            KitchenServiceProperties properties) {
        this.restClient = restClientBuilder.build();
        this.endpointResolver = endpointResolver;
        this.menuCapabilityQuery = properties.getDownstream().getMenu().getCapabilityQuery();
    }

    @Override
    public MenuSubstitutionResponse suggestSubstitutes(MenuSubstitutionRequest request, String accessToken) {
        return Objects.requireNonNull(restClient.post()
            .uri(endpointResolver.resolveUrl(menuCapabilityQuery, "/internal/menu/substitutes"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(MenuSubstitutionResponse.class));
    }
}