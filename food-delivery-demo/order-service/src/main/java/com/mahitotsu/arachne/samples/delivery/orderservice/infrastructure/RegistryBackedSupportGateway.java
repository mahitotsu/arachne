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

    private static final String SUPPORT_TARGET = "support-service";

    private final RestClient restClient;
    private final ServiceEndpointResolver endpointResolver;
    private final DownstreamObservationSupport observationSupport;
    private final String supportCapabilityQuery;

    RegistryBackedSupportGateway(
            RestClient.Builder restClientBuilder,
            ServiceEndpointResolver endpointResolver,
            DownstreamObservationSupport observationSupport,
            OrderRegistryProperties properties) {
        this.restClient = restClientBuilder.build();
        this.endpointResolver = endpointResolver;
        this.observationSupport = observationSupport;
        this.supportCapabilityQuery = properties.getDownstream().getSupport().getCapabilityQuery();
    }

    @Override
    public Optional<SupportFeedbackResponse> recordFeedback(SupportFeedbackRequestPayload request, String accessToken) {
        try {
            return observationSupport.observe(
                "delivery.order.downstream",
                request.sessionId(),
                SUPPORT_TARGET,
                "record-feedback",
                "orderId=" + request.orderId(),
                () ->
                    Optional.ofNullable(restClient.post()
                            .uri(endpointResolver.resolveUrl(supportCapabilityQuery, "/api/support/feedback"))
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