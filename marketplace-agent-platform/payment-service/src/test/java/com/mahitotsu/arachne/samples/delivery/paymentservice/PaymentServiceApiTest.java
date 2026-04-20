package com.mahitotsu.arachne.samples.delivery.paymentservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentServiceApiTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void chargesWhenTheRequestIsConfirmed() {
        PaymentPrepareResponse response = restTemplate.postForObject(
                "/internal/payment/prepare",
                new PaymentPrepareRequest("session-1", "この内容でApple Payで確定", new BigDecimal("3280.00"), true),
                PaymentPrepareResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.agent()).isEqualTo("payment-agent");
        assertThat(response.charged()).isTrue();
        assertThat(response.authorizationId()).isNotBlank();
    }
}