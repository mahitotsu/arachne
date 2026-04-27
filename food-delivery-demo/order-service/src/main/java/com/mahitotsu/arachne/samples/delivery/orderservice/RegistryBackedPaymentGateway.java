package com.mahitotsu.arachne.samples.delivery.orderservice;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class RegistryBackedPaymentGateway implements PaymentGateway {

    private static final String DEFAULT_PAYMENT_SERVICE_NAME = "payment-service";

    private final RestClient restClient;
    private final ServiceEndpointResolver endpointResolver;
    private final String paymentServiceName;
    private final String fallbackBaseUrl;

    RegistryBackedPaymentGateway(
            RestClient.Builder restClientBuilder,
            ServiceEndpointResolver endpointResolver,
            @Value("${PAYMENT_SERVICE_NAME:" + DEFAULT_PAYMENT_SERVICE_NAME + "}") String paymentServiceName,
            @Value("${PAYMENT_SERVICE_BASE_URL:}") String fallbackBaseUrl) {
        this.restClient = restClientBuilder.build();
        this.endpointResolver = endpointResolver;
        this.paymentServiceName = paymentServiceName;
        this.fallbackBaseUrl = fallbackBaseUrl.isBlank()
                ? "http://" + paymentServiceName + ":8080"
                : fallbackBaseUrl;
    }

    @Override
    public PaymentPrepareResponse prepare(PaymentPrepareRequest request, String accessToken) {
        return Objects.requireNonNull(restClient.post()
                .uri(endpointResolver.resolveUrl(paymentServiceName, fallbackBaseUrl, "/internal/payment/prepare"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(PaymentPrepareResponse.class));
    }
}