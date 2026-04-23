package com.mahitotsu.arachne.samples.delivery.orderservice;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
                "delivery.model.mode=deterministic",
                "delivery.order.session-store=in-memory",
                "spring.datasource.url=jdbc:h2:mem:orders;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        })
class OrderServiceApiTest {

    private static MockWebServer menuServer;
    private static MockWebServer kitchenServer;
    private static MockWebServer deliveryServer;
    private static MockWebServer paymentServer;

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeAll
    static void startServers() throws IOException {
        menuServer = new MockWebServer();
        kitchenServer = new MockWebServer();
        deliveryServer = new MockWebServer();
        paymentServer = new MockWebServer();
        menuServer.start();
        kitchenServer.start();
        deliveryServer.start();
        paymentServer.start();
    }

    @AfterAll
    static void stopServers() throws IOException {
        menuServer.shutdown();
        kitchenServer.shutdown();
        deliveryServer.shutdown();
        paymentServer.shutdown();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("MENU_SERVICE_BASE_URL", () -> menuServer.url("/").toString());
        registry.add("KITCHEN_SERVICE_BASE_URL", () -> kitchenServer.url("/").toString());
        registry.add("DELIVERY_SERVICE_BASE_URL", () -> deliveryServer.url("/").toString());
        registry.add("PAYMENT_SERVICE_BASE_URL", () -> paymentServer.url("/").toString());
    }

    @Test
    void confirmsAnOrderThroughTheComposedFlow() {
        menuServer.enqueue(jsonResponse("""
                {
                  "service": "menu-service",
                  "agent": "menu-agent",
                  "headline": "menu-agent matched 2 menu options",
                  "summary": "menu-agent recommends Crispy Chicken Box and Lemon Soda.",
                  "items": [
                    {"id": "combo-crispy", "name": "Crispy Chicken Box", "description": "Crispy chicken, fries, and lemon soda.", "price": 980.0, "suggestedQuantity": 2},
                    {"id": "drink-lemon", "name": "Lemon Soda", "description": "Fresh lemon soda.", "price": 240.0, "suggestedQuantity": 2}
                  ]
                }
                """));
        kitchenServer.enqueue(jsonResponse("""
                {
                  "service": "kitchen-service",
                  "agent": "kitchen-agent",
                  "headline": "kitchen-agent cleared the whole draft",
                  "summary": "kitchen-agent cleared the draft.",
                  "readyInMinutes": 14,
                  "items": [
                    {"itemId": "combo-crispy", "available": true, "prepMinutes": 14, "substituteItemId": null, "substituteName": null, "substitutePrice": null},
                    {"itemId": "drink-lemon", "available": true, "prepMinutes": 3, "substituteItemId": null, "substituteName": null, "substitutePrice": null}
                  ]
                }
                """));
        deliveryServer.enqueue(jsonResponse("""
                {
                  "service": "delivery-service",
                  "agent": "delivery-agent",
                  "headline": "delivery-agent prioritised the express lane",
                  "summary": "delivery-agent prepared an express route.",
                  "options": [
                                                                                {"code": "express", "label": "In-house Express", "etaMinutes": 18, "fee": 300.0},
                                                                                {"code": "standard", "label": "Partner Standard", "etaMinutes": 27, "fee": 180.0}
                  ]
                }
                """));
        paymentServer.enqueue(jsonResponse("""
                {
                  "service": "payment-service",
                  "agent": "payment-agent",
                  "headline": "payment-agent completed a deterministic charge",
                  "summary": "payment-agent used the default card.",
                  "selectedMethod": "Saved Visa ending in 2048",
                  "total": 2740.0,
                  "paymentStatus": "CHARGED",
                  "charged": true,
                  "authorizationId": "pay-test-01"
                }
                """));

        ChatResponse response = restTemplate.postForObject(
                "/api/chat",
                new ChatRequest("", "前回と同じ量で最速配送にして。この内容で注文確定して"),
                ChatResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.sessionId()).isNotBlank();
        assertThat(response.draft().status()).isEqualTo("CONFIRMED");
        assertThat(response.draft().orderId()).isNotBlank();
        assertThat(response.trace()).hasSize(5);
        assertThat(response.routing()).isNotNull();
        assertThat(response.routing().intent()).isEqualTo("direct-order");
        assertThat(response.routing().selectedSkill()).isEqualTo("order-skill");
        assertThat(response.routing().entryStep()).isEqualTo("kitchen-validation");
        assertThat(response.trace())
                .filteredOn(trace -> "order-service".equals(trace.service()))
                .singleElement()
                .satisfies(trace -> assertThat(trace.routing()).isEqualTo(response.routing()));
        assertThat(response.assistantMessage())
                .contains("In-house Express")
                .contains("Saved Visa ending in 2048")
                .contains("¥2740")
                .contains("注文を確定しました");
    }

