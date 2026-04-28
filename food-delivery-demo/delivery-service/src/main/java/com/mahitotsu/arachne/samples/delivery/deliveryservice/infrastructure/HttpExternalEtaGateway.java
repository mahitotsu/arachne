package com.mahitotsu.arachne.samples.delivery.deliveryservice.infrastructure;

import static com.mahitotsu.arachne.samples.delivery.deliveryservice.domain.DeliveryTypes.*;

import java.util.List;
import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class HttpExternalEtaGateway implements ExternalEtaGateway {

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final DownstreamObservationSupport observationSupport;

    HttpExternalEtaGateway(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            DownstreamObservationSupport observationSupport) {
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
        this.observationSupport = observationSupport;
    }

    @Override
    public Optional<ExternalEtaQuote> quote(EtaServiceTarget service, List<String> itemNames, String context) {
        try {
            AdapterEtaResponsePayload response = observationSupport.observe(
                    "delivery.delivery.downstream",
                    service.serviceName(),
                    "quote",
                    () -> restClientBuilder.build().post()
                            .uri(service.url())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(new AdapterEtaRequestPayload(itemNames, context))
                            .retrieve()
                            .body(AdapterEtaResponsePayload.class));
            return toQuote(service, response);
        } catch (RestClientResponseException ex) {
            return toQuote(service, parse(ex.getResponseBodyAsString()));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Optional<ExternalEtaQuote> toQuote(EtaServiceTarget service, AdapterEtaResponsePayload response) {
        if (response == null || !"AVAILABLE".equalsIgnoreCase(response.status())) {
            return Optional.empty();
        }
        return Optional.of(new ExternalEtaQuote(
                service.serviceName(),
                response.etaMinutes(),
                response.congestion(),
                response.fee(),
                response.note()));
    }

    private AdapterEtaResponsePayload parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(body, AdapterEtaResponsePayload.class);
        } catch (Exception ignored) {
            return null;
        }
    }
}