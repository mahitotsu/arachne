package com.mahitotsu.arachne.samples.delivery.menuservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class RegistryBackedKitchenCheckGateway implements KitchenCheckGateway {

    private static final String DEFAULT_KITCHEN_SERVICE_NAME = "kitchen-service";

    private final RestClient restClient;
    private final ServiceEndpointResolver endpointResolver;
    private final String kitchenServiceName;
    private final String fallbackBaseUrl;

    RegistryBackedKitchenCheckGateway(
            RestClient.Builder restClientBuilder,
            ServiceEndpointResolver endpointResolver,
            @Value("${KITCHEN_SERVICE_NAME:" + DEFAULT_KITCHEN_SERVICE_NAME + "}") String kitchenServiceName,
            @Value("${KITCHEN_SERVICE_BASE_URL:}") String fallbackBaseUrl) {
        this.restClient = restClientBuilder.build();
        this.endpointResolver = endpointResolver;
        this.kitchenServiceName = kitchenServiceName;
        this.fallbackBaseUrl = fallbackBaseUrl.isBlank()
                ? "http://" + kitchenServiceName + ":8080"
                : fallbackBaseUrl;
    }

    @Override
    public KitchenCheckResponse check(KitchenCheckRequest request, String accessToken) {
        return restClient.post()
                .uri(endpointResolver.resolveUrl(kitchenServiceName, fallbackBaseUrl, "/internal/kitchen/check"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(KitchenCheckResponse.class);
    }
}