    @Test
    void surfacesKitchenToMenuCollaborationWhenKitchenNeedsFallbackHelp() {
        menuServer.enqueue(jsonResponse("""
                {
                  "service": "menu-service",
                  "agent": "menu-agent",
                  "headline": "menu-agent matched 1 menu option",
                  "summary": "menu-agent recommends Curly Fries.",
                  "items": [
                    {"id": "side-fries", "name": "Curly Fries", "description": "Seasoned curly fries.", "price": 330.0, "suggestedQuantity": 1}
                  ]
                }
                """));
        kitchenServer.enqueue(jsonResponse("""
                {
                  "service": "kitchen-service",
                  "agent": "kitchen-agent",
                  "headline": "kitchen-agent found 1 substitution point",
                  "summary": "kitchen-agent checked the line, consulted menu-agent, and approved Nugget Share Box.",
                  "readyInMinutes": 8,
                  "items": [
                    {"itemId": "side-fries", "available": false, "prepMinutes": 8, "substituteItemId": "side-nuggets", "substituteName": "Nugget Share Box", "substitutePrice": 640.0}
                  ],
                  "collaborations": [
                    {"service": "menu-service/support", "agent": "menu-agent", "headline": "menu-agent prepared 2 fallback options", "summary": "menu-agent suggested Nugget Share Box and Garden Wrap for kitchen-agent to validate."}
                  ]
                }
                """));
        deliveryServer.enqueue(jsonResponse("""
                {
                  "service": "delivery-service",
                  "agent": "delivery-agent",
                  "headline": "delivery-agent prioritised the express lane",
                  "summary": "delivery-agent prepared an express route.",
                  "options": [
                                                                                {"code": "express", "label": "In-house Express", "etaMinutes": 18, "fee": 300.0},
                                                                                {"code": "standard", "label": "Partner Standard", "etaMinutes": 27, "fee": 180.0}
                  ]
                }
                """));
        paymentServer.enqueue(jsonResponse("""
                {
                  "service": "payment-service",
                  "agent": "payment-agent",
                  "headline": "payment-agent prepared a draft total",
                  "summary": "payment-agent is waiting for explicit confirmation.",
                  "selectedMethod": "Saved Visa ending in 2048",
                  "total": 940.0,
                  "paymentStatus": "READY",
                  "charged": false,
                  "authorizationId": ""
                }
                """));

        ChatResponse response = restTemplate.postForObject(
                "/api/chat",
                new ChatRequest("", "ポテトを追加して"),
                ChatResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.trace()).extracting(ServiceTrace::service)
                .contains("kitchen-service", "menu-service/support");
        assertThat(response.trace())
                .filteredOn(trace -> "menu-service/support".equals(trace.service()))
                .singleElement()
                .satisfies(trace -> {
                    assertThat(trace.agent()).isEqualTo("menu-agent");
                    assertThat(trace.headline()).contains("fallback options");
                });
        assertThat(response.draft().items())
                .extracting(OrderLineItem::name)
                .contains("Nugget Share Box");
    }

