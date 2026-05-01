package com.mahitotsu.arachne.samples.delivery.deliveryservice.infrastructure;

import java.util.List;
import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahitotsu.arachne.samples.delivery.deliveryservice.domain.DeliveryTypes.AdapterEtaRequestPayload;
import com.mahitotsu.arachne.samples.delivery.deliveryservice.domain.DeliveryTypes.AdapterEtaResponsePayload;
import com.mahitotsu.arachne.samples.delivery.deliveryservice.domain.DeliveryTypes.DeliveryPreferenceInput;
import com.mahitotsu.arachne.samples.delivery.deliveryservice.domain.DeliveryTypes.EtaServiceTarget;
import com.mahitotsu.arachne.samples.delivery.deliveryservice.domain.DeliveryTypes.ExternalEtaQuote;

@Component
public class HttpExternalEtaGateway implements ExternalEtaGateway {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final DownstreamObservationSupport observationSupport;

    HttpExternalEtaGateway(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            DownstreamObservationSupport observationSupport,
            SsrfGuardInterceptor ssrfGuardInterceptor) {
        // SsrfGuardInterceptor はこの RestClient 専用。レジストリ呼び出し等の
        // 他の RestClient には適用されない。
        this.restClient = restClientBuilder
                .requestInterceptor(ssrfGuardInterceptor)
                .build();
        this.objectMapper = objectMapper;
        this.observationSupport = observationSupport;
    }

    @Override
    public Optional<ExternalEtaQuote> quote(EtaServiceTarget service, List<String> itemNames, DeliveryPreferenceInput preference) {
        try {
            AdapterEtaResponsePayload response = observationSupport.observe(
                    "delivery.delivery.downstream",
                    service.serviceName(),
                    "quote",
                    () -> restClient.post()
                            .uri(service.url())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(new AdapterEtaRequestPayload(itemNames, preference))
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