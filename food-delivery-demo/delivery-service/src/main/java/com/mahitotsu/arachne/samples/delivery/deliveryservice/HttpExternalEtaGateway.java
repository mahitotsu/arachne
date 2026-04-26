package com.mahitotsu.arachne.samples.delivery.deliveryservice;

import java.util.List;
import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
class HttpExternalEtaGateway implements ExternalEtaGateway {

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    HttpExternalEtaGateway(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<ExternalEtaQuote> quote(EtaServiceTarget service, List<String> itemNames, String context) {
        try {
            AdapterEtaResponsePayload response = restClientBuilder.build().post()
                    .uri(service.url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new AdapterEtaRequestPayload(itemNames, context))
                    .retrieve()
                    .body(AdapterEtaResponsePayload.class);
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