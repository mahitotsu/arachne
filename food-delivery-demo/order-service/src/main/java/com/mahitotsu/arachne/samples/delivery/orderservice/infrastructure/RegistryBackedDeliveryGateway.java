package com.mahitotsu.arachne.samples.delivery.orderservice.infrastructure;

import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.mahitotsu.arachne.samples.delivery.orderservice.config.OrderRegistryProperties;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.DeliveryQuoteRequest;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.DeliveryQuoteResponse;

@Component
public class RegistryBackedDeliveryGateway implements DeliveryGateway {

    private static final String DEFAULT_DELIVERY_SERVICE_NAME = "delivery-service";

    private final RestClient restClient;
    private final ServiceEndpointResolver endpointResolver;
    private final DownstreamObservationSupport observationSupport;
    private final String deliveryServiceName;
    private final String fallbackBaseUrl;

    RegistryBackedDeliveryGateway(
            RestClient.Builder restClientBuilder,
            ServiceEndpointResolver endpointResolver,
            DownstreamObservationSupport observationSupport,
            OrderRegistryProperties properties) {
        this.restClient = restClientBuilder.build();
        this.endpointResolver = endpointResolver;
        this.observationSupport = observationSupport;
        this.deliveryServiceName = properties.getDownstream().getDelivery().getServiceName();
        String configuredBaseUrl = properties.getDownstream().getDelivery().getBaseUrl();
        this.fallbackBaseUrl = configuredBaseUrl.isBlank()
                ? "http://" + deliveryServiceName + ":8080"
            : configuredBaseUrl;
    }

    @Override
    public DeliveryQuoteResponse quote(DeliveryQuoteRequest request, String accessToken) {
        return observationSupport.observe("delivery.order.downstream", deliveryServiceName, "quote", () ->
            Objects.requireNonNull(restClient.post()
                .uri(endpointResolver.resolveUrl(deliveryServiceName, fallbackBaseUrl, "/internal/delivery/quote"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(DeliveryQuoteResponse.class)));
    }
}