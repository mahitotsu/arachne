package com.mahitotsu.arachne.samples.delivery.deliveryservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"delivery.model.mode=deterministic"})
class DeliveryServiceApiTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void returnsExpressOptionFirstForFastestRequests() {
        DeliveryQuoteResponse response = restTemplate.postForObject(
                "/internal/delivery/quote",
                new DeliveryQuoteRequest("session-1", "最速配送でお願い", List.of("Crispy Chicken Box")),
                DeliveryQuoteResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.agent()).isEqualTo("delivery-agent");
        assertThat(response.options()).first().extracting(DeliveryOption::code).isEqualTo("express");
    }
}