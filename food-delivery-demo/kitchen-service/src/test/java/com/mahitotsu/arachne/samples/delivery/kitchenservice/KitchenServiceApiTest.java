package com.mahitotsu.arachne.samples.delivery.kitchenservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class KitchenServiceApiTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void reportsSubstitutionWhenItemIsUnavailable() {
        KitchenCheckResponse response = restTemplate.postForObject(
                "/internal/kitchen/check",
                new KitchenCheckRequest("session-1", "ポテトも付けて", List.of("side-fries")),
                KitchenCheckResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.agent()).isEqualTo("kitchen-agent");
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.available()).isFalse();
            assertThat(item.substituteName()).isEqualTo("Nugget Share Box");
        });
    }
}