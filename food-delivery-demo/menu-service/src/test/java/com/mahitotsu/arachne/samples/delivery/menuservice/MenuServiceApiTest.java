package com.mahitotsu.arachne.samples.delivery.menuservice;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"delivery.model.mode=deterministic"})
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

    @Test
    void activatesFamilyOrderGuideSkillForGroupRequest() {
        MenuSuggestionResponse response = restTemplate.postForObject(
                "/internal/menu/suggest",
                new MenuSuggestionRequest("session-family", "家族4人でファミリー向けのセットを見せて"),
                MenuSuggestionResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.summary()).contains("[family-order-guide]");
        assertThat(response.items()).isNotEmpty();
    }
}