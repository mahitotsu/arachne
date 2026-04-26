package com.mahitotsu.arachne.samples.delivery.orderservice;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class RegistryBackedDeliveryGateway implements DeliveryGateway {

    private static final String DELIVERY_CAPABILITY_QUERY = "配送 ETA";

    private final RestClient restClient;
    private final ServiceEndpointResolver endpointResolver;
    private final String fallbackBaseUrl;

    RegistryBackedDeliveryGateway(
            RestClient.Builder restClientBuilder,
            ServiceEndpointResolver endpointResolver,
            @Value("${DELIVERY_SERVICE_BASE_URL:}") String fallbackBaseUrl) {
        this.restClient = restClientBuilder.build();
        this.endpointResolver = endpointResolver;
        this.fallbackBaseUrl = fallbackBaseUrl;
    }

    @Override
    public DeliveryQuoteResponse quote(DeliveryQuoteRequest request, String accessToken) {
        return Objects.requireNonNull(restClient.post()
                .uri(endpointResolver.resolveUrl(DELIVERY_CAPABILITY_QUERY, fallbackBaseUrl, "/internal/delivery/quote"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(DeliveryQuoteResponse.class));
    }
}