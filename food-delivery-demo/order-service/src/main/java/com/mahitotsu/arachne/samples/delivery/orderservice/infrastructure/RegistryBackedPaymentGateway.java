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

    private static final String DEFAULT_PAYMENT_SERVICE_NAME = "payment-service";

    private final RestClient restClient;
    private final ServiceEndpointResolver endpointResolver;
    private final String paymentServiceName;
    private final String fallbackBaseUrl;

    RegistryBackedPaymentGateway(
            RestClient.Builder restClientBuilder,
            ServiceEndpointResolver endpointResolver,
            OrderRegistryProperties properties) {
        this.restClient = restClientBuilder.build();
        this.endpointResolver = endpointResolver;
        this.paymentServiceName = properties.getDownstream().getPayment().getServiceName();
        String configuredBaseUrl = properties.getDownstream().getPayment().getBaseUrl();
        this.fallbackBaseUrl = configuredBaseUrl.isBlank()
                ? "http://" + paymentServiceName + ":8080"
            : configuredBaseUrl;
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