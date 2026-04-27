package com.mahitotsu.arachne.samples.delivery.hermesadapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class HermesAdapterApiTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private HermesAdapterService adapterService;

    @Test
    void returnsServiceUnavailableWhenEtaProviderIsUnavailable() {
        when(adapterService.quote(new AdapterEtaRequest(List.of("combo-crispy"), "急ぎで届けてほしい")))
                .thenReturn(new AdapterEtaResponse(
                        "hermes-adapter",
                        "NOT_AVAILABLE",
                        0,
                        "high",
                        new BigDecimal("0.00"),
                        "Hermes は現在混雑のため受付停止中です。"));

        ResponseEntity<AdapterEtaResponse> response = restTemplate.postForEntity(
                "/adapter/eta",
                new AdapterEtaRequest(List.of("combo-crispy"), "急ぎで届けてほしい"),
                AdapterEtaResponse.class);
        AdapterEtaResponse body = Objects.requireNonNull(response.getBody());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(body.status()).isEqualTo("NOT_AVAILABLE");
        assertThat(body.service()).isEqualTo("hermes-adapter");
    }

    @Test
    void returnsServiceUnavailableFromHealthWhenAdapterIsDown() {
        when(adapterService.available()).thenReturn(false);

        ResponseEntity<AdapterHealthResponse> response = restTemplate.getForEntity(
                "/adapter/health",
                AdapterHealthResponse.class);
        AdapterHealthResponse body = Objects.requireNonNull(response.getBody());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(body.status()).isEqualTo("NOT_AVAILABLE");
        assertThat(body.service()).isEqualTo("hermes-adapter");
    }
}