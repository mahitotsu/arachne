package com.mahitotsu.arachne.samples.delivery.orderservice.infrastructure;

import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.mahitotsu.arachne.samples.delivery.orderservice.config.OrderRegistryProperties;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.PaymentPrepareRequest;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.PaymentPrepareResponse;

@Component
public class RegistryBackedPaymentGateway implements PaymentGateway {

    private static final String PAYMENT_TARGET = "payment-service";

    private final RestClient restClient;
    private final ServiceEndpointResolver endpointResolver;
    private final DownstreamObservationSupport observationSupport;
    private final String paymentCapabilityQuery;

    RegistryBackedPaymentGateway(
            RestClient.Builder restClientBuilder,
            ServiceEndpointResolver endpointResolver,
            DownstreamObservationSupport observationSupport,
            OrderRegistryProperties properties) {
        this.restClient = restClientBuilder.build();
        this.endpointResolver = endpointResolver;
        this.observationSupport = observationSupport;
        this.paymentCapabilityQuery = properties.getDownstream().getPayment().getCapabilityQuery();
    }

    @Override
    public PaymentPrepareResponse prepare(PaymentPrepareRequest request, String accessToken) {
        return observationSupport.observe("delivery.order.downstream", PAYMENT_TARGET, "prepare", () ->
            Objects.requireNonNull(restClient.post()
            .uri(endpointResolver.resolveUrl(paymentCapabilityQuery, "/internal/payment/prepare"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(PaymentPrepareResponse.class)));
    }
}