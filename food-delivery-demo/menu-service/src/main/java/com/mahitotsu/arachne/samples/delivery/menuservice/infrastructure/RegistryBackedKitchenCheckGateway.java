package com.mahitotsu.arachne.samples.delivery.menuservice.infrastructure;

import static com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuTypes.*;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.mahitotsu.arachne.samples.delivery.menuservice.config.MenuServiceProperties;

@Component
public class RegistryBackedKitchenCheckGateway implements KitchenCheckGateway {

    private static final String OBSERVATION_TARGET = "kitchen-service";

    private final RestClient restClient;
    private final ServiceEndpointResolver endpointResolver;
    private final DownstreamObservationSupport observationSupport;
    private final String kitchenCapabilityQuery;

    RegistryBackedKitchenCheckGateway(
            RestClient.Builder restClientBuilder,
            ServiceEndpointResolver endpointResolver,
            DownstreamObservationSupport observationSupport,
            MenuServiceProperties properties) {
        this.restClient = restClientBuilder.build();
        this.endpointResolver = endpointResolver;
        this.observationSupport = observationSupport;
        this.kitchenCapabilityQuery = properties.getDownstream().getKitchen().getCapabilityQuery();
    }

    @Override
    public KitchenCheckResponse check(KitchenCheckRequest request, String accessToken) {
        return observationSupport.observe("delivery.menu.downstream", OBSERVATION_TARGET, "check", () -> restClient.post()
            .uri(endpointResolver.resolveUrl(kitchenCapabilityQuery, "/internal/kitchen/check"))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(KitchenCheckResponse.class));
    }
}