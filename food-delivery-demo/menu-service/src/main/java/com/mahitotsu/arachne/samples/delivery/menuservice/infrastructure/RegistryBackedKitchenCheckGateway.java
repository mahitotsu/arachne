package com.mahitotsu.arachne.samples.delivery.menuservice.infrastructure;

import static com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuTypes.*;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.mahitotsu.arachne.samples.delivery.menuservice.config.MenuServiceProperties;

@Component
public class RegistryBackedKitchenCheckGateway implements KitchenCheckGateway {

    private static final String DEFAULT_KITCHEN_SERVICE_NAME = "kitchen-service";

    private final RestClient restClient;
    private final ServiceEndpointResolver endpointResolver;
    private final DownstreamObservationSupport observationSupport;
    private final String kitchenServiceName;
    private final String fallbackBaseUrl;

    RegistryBackedKitchenCheckGateway(
            RestClient.Builder restClientBuilder,
            ServiceEndpointResolver endpointResolver,
            DownstreamObservationSupport observationSupport,
            MenuServiceProperties properties) {
        this.restClient = restClientBuilder.build();
        this.endpointResolver = endpointResolver;
        this.observationSupport = observationSupport;
        this.kitchenServiceName = properties.getDownstream().getKitchen().getServiceName();
        String configuredBaseUrl = properties.getDownstream().getKitchen().getBaseUrl();
        this.fallbackBaseUrl = configuredBaseUrl.isBlank()
                ? "http://" + kitchenServiceName + ":8080"
            : configuredBaseUrl;
    }

    @Override
    public KitchenCheckResponse check(KitchenCheckRequest request, String accessToken) {
        return observationSupport.observe("delivery.menu.downstream", kitchenServiceName, "check", () -> restClient.post()
            .uri(endpointResolver.resolveUrl(kitchenServiceName, fallbackBaseUrl, "/internal/kitchen/check"))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(KitchenCheckResponse.class));
    }
}