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
                "spring.datasource.url=jdbc:h2:mem:orders;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        })
class OrderServiceApiTest {

    private static MockWebServer menuServer;
    private static MockWebServer deliveryServer;
    private static MockWebServer paymentServer;
    private static MockWebServer jwkServer;
    private static RSAKey signingKey;
    private static String accessToken;

    private static final String ISSUER = "http://customer-service";

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @BeforeAll
    static void startServers() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("demo-key").generate();
        accessToken = createAccessToken();
        menuServer = new MockWebServer();
        deliveryServer = new MockWebServer();
        paymentServer = new MockWebServer();
        jwkServer = new MockWebServer();
        jwkServer.setDispatcher(jwkDispatcher());
        menuServer.start();
        deliveryServer.start();
        paymentServer.start();
        jwkServer.start();
    }

    @AfterAll
    static void stopServers() throws IOException {
        menuServer.shutdown();
        deliveryServer.shutdown();
        paymentServer.shutdown();
        jwkServer.shutdown();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("MENU_SERVICE_BASE_URL", () -> menuServer.url("/").toString());
        registry.add("DELIVERY_SERVICE_BASE_URL", () -> deliveryServer.url("/").toString());
        registry.add("PAYMENT_SERVICE_BASE_URL", () -> paymentServer.url("/").toString());
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> jwkServer.url("/oauth2/jwks").toString());
    }

    @BeforeEach
    void prepareAuthenticatedClient() throws InterruptedException {
        drainRequests(menuServer);
        drainRequests(deliveryServer);
        drainRequests(paymentServer);
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
    void suggestReturnsStructuredProposalAndWorkflowStep() {
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
        assertThat(response.trace()).extracting(ServiceTrace::service)
                .contains("menu-service", "kitchen-service/support", "order-service");
    }

    @Test
    void confirmItemsReturnsDeliveryOptions() {
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
                    {"code": "standard", "label": "Partner Standard", "etaMinutes": 27, "fee": 180.0}
                  ]
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
        assertThat(response.deliveryOptions()).hasSize(2);
        assertThat(response.deliveryOptions().get(0).recommended()).isTrue();
    }

    @Test
    void confirmDeliveryReturnsPaymentSummary() {
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
                    {"code": "standard", "label": "Partner Standard", "etaMinutes": 27, "fee": 180.0}
                  ]
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
                new ConfirmDeliveryRequest(suggestion.sessionId(), "standard"),
                ConfirmDeliveryResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.workflowStep()).isEqualTo("payment");
        assertThat(response.payment().paymentMethod()).isEqualTo("Saved Visa ending in 2048");
        assertThat(response.payment().total()).isEqualByComparingTo("2500.00");
        assertThat(response.draft().status()).isEqualTo("PAYMENT_READY");
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
                    {"code": "standard", "label": "Partner Standard", "etaMinutes": 27, "fee": 180.0}
                  ]
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
                new ConfirmDeliveryRequest(suggestion.sessionId(), "standard"),
                ConfirmDeliveryResponse.class);
        RecordedRequest prepareRequest = paymentServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(prepareRequest).isNotNull();
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

        ConfirmPaymentResponse response = restTemplate.postForObject(
                "/api/order/confirm-payment",
                new ConfirmPaymentRequest(suggestion.sessionId()),
                ConfirmPaymentResponse.class);

        RecordedRequest paymentRequest = paymentServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(paymentRequest).isNotNull();
        assertThat(paymentRequest.getBody().readUtf8()).contains("\"confirmRequested\":true");
        assertThat(response).isNotNull();
        assertThat(response.workflowStep()).isEqualTo("completed");
        assertThat(response.draft().status()).isEqualTo("CONFIRMED");
        assertThat(response.draft().orderId()).isNotBlank();
        assertThat(response.summary()).contains("¥2500");
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
                    {"id": "combo-teriyaki", "name": "Teriyaki Chicken Box", "description": "Teriyaki set.", "price": 920.0, "suggestedQuantity": 2},
                    {"id": "drink-lemon", "name": "Lemon Soda", "description": "Fresh lemon soda.", "price": 240.0, "suggestedQuantity": 2}
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

    private static void drainRequests(MockWebServer server) throws InterruptedException {
        while (server.takeRequest(10, TimeUnit.MILLISECONDS) != null) {
            // Drain shared server state between tests.
        }
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