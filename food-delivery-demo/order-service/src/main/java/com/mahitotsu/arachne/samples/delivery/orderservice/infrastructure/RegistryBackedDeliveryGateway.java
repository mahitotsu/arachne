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

    private static final String DELIVERY_TARGET = "delivery-service";

    private final RestClient restClient;
    private final ServiceEndpointResolver endpointResolver;
    private final DownstreamObservationSupport observationSupport;
    private final String deliveryCapabilityQuery;

    RegistryBackedDeliveryGateway(
            RestClient.Builder restClientBuilder,
            ServiceEndpointResolver endpointResolver,
            DownstreamObservationSupport observationSupport,
            OrderRegistryProperties properties) {
        this.restClient = restClientBuilder.build();
        this.endpointResolver = endpointResolver;
        this.observationSupport = observationSupport;
        this.deliveryCapabilityQuery = properties.getDownstream().getDelivery().getCapabilityQuery();
    }

    @Override
    public DeliveryQuoteResponse quote(DeliveryQuoteRequest request, String accessToken) {
        return observationSupport.observe("delivery.order.downstream", DELIVERY_TARGET, "quote", () ->
            Objects.requireNonNull(restClient.post()
            .uri(endpointResolver.resolveUrl(deliveryCapabilityQuery, "/internal/delivery/quote"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(DeliveryQuoteResponse.class)));
    }
}