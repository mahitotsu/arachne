package com.mahitotsu.arachne.samples.delivery.orderservice;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.ConfirmDeliveryRequest;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.ConfirmDeliveryResponse;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.ConfirmItemsRequest;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.ConfirmItemsResponse;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.ConfirmPaymentRequest;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.ConfirmPaymentResponse;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.DeliveryOptionChoice;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.OrderLineItem;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.ProposalItem;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.ServiceTrace;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.StoredOrderSummary;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.SuggestOrderRequest;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.SuggestOrderResponse;
import com.mahitotsu.arachne.samples.delivery.orderservice.infrastructure.RegistryServiceEndpointResolver;
import static com.mahitotsu.arachne.samples.delivery.testsupport.MockWebServerTestSupport.drainRequests;
import static com.mahitotsu.arachne.samples.delivery.testsupport.MockWebServerTestSupport.recordedPaths;
import static com.mahitotsu.arachne.samples.delivery.testsupport.MockWebServerTestSupport.trimTrailingSlash;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
                "delivery.order.session-store=in-memory",
                "spring.datasource.url=jdbc:h2:mem:orders;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        })
class OrderServiceApiTest {

        private static final String MENU_META_PATH = "/meta/menu-suggest";
        private static final String DELIVERY_META_PATH = "/meta/delivery-quote";
        private static final String PAYMENT_META_PATH = "/meta/payment-prepare";
        private static final String SUPPORT_META_PATH = "/meta/support-feedback";

    private static MockWebServer menuServer;
    private static MockWebServer deliveryServer;
    private static MockWebServer paymentServer;
        private static MockWebServer supportServer;
        private static MockWebServer registryServer;
    private static MockWebServer jwkServer;
    private static RSAKey signingKey;
    private static String accessToken;

    private static final String ISSUER = "http://customer-service";

    @Autowired
    private TestRestTemplate restTemplate;

        @Autowired
        private RegistryServiceEndpointResolver endpointResolver;

    @LocalServerPort
    private int port;