    @Test
    void recommendationReplyMergesMenuRationaleWithKitchenStatus() {
        menuServer.enqueue(jsonResponse("""
                {
                  "service": "menu-service",
                  "agent": "menu-agent",
                  "headline": "menu-agent matched 2 menu options",
                  "summary": "辛さ控えめで食べやすい組み合わせとして、Crispy Chicken Box と Lemon Soda がおすすめです。",
                  "items": [
                    {"id": "combo-crispy", "name": "Crispy Chicken Box", "description": "Crispy chicken, fries, and lemon soda.", "price": 980.0, "suggestedQuantity": 2},
                    {"id": "drink-lemon", "name": "Lemon Soda", "description": "Fresh lemon soda.", "price": 240.0, "suggestedQuantity": 2}
                  ]
                }
                """));
        kitchenServer.enqueue(jsonResponse("""
                {
                  "service": "kitchen-service",
                  "agent": "kitchen-agent",
                  "headline": "kitchen-agent cleared the whole draft",
                  "summary": "kitchen-agent cleared the draft.",
                  "readyInMinutes": 14,
                  "items": [
                    {"itemId": "combo-crispy", "available": true, "prepMinutes": 14, "substituteItemId": null, "substituteName": null, "substitutePrice": null},
                    {"itemId": "drink-lemon", "available": true, "prepMinutes": 3, "substituteItemId": null, "substituteName": null, "substitutePrice": null}
                  ]
                }
                """));
        deliveryServer.enqueue(jsonResponse("""
                {
                  "service": "delivery-service",
                  "agent": "delivery-agent",
                  "headline": "delivery-agent prioritised the express lane",
                  "summary": "delivery-agent prepared an express route.",
                  "options": [
                    {"code": "express", "label": "In-house Express", "etaMinutes": 18, "fee": 300.0},
                    {"code": "standard", "label": "Partner Standard", "etaMinutes": 27, "fee": 180.0}
                  ]
                }
                """));
        paymentServer.enqueue(jsonResponse("""
                {
                  "service": "payment-service",
                  "agent": "payment-agent",
                  "headline": "payment-agent prepared a draft total",
                  "summary": "payment-agent is waiting for explicit confirmation.",
                  "selectedMethod": "Saved Visa ending in 2048",
                  "total": 2740.0,
                  "paymentStatus": "READY",
                  "charged": false,
                  "authorizationId": ""
                }
                """));

        ChatResponse response = restTemplate.postForObject(
                "/api/chat",
                new ChatRequest("", "子ども向けのおすすめを見せて"),
                ChatResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.assistantMessage())
                .contains("辛さ控えめで食べやすい組み合わせとして")
                .contains("キッチンではこの内容をそのまま用意でき")
                .contains("出来上がり目安は約14分")
                .doesNotContain("kitchen-agent cleared the draft.");
    }

    @Test
    void directOrderReplyMergesDeliveryAndPaymentIntoNaturalSummary() {
        menuServer.enqueue(jsonResponse("""
                {
                  "service": "menu-service",
                  "agent": "menu-agent",
                  "headline": "menu-agent matched 1 menu option",
                  "summary": "menu-agent recommends Crispy Chicken Box.",
                  "items": [
                    {"id": "combo-crispy", "name": "Crispy Chicken Box", "description": "Crispy chicken, fries, and lemon soda.", "price": 980.0, "suggestedQuantity": 1}
                  ]
                }
                """));
        kitchenServer.enqueue(jsonResponse("""
                {
                  "service": "kitchen-service",
                  "agent": "kitchen-agent",
                  "headline": "kitchen-agent cleared the whole draft",
                  "summary": "kitchen-agent cleared the draft.",
                  "readyInMinutes": 12,
                  "items": [
                    {"itemId": "combo-crispy", "available": true, "prepMinutes": 12, "substituteItemId": null, "substituteName": null, "substitutePrice": null}
                  ]
                }
                """));
        deliveryServer.enqueue(jsonResponse("""
                {
                  "service": "delivery-service",
                  "agent": "delivery-agent",
                  "headline": "delivery-agent prioritised the express lane",
                  "summary": "delivery-agent prepared an express route.",
                  "options": [
                    {"code": "express", "label": "In-house Express", "etaMinutes": 18, "fee": 300.0},
                    {"code": "standard", "label": "Partner Standard", "etaMinutes": 27, "fee": 180.0}
                  ]
                }
                """));
        paymentServer.enqueue(jsonResponse("""
                {
                  "service": "payment-service",
                  "agent": "payment-agent",
                  "headline": "payment-agent prepared a draft total",
                  "summary": "payment-agent is waiting for explicit confirmation.",
                  "selectedMethod": "Saved Visa ending in 2048",
                  "total": 1280.0,
                  "paymentStatus": "READY",
                  "charged": false,
                  "authorizationId": ""
                }
                """));

        ChatResponse response = restTemplate.postForObject(
                "/api/chat",
                new ChatRequest("", "チキンボックスを注文したい"),
                ChatResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.assistantMessage())
                .contains("In-house Express")
                .contains("Partner Standard")
                .contains("Saved Visa ending in 2048")
                .contains("¥1280")
                .contains("注文確定");
    }

    private static MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}