package com.mahitotsu.arachne.samples.delivery.menuservice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MenuServiceApiTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void suggestsKidFriendlyMenuItems() {
        MenuSuggestionResponse response = restTemplate.postForObject(
                "/internal/menu/suggest",
                new MenuSuggestionRequest("session-1", "2人で子ども向けのセットを見せて"),
                MenuSuggestionResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.agent()).isEqualTo("menu-agent");
        assertThat(response.items()).extracting(MenuItem::name).anyMatch(name -> name.contains("Kids"));
    }
}