    @BeforeAll
    static void startServers() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("demo-key").generate();
        accessToken = createAccessToken();
        menuServer = new MockWebServer();
        deliveryServer = new MockWebServer();
        paymentServer = new MockWebServer();
        supportServer = new MockWebServer();
                registryServer = new MockWebServer();
                registryServer.setDispatcher(registryDispatcher());
        jwkServer = new MockWebServer();
        jwkServer.setDispatcher(jwkDispatcher());
        menuServer.start();
        deliveryServer.start();
        paymentServer.start();
        supportServer.start();
                registryServer.start();
        jwkServer.start();
    }

    @AfterAll
    static void stopServers() throws IOException {
        menuServer.shutdown();
        deliveryServer.shutdown();
        paymentServer.shutdown();
        supportServer.shutdown();
                registryServer.shutdown();
        jwkServer.shutdown();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
                registry.add("DELIVERY_REGISTRY_BASE_URL", () -> registryServer.url("/").toString());
                                registry.add("MENU_SERVICE_NAME", () -> "legacy-menu-service");
                                registry.add("DELIVERY_SERVICE_NAME", () -> "legacy-delivery-service");
                                registry.add("PAYMENT_SERVICE_NAME", () -> "legacy-payment-service");
                                registry.add("SUPPORT_SERVICE_NAME", () -> "legacy-support-service");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> jwkServer.url("/oauth2/jwks").toString());
    }

    @BeforeEach
    void prepareAuthenticatedClient() throws InterruptedException {
                endpointResolver.clearCache();
                drainRequests(registryServer);
        drainRequests(menuServer);
        drainRequests(deliveryServer);
        drainRequests(paymentServer);
                drainRequests(supportServer);
        restTemplate.getRestTemplate().setInterceptors(List.of((request, body, execution) -> {
            request.getHeaders().setBearerAuth(accessToken);
            return execution.execute(request, body);
        }));
    }

    @Test
    void rejectsUnauthenticatedSuggestRequests() {
        TestRestTemplate anonymous = new TestRestTemplate();

        ResponseEntity<String> response = anonymous.postForEntity(
                "http://localhost:" + port + "/api/order/suggest",
                new SuggestOrderRequest("", "おすすめを見せて", "ja-JP", null),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
        void suggestReturnsStructuredProposalAndWorkflowStep() throws InterruptedException {
        menuServer.enqueue(jsonResponse(menuSuggestBody()));

        SuggestOrderResponse response = restTemplate.postForObject(
                "/api/order/suggest",
                new SuggestOrderRequest("", "4人で4000円以内、子ども1人います", "ja-JP", null),
                SuggestOrderResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.workflowStep()).isEqualTo("item-selection");
        assertThat(response.etaMinutes()).isEqualTo(14);
        assertThat(response.proposals()).hasSize(2);
        assertThat(response.proposals()).extracting(ProposalItem::itemId)
                .containsExactly("combo-teriyaki", "drink-lemon");
        RecordedRequest menuRequest = menuServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(menuRequest).isNotNull();
        assertThat(menuRequest.getPath()).isEqualTo(MENU_META_PATH);
        assertThat(response.trace()).extracting(ServiceTrace::service)
                .contains("menu-service", "kitchen-service/support", "order-service");
        assertThat(recordedPaths(registryServer)).anyMatch(path -> path.startsWith("/registry/services"));
    }

    @Test
        void confirmItemsReturnsDeliveryOptions() throws InterruptedException {
        menuServer.enqueue(jsonResponse(menuSuggestBody()));
        SuggestOrderResponse suggestion = restTemplate.postForObject(
                "/api/order/suggest",
                new SuggestOrderRequest("", "照り焼きセットで", "ja-JP", null),
                SuggestOrderResponse.class);

        deliveryServer.enqueue(jsonResponse("""
                {
                  "service": "delivery-service",
                  "agent": "delivery-agent",
                  "headline": "delivery-agent prioritised the express lane",
                  "summary": "delivery-agent prepared an express route.",
                  "options": [
                    {"code": "express", "label": "In-house Express", "etaMinutes": 18, "fee": 300.0},
                                                                                {"code": "idaten", "label": "Idaten Economy", "etaMinutes": 34, "fee": 180.0}
                                                                        ],
                                                                        "recommendedTier": "express",
                                                                        "recommendationReason": "「急いで」の文脈なので最短ETAを優先しました。"
                }
                """));

        ConfirmItemsResponse response = restTemplate.postForObject(
                "/api/order/confirm-items",
                new ConfirmItemsRequest(suggestion.sessionId(), null),
                ConfirmItemsResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.workflowStep()).isEqualTo("delivery-selection");
        assertThat(response.draft().status()).isEqualTo("ITEMS_CONFIRMED");
        assertThat(response.items()).extracting(OrderLineItem::name)
                .containsExactly("Teriyaki Chicken Box", "Lemon Soda");
        assertThat(deliveryServer.takeRequest(1, TimeUnit.SECONDS).getPath()).isEqualTo(DELIVERY_META_PATH);
        assertThat(response.deliveryOptions()).hasSize(2);
        assertThat(response.deliveryOptions().get(0).recommended()).isTrue();
        assertThat(response.deliveryOptions().get(0).code()).isEqualTo("express");
    }

    @Test
    void confirmItemsUsesDeliveryServiceRecommendedTier() {
        menuServer.enqueue(jsonResponse(menuSuggestBody()));
        SuggestOrderResponse suggestion = restTemplate.postForObject(
                "/api/order/suggest",
                new SuggestOrderRequest("", "安く届けて", "ja-JP", null),
                SuggestOrderResponse.class);

        deliveryServer.enqueue(jsonResponse("""
                {
                  "service": "delivery-service",
                  "agent": "delivery-agent",
                  "headline": "delivery-agent recommends the economy lane",
                  "summary": "delivery-agent prepared a cheaper route.",
                  "options": [
                    {"code": "idaten", "label": "Idaten Economy", "etaMinutes": 34, "fee": 180.0},
                    {"code": "express", "label": "In-house Express", "etaMinutes": 18, "fee": 300.0}
                  ],
                  "recommendedTier": "idaten",
                  "recommendationReason": "「安く」の文脈なので最安料金を優先しました。"
                }
                """));

        ConfirmItemsResponse response = restTemplate.postForObject(
                "/api/order/confirm-items",
                new ConfirmItemsRequest(suggestion.sessionId(), null),
                ConfirmItemsResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.deliveryOptions()).extracting(DeliveryOptionChoice::code)
                .containsExactly("idaten", "express");
        assertThat(response.deliveryOptions()).filteredOn(DeliveryOptionChoice::recommended)
                .singleElement()
                .extracting(DeliveryOptionChoice::code)
                .isEqualTo("idaten");
    }

    @Test
        void confirmDeliveryReturnsPaymentSummary() throws InterruptedException {
        menuServer.enqueue(jsonResponse(menuSuggestBody()));
        SuggestOrderResponse suggestion = restTemplate.postForObject(
                "/api/order/suggest",
                new SuggestOrderRequest("", "照り焼きセットで", "ja-JP", null),
                SuggestOrderResponse.class);

        deliveryServer.enqueue(jsonResponse("""
                {
                  "service": "delivery-service",
                  "agent": "delivery-agent",
                  "headline": "delivery-agent prepared two options",
                  "summary": "delivery-agent recommends express.",
                  "options": [
                    {"code": "express", "label": "In-house Express", "etaMinutes": 18, "fee": 300.0},
                                                                                {"code": "idaten", "label": "Idaten Economy", "etaMinutes": 34, "fee": 180.0}
                                                                        ],
                                                                        "recommendedTier": "express",
                                                                        "recommendationReason": "「急いで」の文脈なので最短ETAを優先しました。"
                }
                """));
        restTemplate.postForObject(
                "/api/order/confirm-items",
                new ConfirmItemsRequest(suggestion.sessionId(), null),
                ConfirmItemsResponse.class);

        paymentServer.enqueue(jsonResponse("""
                {
                  "service": "payment-service",
                  "agent": "payment-service",
                  "headline": "payment-service prepared a deterministic total",
                  "summary": "payment-service is waiting for explicit confirmation.",
                  "selectedMethod": "Saved Visa ending in 2048",
                  "total": 2500.0,
                  "paymentStatus": "READY",
                  "charged": false,
                  "authorizationId": ""
                }
                """));

        ConfirmDeliveryResponse response = restTemplate.postForObject(
                "/api/order/confirm-delivery",
                new ConfirmDeliveryRequest(suggestion.sessionId(), "idaten"),
                ConfirmDeliveryResponse.class);

        RecordedRequest paymentPrepareRequest = paymentServer.takeRequest(1, TimeUnit.SECONDS);

        assertThat(response).isNotNull();
        assertThat(response.workflowStep()).isEqualTo("payment");
        assertThat(response.payment().paymentMethod()).isEqualTo("Saved Visa ending in 2048");
        assertThat(response.payment().total()).isEqualByComparingTo("2500.00");
        assertThat(response.draft().status()).isEqualTo("PAYMENT_READY");
        assertThat(paymentPrepareRequest).isNotNull();
        assertThat(paymentPrepareRequest.getPath()).isEqualTo(PAYMENT_META_PATH);
    }

    @Test
    void confirmPaymentPlacesOrder() throws Exception {
        menuServer.enqueue(jsonResponse(menuSuggestBody()));
        SuggestOrderResponse suggestion = restTemplate.postForObject(
                "/api/order/suggest",
                new SuggestOrderRequest("", "照り焼きセットで", "ja-JP", null),
                SuggestOrderResponse.class);

        deliveryServer.enqueue(jsonResponse("""
                {
                  "service": "delivery-service",
                  "agent": "delivery-agent",
                  "headline": "delivery-agent prepared two options",
                  "summary": "delivery-agent recommends express.",
                  "options": [
                    {"code": "express", "label": "In-house Express", "etaMinutes": 18, "fee": 300.0},
                                                                                {"code": "idaten", "label": "Idaten Economy", "etaMinutes": 34, "fee": 180.0}
                                                                        ],
                                                                        "recommendedTier": "express",
                                                                        "recommendationReason": "「急いで」の文脈なので最短ETAを優先しました。"
                }
                """));
        restTemplate.postForObject(
                "/api/order/confirm-items",
                new ConfirmItemsRequest(suggestion.sessionId(), null),
                ConfirmItemsResponse.class);

        paymentServer.enqueue(jsonResponse("""
                {
                  "service": "payment-service",
                  "agent": "payment-service",
                  "headline": "payment-service prepared a deterministic total",
                  "summary": "payment-service is waiting for explicit confirmation.",
                  "selectedMethod": "Saved Visa ending in 2048",
                  "total": 2500.0,
                  "paymentStatus": "READY",
                  "charged": false,
                  "authorizationId": ""
                }
                """));
        restTemplate.postForObject(
                "/api/order/confirm-delivery",
                new ConfirmDeliveryRequest(suggestion.sessionId(), "idaten"),
                ConfirmDeliveryResponse.class);
        RecordedRequest prepareRequest = paymentServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(prepareRequest).isNotNull();
        assertThat(prepareRequest.getPath()).isEqualTo(PAYMENT_META_PATH);
        assertThat(prepareRequest.getBody().readUtf8()).contains("\"confirmRequested\":false");

        paymentServer.enqueue(jsonResponse("""
                {
                  "service": "payment-service",
                  "agent": "payment-service",
                  "headline": "payment-service charged the selected card",
                  "summary": "payment-service completed the charge.",
                  "selectedMethod": "Saved Visa ending in 2048",
                  "total": 2500.0,
                  "paymentStatus": "CHARGED",
                  "charged": true,
                  "authorizationId": "pay-test-01"
                }
                """));
        supportServer.enqueue(jsonResponse("""
                {
                  "service": "support-service",
                  "agent": "support-agent",
                  "headline": "support-agent が問い合わせを受け付けました",
                  "summary": "GENERAL 問い合わせとして記録しました。",
                  "classification": "GENERAL",
                  "escalationRequired": false
                }
                """));

        ConfirmPaymentResponse response = restTemplate.postForObject(
                "/api/order/confirm-payment",
                new ConfirmPaymentRequest(suggestion.sessionId()),
                ConfirmPaymentResponse.class);

        RecordedRequest paymentRequest = paymentServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(paymentRequest).isNotNull();
        assertThat(paymentRequest.getPath()).isEqualTo(PAYMENT_META_PATH);
        assertThat(paymentRequest.getBody().readUtf8()).contains("\"confirmRequested\":true");
        RecordedRequest supportRequest = supportServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(supportRequest).isNotNull();
        assertThat(supportRequest.getPath()).isEqualTo(SUPPORT_META_PATH);
        assertThat(supportRequest.getBody().readUtf8())
                .contains("\"orderId\":")
                .contains("注文 ord-");
        assertThat(response).isNotNull();
        assertThat(response.workflowStep()).isEqualTo("completed");
        assertThat(response.draft().status()).isEqualTo("CONFIRMED");
        assertThat(response.draft().orderId()).isNotBlank();
        assertThat(response.summary()).contains("¥2500");
        assertThat(response.trace()).extracting(ServiceTrace::service)
                .contains("payment-service", "support-service", "order-service");
    }

    @Test
    void orderHistoryReturnsConfirmedOrders() {
        menuServer.enqueue(jsonResponse(menuSuggestBody()));
        SuggestOrderResponse suggestion = restTemplate.postForObject(
                "/api/order/suggest",
                new SuggestOrderRequest("", "照り焼きセットで", "ja-JP", null),
                SuggestOrderResponse.class);

        deliveryServer.enqueue(jsonResponse("""
                {
                  "service": "delivery-service",
                  "agent": "delivery-agent",
                  "headline": "delivery-agent prepared two options",
                  "summary": "delivery-agent recommends express.",
                  "options": [
                    {"code": "express", "label": "In-house Express", "etaMinutes": 18, "fee": 300.0},
                    {"code": "idaten", "label": "Idaten Economy", "etaMinutes": 34, "fee": 180.0}
                  ],
                  "recommendedTier": "express",
                  "recommendationReason": "「急いで」の文脈なので最短ETAを優先しました。"
                }
                """));
        restTemplate.postForObject(
                "/api/order/confirm-items",
                new ConfirmItemsRequest(suggestion.sessionId(), null),
                ConfirmItemsResponse.class);

        paymentServer.enqueue(jsonResponse("""
                {
                  "service": "payment-service",
                  "agent": "payment-service",
                  "headline": "payment-service prepared a deterministic total",
                  "summary": "payment-service is waiting for explicit confirmation.",
                  "selectedMethod": "Saved Visa ending in 2048",
                  "total": 2500.0,
                  "paymentStatus": "READY",
                  "charged": false,
                  "authorizationId": ""
                }
                """));
        restTemplate.postForObject(
                "/api/order/confirm-delivery",
                new ConfirmDeliveryRequest(suggestion.sessionId(), "express"),
                ConfirmDeliveryResponse.class);

        paymentServer.enqueue(jsonResponse("""
                {
                  "service": "payment-service",
                  "agent": "payment-service",
                  "headline": "payment-service charged the selected card",
                  "summary": "payment-service completed the charge.",
                  "selectedMethod": "Saved Visa ending in 2048",
                  "total": 2500.0,
                  "paymentStatus": "CHARGED",
                  "charged": true,
                  "authorizationId": "pay-test-02"
                }
                """));
        supportServer.enqueue(jsonResponse("""
                {
                  "service": "support-service",
                  "agent": "support-agent",
                  "headline": "support-agent が問い合わせを受け付けました",
                  "summary": "GENERAL 問い合わせとして記録しました。",
                  "classification": "GENERAL",
                  "escalationRequired": false
                }
                """));
        ConfirmPaymentResponse confirmed = restTemplate.postForObject(
                "/api/order/confirm-payment",
                new ConfirmPaymentRequest(suggestion.sessionId()),
                ConfirmPaymentResponse.class);

        ResponseEntity<StoredOrderSummary[]> historyResponse = restTemplate.getForEntity(
                "/api/orders/history",
                StoredOrderSummary[].class);

        assertThat(historyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(historyResponse.getBody()).isNotNull();
        assertThat(List.of(historyResponse.getBody()))
                .extracting(StoredOrderSummary::orderId)
                .contains(confirmed.draft().orderId());
    }

    private static String menuSuggestBody() {
        return """
                {
                  "service": "menu-service",
                  "agent": "menu-agent",
                  "headline": "menu-agent matched 2 menu options",
                  "summary": "menu-agent recommends Teriyaki Chicken Box and Lemon Soda.",
                  "etaMinutes": 14,
                  "items": [
                                                                                {"id": "combo-teriyaki", "name": "Teriyaki Chicken Box", "description": "Teriyaki set.", "price": 920.0, "suggestedQuantity": 2, "category": "combo", "tags": ["teriyaki", "chicken"]},
                                                                                {"id": "drink-lemon", "name": "Lemon Soda", "description": "Fresh lemon soda.", "price": 240.0, "suggestedQuantity": 2, "category": "drink", "tags": ["lemon", "soda"]}
                  ],
                  "kitchenTrace": {
                    "summary": "kitchen-agent cleared the draft with a 14 minute ETA.",
                    "notes": ["All items available"]
                  }
                }
                """;
    }

    private static MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(body);
    }

    private static Dispatcher jwkDispatcher() {
        return new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse()
                        .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .setBody(new JWKSet(signingKey.toPublicJWK()).toString());
            }
        };
    }

        private static Dispatcher registryDispatcher() {
                return new Dispatcher() {
                        @Override
                        public MockResponse dispatch(RecordedRequest request) {
                                String path = request.getPath();
                                if (path != null && path.startsWith("/registry/register")) {
                                        return jsonResponse("""
                                                        {
                                                          "serviceName": "order-service",
                                                          "endpoint": "http://order-service:8080",
                                                          "status": "AVAILABLE"
                                                        }
                                                        """);
                                }
                                if (path != null && path.startsWith("/registry/services")) {
                                        return jsonResponse("""
                                                        [
                                                                                                                                                                                                                                        {"serviceName": "legacy-menu-service", "endpoint": "%s", "capability": "メニュー提案、在庫付き提案、注文候補の提示を扱う。", "agentName": "menu-agent", "requestMethod": "POST", "requestPath": "%s", "status": "AVAILABLE"},
                                                                                                                                                                                                                                        {"serviceName": "legacy-delivery-service", "endpoint": "%s", "capability": "配送候補、ETA 比較、配送選択肢の提示を扱う。", "agentName": "delivery-agent", "requestMethod": "POST", "requestPath": "%s", "status": "AVAILABLE"},
                                                                                                                                                                                                                                        {"serviceName": "legacy-payment-service", "endpoint": "%s", "capability": "支払い準備、合計確認、課金確定を扱う。", "agentName": "payment-service", "requestMethod": "POST", "requestPath": "%s", "status": "AVAILABLE"},
                                                                                                                                                                                                                                        {"serviceName": "legacy-support-service", "endpoint": "%s", "capability": "注文後フィードバック受付、問い合わせ受付、サポート連携を扱う。", "agentName": "support-agent", "requestMethod": "POST", "requestPath": "%s", "status": "AVAILABLE"}
                                                        ]
                                                        """.formatted(
                                                                        trimTrailingSlash(menuServer.url("/").toString()),
                                                                                                                                                                                                                                                                                                MENU_META_PATH,
                                                                        trimTrailingSlash(deliveryServer.url("/").toString()),
                                                                                                                                                                                                                                                                                                DELIVERY_META_PATH,
                                                                        trimTrailingSlash(paymentServer.url("/").toString()),
                                                                                                                                                                                                                                                                                                PAYMENT_META_PATH,
                                                                                                                                                                                                                                                                                                trimTrailingSlash(supportServer.url("/").toString()),
                                                                                                                                                                                                                                                                                                SUPPORT_META_PATH));
                                }
                                return new MockResponse().setResponseCode(404);
                        }
                };
        }

    private static String createAccessToken() {
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(signingKey.getKeyID())
                            .type(JOSEObjectType.JWT)
                            .build(),
                    new JWTClaimsSet.Builder()
                            .issuer(ISSUER)
                            .subject("demo-user")
                            .audience("food-delivery-demo")
                            .claim("scope", "chat order:create")
                            .issueTime(java.util.Date.from(Instant.now().minusSeconds(30)))
                            .expirationTime(java.util.Date.from(Instant.now().plusSeconds(3600)))
                            .build());
            jwt.sign(new RSASSASigner(signingKey.toPrivateKey()));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}