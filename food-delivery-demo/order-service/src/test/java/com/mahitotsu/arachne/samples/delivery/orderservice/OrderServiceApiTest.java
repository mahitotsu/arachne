package com.mahitotsu.arachne.samples.delivery.orderservice;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

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

        @BeforeEach
        void clearCapturedRequests() throws InterruptedException {
                drainRequests(menuServer);
                drainRequests(kitchenServer);
                drainRequests(deliveryServer);
                drainRequests(paymentServer);
        }

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
                new ChatRequest("", "前回と同じ量で最速配送にして。この内容で注文確定して", "ja-JP"),
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
                new ChatRequest("", "ポテトを追加して", "ja-JP"),
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

        ChatResponse response = restTemplate.postForObject(
                "/api/chat",
                new ChatRequest("", "子ども向けのおすすめを見せて", "ja-JP"),
                ChatResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.assistantMessage())
                .contains("辛さ控えめで食べやすい組み合わせとして")
                .contains("このまま注文に進められる内容は 2× Crispy Chicken Box、2× Lemon Soda です。")
                .contains("キッチンではこの内容をそのまま用意でき")
                .contains("出来上がり目安は約14分")
                .doesNotContain("kitchen-agent cleared the draft.");
        assertThat(response.choices()).containsExactly("はい、この内容で注文します", "変更したい");
    }

    @Test
    void recommendationReplyClarifiesOrderableItemsWhenPartOfProposalIsUnavailable() {
        menuServer.enqueue(jsonResponse("""
                {
                  "service": "menu-service",
                  "agent": "menu-agent",
                  "headline": "menu-agent matched 4 menu options",
                  "summary": "menu-agent recommends Crispy Chicken Box x1, Smash Burger Combo x1, Curly Fries x1, Lemon Soda x1. It keeps the order aligned with the current menu.",
                  "items": [
                    {"id": "combo-crispy", "name": "Crispy Chicken Box", "description": "Crispy chicken, fries, and lemon soda.", "price": 980.0, "suggestedQuantity": 1},
                    {"id": "combo-smash", "name": "Smash Burger Combo", "description": "Double smash burger with fries and cola.", "price": 1050.0, "suggestedQuantity": 1},
                    {"id": "side-fries", "name": "Curly Fries", "description": "Seasoned curly fries.", "price": 330.0, "suggestedQuantity": 1},
                    {"id": "drink-lemon", "name": "Lemon Soda", "description": "Fresh lemon soda with low sweetness.", "price": 240.0, "suggestedQuantity": 1}
                  ]
                }
                """));
        kitchenServer.enqueue(jsonResponse("""
                {
                  "service": "kitchen-service",
                  "agent": "kitchen-agent",
                  "headline": "kitchen-agent found 1 substitution point",
                  "summary": "Sorry, I can't proceed with the substitution request due to a technical issue with the menu-agent service.",
                  "readyInMinutes": 16,
                  "items": [
                    {"itemId": "combo-crispy", "available": true, "prepMinutes": 14, "substituteItemId": null, "substituteName": null, "substitutePrice": null},
                    {"itemId": "combo-smash", "available": true, "prepMinutes": 16, "substituteItemId": null, "substituteName": null, "substitutePrice": null},
                    {"itemId": "side-fries", "available": false, "prepMinutes": 7, "substituteItemId": null, "substituteName": null, "substitutePrice": null},
                    {"itemId": "drink-lemon", "available": true, "prepMinutes": 3, "substituteItemId": null, "substituteName": null, "substitutePrice": null}
                  ],
                  "collaborations": []
                }
                """));

        ChatResponse response = restTemplate.postForObject(
                "/api/chat",
                new ChatRequest("", "おすすめを見せて", "ja-JP"),
                ChatResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.assistantMessage())
                .contains("このまま注文に進められる内容は 1× Crispy Chicken Box、1× Smash Burger Combo、1× Lemon Soda です。")
                .contains("Curly Fries は現在ご用意できません。")
                .doesNotContain("Curly Fries x1");
        assertThat(response.choices()).containsExactly("はい、この内容で注文します", "変更したい");
    }

    @Test
    void proposalConfirmationImmediatelyAdvancesToDeliveryOptions() {
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

        ChatResponse proposal = restTemplate.postForObject(
                "/api/chat",
                new ChatRequest("", "子ども向けのおすすめを見せて", "ja-JP"),
                ChatResponse.class);

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

        ChatResponse confirmed = restTemplate.postForObject(
                "/api/chat",
                new ChatRequest(proposal.sessionId(), "はい、この内容で注文します", "ja-JP"),
                ChatResponse.class);

        assertThat(confirmed).isNotNull();
        assertThat(confirmed.routing()).isNotNull();
        assertThat(confirmed.routing().intent()).isEqualTo("draft-confirmation");
        assertThat(confirmed.routing().entryStep()).isEqualTo("delivery-quote");
        assertThat(confirmed.draft().status()).isEqualTo("DRAFT_READY");
        assertThat(confirmed.trace()).extracting(ServiceTrace::service)
                .contains("delivery-service", "order-service");
        assertThat(confirmed.assistantMessage())
                .contains("配送候補を確認しました")
                .contains("In-house Express")
                .contains("Partner Standard");
        assertThat(confirmed.choices()).containsExactly(
                "In-house Express (18分・¥300)",
                "Partner Standard (27分・¥180)");
    }

    @Test
    void paymentAfterDeliverySelectionUsesExistingDraftItemsInTotal() throws Exception {
        menuServer.enqueue(jsonResponse("""
                {
                  "service": "menu-service",
                  "agent": "menu-agent",
                  "headline": "menu-agent matched 1 menu option",
                  "summary": "menu-agent recommends Iced Latte.",
                  "items": [
                    {"id": "drink-latte", "name": "Iced Latte", "description": "Fresh iced latte.", "price": 320.0, "suggestedQuantity": 1}
                  ]
                }
                """));
        kitchenServer.enqueue(jsonResponse("""
                {
                  "service": "kitchen-service",
                  "agent": "kitchen-agent",
                  "headline": "kitchen-agent cleared the whole draft",
                  "summary": "kitchen-agent cleared the draft.",
                  "readyInMinutes": 4,
                  "items": [
                    {"itemId": "drink-latte", "available": true, "prepMinutes": 4, "substituteItemId": null, "substituteName": null, "substitutePrice": null}
                  ]
                }
                """));

        ChatResponse proposal = restTemplate.postForObject(
                "/api/chat",
                new ChatRequest("", "冷たい飲み物のおすすめを見せて", "ja-JP"),
                ChatResponse.class);

        deliveryServer.enqueue(jsonResponse("""
                {
                  "service": "delivery-service",
                  "agent": "delivery-agent",
                  "headline": "delivery-agent prioritised the express lane",
                  "summary": "delivery-agent prepared an express route.",
                  "options": [
                    {"code": "express", "label": "In-house Express", "etaMinutes": 14, "fee": 300.0},
                    {"code": "standard", "label": "Partner Standard", "etaMinutes": 27, "fee": 180.0}
                  ]
                }
                """));

        ChatResponse quoted = restTemplate.postForObject(
                "/api/chat",
                new ChatRequest(proposal.sessionId(), "はい、この内容で注文します", "ja-JP"),
                ChatResponse.class);

        paymentServer.enqueue(jsonResponse("""
                {
                  "service": "payment-service",
                  "agent": "payment-agent",
                  "headline": "payment-agent completed a deterministic charge",
                  "summary": "payment-agent used the default card.",
                  "selectedMethod": "Saved Visa ending in 2048",
                  "total": 500.0,
                  "paymentStatus": "CHARGED",
                  "charged": true,
                  "authorizationId": "pay-test-02"
                }
                """));

        ChatResponse confirmed = restTemplate.postForObject(
                "/api/chat",
                new ChatRequest(quoted.sessionId(), "パートナースタンダードで注文確定して", "ja-JP"),
                ChatResponse.class);

        RecordedRequest paymentRequest = paymentServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(paymentRequest).isNotNull();
        assertThat(paymentRequest.getBody().readUtf8()).contains("\"total\":500.00");
        assertThat(confirmed).isNotNull();
        assertThat(confirmed.draft().status()).isEqualTo("CONFIRMED");
        assertThat(confirmed.draft().subtotal()).isEqualByComparingTo("320.00");
        assertThat(confirmed.draft().total()).isEqualByComparingTo("500.00");
        assertThat(confirmed.assistantMessage()).contains("¥500");
    }

                @Test
                void paymentAfterExpressSelectionUsesExpressFee() throws Exception {
                                menuServer.enqueue(jsonResponse("""
                                                                {
                                                                        "service": "menu-service",
                                                                        "agent": "menu-agent",
                                                                        "headline": "menu-agent matched 1 menu option",
                                                                        "summary": "menu-agent recommends Iced Latte.",
                                                                        "items": [
                                                                                {"id": "drink-latte", "name": "Iced Latte", "description": "Fresh iced latte.", "price": 320.0, "suggestedQuantity": 1}
                                                                        ]
                                                                }
                                                                """));
                                kitchenServer.enqueue(jsonResponse("""
                                                                {
                                                                        "service": "kitchen-service",
                                                                        "agent": "kitchen-agent",
                                                                        "headline": "kitchen-agent cleared the whole draft",
                                                                        "summary": "kitchen-agent cleared the draft.",
                                                                        "readyInMinutes": 4,
                                                                        "items": [
                                                                                {"itemId": "drink-latte", "available": true, "prepMinutes": 4, "substituteItemId": null, "substituteName": null, "substitutePrice": null}
                                                                        ]
                                                                }
                                                                """));

                                ChatResponse proposal = restTemplate.postForObject(
                                                                "/api/chat",
                                                                new ChatRequest("", "冷たい飲み物のおすすめを見せて", "ja-JP"),
                                                                ChatResponse.class);

                                deliveryServer.enqueue(jsonResponse("""
                                                                {
                                                                        "service": "delivery-service",
                                                                        "agent": "delivery-agent",
                                                                        "headline": "delivery-agent prioritised the express lane",
                                                                        "summary": "delivery-agent prepared an express route.",
                                                                        "options": [
                                                                                {"code": "express", "label": "In-house Express", "etaMinutes": 14, "fee": 300.0},
                                                                                {"code": "standard", "label": "Partner Standard", "etaMinutes": 27, "fee": 180.0}
                                                                        ]
                                                                }
                                                                """));

                                ChatResponse quoted = restTemplate.postForObject(
                                                                "/api/chat",
                                                                new ChatRequest(proposal.sessionId(), "はい、この内容で注文します", "ja-JP"),
                                                                ChatResponse.class);

                                paymentServer.enqueue(jsonResponse("""
                                                                {
                                                                        "service": "payment-service",
                                                                        "agent": "payment-agent",
                                                                        "headline": "payment-agent completed a deterministic charge",
                                                                        "summary": "payment-agent used the default card.",
                                                                        "selectedMethod": "Saved Visa ending in 2048",
                                                                        "total": 620.0,
                                                                        "paymentStatus": "CHARGED",
                                                                        "charged": true,
                                                                        "authorizationId": "pay-test-03"
                                                                }
                                                                """));

                                ChatResponse confirmed = restTemplate.postForObject(
                                                                "/api/chat",
                                                                new ChatRequest(quoted.sessionId(), "In-house Express で注文確定して", "ja-JP"),
                                                                ChatResponse.class);

                                RecordedRequest paymentRequest = paymentServer.takeRequest(1, TimeUnit.SECONDS);
                                assertThat(paymentRequest).isNotNull();
                                assertThat(paymentRequest.getBody().readUtf8()).contains("\"total\":620.00");
                                assertThat(confirmed).isNotNull();
                                assertThat(confirmed.draft().status()).isEqualTo("CONFIRMED");
                                assertThat(confirmed.draft().subtotal()).isEqualByComparingTo("320.00");
                                assertThat(confirmed.draft().total()).isEqualByComparingTo("620.00");
                                assertThat(confirmed.assistantMessage()).contains("In-house Express").contains("¥620");
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
                new ChatRequest("", "チキンボックスを注文したい", "ja-JP"),
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

        private static void drainRequests(MockWebServer server) throws InterruptedException {
                while (server.takeRequest(10, TimeUnit.MILLISECONDS) != null) {
                        // Drain leftover requests from previous tests because servers are shared per class.
                }
        }
}