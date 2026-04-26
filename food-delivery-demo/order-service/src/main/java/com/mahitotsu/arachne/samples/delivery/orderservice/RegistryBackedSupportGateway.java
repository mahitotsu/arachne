package com.mahitotsu.arachne.samples.delivery.orderservice;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class RegistryBackedSupportGateway implements SupportGateway {

    private static final String SUPPORT_CAPABILITY_QUERY = "問い合わせ受付";

    private final RestClient restClient;
    private final ServiceEndpointResolver endpointResolver;
    private final String fallbackBaseUrl;

    RegistryBackedSupportGateway(
            RestClient.Builder restClientBuilder,
            ServiceEndpointResolver endpointResolver,
            @Value("${SUPPORT_SERVICE_BASE_URL:}") String fallbackBaseUrl) {
        this.restClient = restClientBuilder.build();
        this.endpointResolver = endpointResolver;
        this.fallbackBaseUrl = fallbackBaseUrl;
    }

    @Override
    public Optional<SupportFeedbackResponse> recordFeedback(SupportFeedbackRequestPayload request, String accessToken) {
        try {
            return Optional.ofNullable(restClient.post()
                    .uri(endpointResolver.resolveUrl(SUPPORT_CAPABILITY_QUERY, fallbackBaseUrl, "/api/support/feedback"))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(SupportFeedbackResponse.class));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}