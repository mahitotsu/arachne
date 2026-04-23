package com.mahitotsu.arachne.samples.delivery.kitchenservice;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"delivery.model.mode=deterministic"})
class KitchenServiceApiTest {

    private static MockWebServer menuServer;

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeAll
    static void startServer() throws IOException {
        menuServer = new MockWebServer();
        menuServer.start();
    }

    @AfterAll
    static void stopServer() throws IOException {
        menuServer.shutdown();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("MENU_SERVICE_BASE_URL", () -> menuServer.url("/").toString());
    }

    @Test
    void reportsSubstitutionWhenItemIsUnavailable() {
        menuServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "service": "menu-service",
                          "agent": "menu-agent",
                          "headline": "menu-agent prepared 2 fallback options",
                          "summary": "menu-agent suggested Nugget Share Box and Garden Wrap for kitchen-agent to validate.",
                          "items": [
                            {"id": "side-nuggets", "name": "Nugget Share Box", "description": "Ten-piece nugget box with sauces.", "price": 640.0, "suggestedQuantity": 1},
                            {"id": "wrap-garden", "name": "Garden Wrap", "description": "Fresh veggie wrap with yogurt sauce.", "price": 760.0, "suggestedQuantity": 1}
                          ]
                        }
                        """));

        KitchenCheckResponse response = restTemplate.postForObject(
                "/internal/kitchen/check",
                new KitchenCheckRequest("session-1", "ポテトも付けて", List.of("side-fries")),
                KitchenCheckResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.agent()).isEqualTo("kitchen-agent");
        assertThat(response.summary()).contains("consulted menu-agent");
        assertThat(response.collaborations()).singleElement().satisfies(trace -> {
            assertThat(trace.service()).isEqualTo("menu-service/support");
            assertThat(trace.agent()).isEqualTo("menu-agent");
        });
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.available()).isFalse();
            assertThat(item.substituteName()).isEqualTo("Nugget Share Box");
        });
    }
}