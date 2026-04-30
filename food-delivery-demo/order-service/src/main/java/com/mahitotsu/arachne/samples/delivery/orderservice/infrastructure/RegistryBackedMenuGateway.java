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

    private static final String MENU_TARGET = "menu-service";

    private final RestClient restClient;
    private final ServiceEndpointResolver endpointResolver;
    private final DownstreamObservationSupport observationSupport;
    private final String menuCapabilityQuery;

    RegistryBackedMenuGateway(
            RestClient.Builder restClientBuilder,
            ServiceEndpointResolver endpointResolver,
            DownstreamObservationSupport observationSupport,
            OrderRegistryProperties properties) {
        this.restClient = restClientBuilder.build();
        this.endpointResolver = endpointResolver;
        this.observationSupport = observationSupport;
        this.menuCapabilityQuery = properties.getDownstream().getMenu().getCapabilityQuery();
    }

    @Override
    public MenuSuggestionResponse suggest(MenuSuggestionRequest request, String accessToken) {
        return observationSupport.observe(
                "delivery.order.downstream",
                request.sessionId(),
                MENU_TARGET,
                "suggest",
                "query=" + request.query(),
                () ->
            Objects.requireNonNull(restClient.post()
                .uri(endpointResolver.resolveUrl(menuCapabilityQuery, "/internal/menu/suggest"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(MenuSuggestionResponse.class)));
    }
}