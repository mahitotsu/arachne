package com.mahitotsu.arachne.samples.delivery.orderservice.infrastructure;

import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.mahitotsu.arachne.samples.delivery.orderservice.config.OrderRegistryProperties;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.SupportFeedbackRequestPayload;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.SupportFeedbackResponse;

@Component
public class RegistryBackedSupportGateway implements SupportGateway {

    private static final String DEFAULT_SUPPORT_SERVICE_NAME = "support-service";

    private final RestClient restClient;
    private final ServiceEndpointResolver endpointResolver;
    private final DownstreamObservationSupport observationSupport;
    private final String supportServiceName;
    private final String fallbackBaseUrl;

    RegistryBackedSupportGateway(
            RestClient.Builder restClientBuilder,
            ServiceEndpointResolver endpointResolver,
            DownstreamObservationSupport observationSupport,
            OrderRegistryProperties properties) {
        this.restClient = restClientBuilder.build();
        this.endpointResolver = endpointResolver;
        this.observationSupport = observationSupport;
        this.supportServiceName = properties.getDownstream().getSupport().getServiceName();
        String configuredBaseUrl = properties.getDownstream().getSupport().getBaseUrl();
        this.fallbackBaseUrl = configuredBaseUrl.isBlank()
                ? "http://" + supportServiceName + ":8080"
            : configuredBaseUrl;
    }

    @Override
    public Optional<SupportFeedbackResponse> recordFeedback(SupportFeedbackRequestPayload request, String accessToken) {
        try {
            return observationSupport.observe("delivery.order.downstream", supportServiceName, "record-feedback", () ->
                    Optional.ofNullable(restClient.post()
                            .uri(endpointResolver.resolveUrl(supportServiceName, fallbackBaseUrl, "/api/support/feedback"))
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(request)
                            .retrieve()
                            .body(SupportFeedbackResponse.class)));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}