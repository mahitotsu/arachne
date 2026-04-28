package com.mahitotsu.arachne.samples.delivery.idatenadapter;

import static com.mahitotsu.arachne.samples.delivery.idatenadapter.domain.IdatenAdapterTypes.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class IdatenAdapterApiTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void exposesOpenApiContract() {
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("/adapter/eta")
                .contains("/adapter/health")
                .contains("Quote Idaten ETA");
    }

    @Test
    void returnsEtaQuoteForAvailableLowCostPartner() {
        ResponseEntity<AdapterEtaResponse> response = restTemplate.postForEntity(
                "/adapter/eta",
            new AdapterEtaRequest(
                List.of("combo-crispy", "drink-lemon"),
                new DeliveryPreferenceInput(null, DeliveryPriority.CHEAP)),
                AdapterEtaResponse.class);
        AdapterEtaResponse body = Objects.requireNonNull(response.getBody());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body.service()).isEqualTo("idaten-adapter");
        assertThat(body.status()).isEqualTo("AVAILABLE");
        assertThat(body.etaMinutes()).isEqualTo(36);
        assertThat(body.congestion()).isEqualTo("low");
        assertThat(body.fee()).isEqualByComparingTo(new BigDecimal("180.00"));
    }

    @Test
    void reportsAvailableHealthStatus() {
        ResponseEntity<AdapterHealthResponse> response = restTemplate.getForEntity(
                "/adapter/health",
                AdapterHealthResponse.class);
        AdapterHealthResponse body = Objects.requireNonNull(response.getBody());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body.status()).isEqualTo("AVAILABLE");
        assertThat(body.service()).isEqualTo("idaten-adapter");
    }
}