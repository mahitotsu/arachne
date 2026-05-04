package com.mahitotsu.arachne.samples.delivery.menuservice;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuExecutionHistoryTypes.MenuExecutionHistoryEvent;
import com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuExecutionHistoryTypes.MenuExecutionHistoryResponse;
import com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuTypes.MenuItem;
import com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuTypes.MenuSubstitutionRequest;
import com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuTypes.MenuSubstitutionResponse;
import com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuTypes.MenuSuggestionRequest;
import com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuTypes.MenuSuggestionResponse;
import com.mahitotsu.arachne.samples.delivery.menuservice.infrastructure.RegistryServiceEndpointResolver;
import static com.mahitotsu.arachne.samples.delivery.testsupport.MockWebServerTestSupport.drainRequests;
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
        properties = {"delivery.model.mode=deterministic"})
class MenuServiceApiTest {

    private static MockWebServer kitchenServer;
    private static MockWebServer registryServer;
    private static final AtomicReference<String> lastRegistryDiscoverRequestBody = new AtomicReference<>("");
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
    static void startServer() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("menu-test-key").generate();
        accessToken = createAccessToken();
        kitchenServer = new MockWebServer();
        kitchenServer.setDispatcher(kitchenDispatcher());
        registryServer = new MockWebServer();
        registryServer.setDispatcher(registryDispatcher());
        jwkServer = new MockWebServer();
        jwkServer.setDispatcher(jwkDispatcher());
        kitchenServer.start();
        registryServer.start();
        jwkServer.start();
    }

    @AfterAll
    static void stopServer() throws IOException {
        kitchenServer.shutdown();
        registryServer.shutdown();
        jwkServer.shutdown();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("DELIVERY_REGISTRY_BASE_URL", () -> registryServer.url("/").toString());
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> jwkServer.url("/oauth2/jwks").toString());
    }

    @BeforeEach
    void prepareAuthenticatedClient() throws InterruptedException {
        endpointResolver.clearCache();
        lastRegistryDiscoverRequestBody.set("");
        drainRequests(registryServer);
        restTemplate.getRestTemplate().setInterceptors(List.of((request, body, execution) -> {
            request.getHeaders().setBearerAuth(accessToken);
            return execution.execute(request, body);
        }));
    }

    @Test
    void rejectsUnauthenticatedSuggestRequests() {
        TestRestTemplate anonymous = new TestRestTemplate();

        ResponseEntity<String> response = anonymous.postForEntity(
                "http://localhost:" + port + "/internal/menu/suggest",
                new MenuSuggestionRequest("session-unauth", "おすすめを見せて"),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void exposesOpenApiContractAndPromptExtensionsWithoutAuthentication() {
        TestRestTemplate anonymous = new TestRestTemplate();

        ResponseEntity<String> response = anonymous.getForEntity(
                "http://localhost:" + port + "/v3/api-docs",
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("/internal/menu/suggest")
                .contains("/api/menu/catalog")
                .contains("x-ai-prompt-contract")
            .contains("主たる customer の注文意図");
    }

    @Test
    void suggestsKidFriendlyMenuItems() throws InterruptedException {
        MenuSuggestionResponse response = restTemplate.postForObject(
                "/internal/menu/suggest",
                new MenuSuggestionRequest("session-1", "2人で子ども向けのセットを見せて"),
                MenuSuggestionResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.agent()).isEqualTo("menu-agent");
        assertThat(response.items()).extracting(MenuItem::name).anyMatch(name -> name.contains("Kids"));
        assertThat(response.etaMinutes()).isPositive();
        assertThat(response.kitchenTrace()).isNotNull();
        RecordedRequest registryRequest = registryServer.takeRequest();
        assertThat(registryRequest.getPath()).isEqualTo("/registry/discover");
        assertThat(lastRegistryDiscoverRequestBody.get())
            .contains("\"query\":\"在庫確認 調理ライン別 ETA 欠品時の代替承認 混雑時の別ライン提案\"")
            .contains("\"availableOnly\":true");
    }

    @Test
    void actuatorMetricsExposeAgentInvocationAndToolCallsAfterSuggest() {
        MenuSuggestionResponse response = restTemplate.postForObject(
                "/internal/menu/suggest",
                new MenuSuggestionRequest("session-metrics", "おすすめを見せて"),
                MenuSuggestionResponse.class);

        ResponseEntity<String> metricsIndex = restTemplate.getForEntity("/actuator/metrics", String.class);

        assertThat(response).isNotNull();
        assertThat(metricsIndex.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(metricsIndex.getBody()).contains("delivery.agent.invocation", "delivery.agent.tool.call", "delivery.agent.usage.tokens");
        assertMetricWithTags("/actuator/metrics/delivery.agent.invocation?tag=service:menu-service&tag=agent:menu-agent&tag=outcome:success");
        assertMetricWithTags("/actuator/metrics/delivery.agent.tool.call?tag=service:menu-service&tag=agent:menu-agent&tag=tool:catalog_lookup_tool&tag=outcome:success");
        assertMetricWithTags("/actuator/metrics/delivery.agent.tool.call?tag=service:menu-service&tag=agent:menu-agent&tag=tool:calculate_total_tool&tag=outcome:success");
        assertMetricWithTags("/actuator/metrics/delivery.agent.usage.tokens?tag=service:menu-service&tag=agent:menu-agent&tag=type:input");
        assertMetricWithTags("/actuator/metrics/delivery.agent.usage.tokens?tag=service:menu-service&tag=agent:menu-agent&tag=type:output");
        assertMetricWithTags("/actuator/metrics/delivery.menu.downstream?tag=target:kitchen-service&tag=operation:check&tag=outcome:success");
        assertMetricWithTags("/actuator/metrics/delivery.menu.registry.lookup?tag=target:registry-service&tag=operation:resolve-endpoint&tag=outcome:success");
    }

        @Test
        void executionHistoryCapturesAgentModelToolAndSkills() {
        MenuSuggestionResponse response = restTemplate.postForObject(
            "/internal/menu/suggest",
            new MenuSuggestionRequest("session-history", "家族4人でファミリー向けのセットを見せて"),
            MenuSuggestionResponse.class);

        ResponseEntity<MenuExecutionHistoryResponse> historyResponse = restTemplate.getForEntity(
            "/internal/menu/execution-history/session-history",
            MenuExecutionHistoryResponse.class);

        assertThat(response).isNotNull();
        assertThat(historyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(historyResponse.getBody()).isNotNull();
        assertThat(historyResponse.getBody().events())
            .extracting(MenuExecutionHistoryEvent::category)
            .contains("agent", "model", "tool");
        assertThat(historyResponse.getBody().events())
            .filteredOn(event -> "agent".equals(event.category()) && "success".equals(event.outcome()))
            .anySatisfy(event -> {
                assertThat(event.component()).isEqualTo("menu-agent");
                assertThat(event.skills()).contains("family-order-guide");
            });
        }

    @Test
    void catalogExposesCategoryAndTagsForAllSixteenItems() {
        ResponseEntity<MenuItem[]> response = restTemplate.getForEntity("/api/menu/catalog", MenuItem[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(16);
        assertThat(response.getBody())
                .filteredOn(item -> "combo-teriyaki".equals(item.id()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.category()).isEqualTo("combo");
                    assertThat(item.tags()).containsExactly("chicken", "grill", "japanese");
                });
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

        @Test
        void familyBudgetRequestReturnsKidFriendlySetWithinBudget() {
        MenuSuggestionResponse response = restTemplate.postForObject(
            "/internal/menu/suggest",
            new MenuSuggestionRequest("session-family-budget", "4人で4000円以内、子ども1人います"),
            MenuSuggestionResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.summary()).contains("[family-order-guide]");
        assertThat(response.items()).extracting(MenuItem::id)
            .contains("combo-kids", "drink-lemon");
        assertThat(response.items()).extracting(MenuItem::id)
            .anyMatch(id -> id.startsWith("combo-") && !"combo-kids".equals(id));
        assertThat(total(response.items())).isLessThanOrEqualTo(new BigDecimal("4000.00"));
        assertThat(response.items())
            .filteredOn(item -> "drink".equals(item.category()))
            .singleElement()
            .satisfies(item -> assertThat(item.suggestedQuantity()).isEqualTo(4));
        }

    @Test
    void suggestsFallbackItemsForUnavailableRequest() {
        MenuSubstitutionResponse response = restTemplate.postForObject(
                "/internal/menu/substitutes",
                new MenuSubstitutionRequest("session-sub", "子どもも食べやすい代替にして", "side-fries"),
                MenuSubstitutionResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.agent()).isEqualTo("menu-agent");
        assertThat(response.headline()).contains("代替候補");
        assertThat(response.summary()).contains("kitchen-agent");
        assertThat(response.items()).extracting(MenuItem::id).doesNotContain("side-fries");
    }

    @Test
    void substitutionCandidatesStayInCategoryAndPreferTagOverlap() {
        MenuSubstitutionResponse response = restTemplate.postForObject(
                "/internal/menu/substitutes",
                new MenuSubstitutionRequest("session-sub-2", "チキン系でお願いします", "combo-crispy"),
                MenuSubstitutionResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.items()).hasSizeLessThanOrEqualTo(3);
        assertThat(response.items()).extracting(MenuItem::category).containsOnly("combo");
        assertThat(response.items()).extracting(MenuItem::id)
                .startsWith("combo-teriyaki")
                .doesNotContain("combo-crispy");
    }

            @Test
            void suggestReplacesUnavailableItemWithIntentMatchedSubstitute() {
            MenuSuggestionResponse response = restTemplate.postForObject(
                "/internal/menu/suggest",
                new MenuSuggestionRequest("session-sub-suggest", "クリスピーチキンお願いします"),
                MenuSuggestionResponse.class);

            assertThat(response).isNotNull();
            assertThat(response.items()).extracting(MenuItem::id)
                .contains("combo-teriyaki")
                .doesNotContain("combo-crispy");
            assertThat(response.kitchenTrace()).isNotNull();
            assertThat(response.kitchenTrace().summary()).contains("代替");
            }

    @Test
    void genericSuggestionSummaryMatchesReturnedItems() {
        MenuSuggestionResponse response = restTemplate.postForObject(
                "/internal/menu/suggest",
                new MenuSuggestionRequest("session-generic", "おすすめを見せて"),
                MenuSuggestionResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.items()).hasSizeGreaterThan(1);
        assertThat(response.summary()).contains("Crispy Chicken Box");
        assertThat(response.summary()).contains("Smash Burger Combo");
        assertThat(response.summary()).contains("Curly Fries");
        assertThat(response.summary()).contains("Lemon Soda");
    }

    private static Dispatcher jwkDispatcher() {
        return new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (request.getPath() != null && request.getPath().startsWith("/oauth2/jwks")) {
                    return new MockResponse()
                            .setHeader("Content-Type", "application/json")
                            .setBody(new JWKSet(signingKey.toPublicJWK()).toString());
                }
                return new MockResponse().setResponseCode(404);
            }
        };
    }

    private static Dispatcher kitchenDispatcher() {
        return new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (request.getPath() == null || !request.getPath().startsWith("/internal/kitchen/check")) {
                    return new MockResponse().setResponseCode(404);
                }
                String body = request.getBody().readUtf8();
                List<String> itemIds = java.util.regex.Pattern.compile("\"(combo-[^\"]+|side-[^\"]+|drink-[^\"]+|wrap-[^\"]+|bowl-[^\"]+|dessert-[^\"]+)\"")
                        .matcher(body)
                        .results()
                        .map(match -> match.group(1))
                        .distinct()
                        .toList();
                boolean crispyUnavailable = body.contains("クリスピーチキン") && itemIds.contains("combo-crispy");
            String itemsJson = itemIds.stream()
                .map(itemId -> kitchenItemJson(itemId, crispyUnavailable))
                        .reduce((left, right) -> left + "," + right)
                        .orElse("{\"itemId\": \"combo-crispy\", \"available\": true, \"prepMinutes\": 14, \"substituteItemId\": null, \"substituteName\": null, \"substitutePrice\": null}");
            int readyInMinutes = itemIds.stream()
                .map(itemId -> crispyUnavailable && "combo-crispy".equals(itemId) ? "combo-teriyaki" : itemId)
                .mapToInt(MenuServiceApiTest::prepMinutes)
                .max()
                .orElse(14);
            String summary = crispyUnavailable
                ? "kitchen-agent が combo-crispy の欠品を検出し、チキン系の代替として combo-teriyaki を提案しました。"
                : "kitchen-agent がラインを確認しました: すべて準備可能です。";
                return new MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody("""
                                {
                                  "service": "kitchen-service",
                                  "agent": "kitchen-agent",
                                  "headline": "kitchen-agent が全アイテムの在庫を確認しました",
                      "summary": "%s",
                                  "readyInMinutes": %d,
                                  "items": [%s],
                                  "collaborations": []
                                }
                    """.formatted(summary, readyInMinutes, itemsJson));
            }
        };
    }

    private static Dispatcher registryDispatcher() {
        return new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path != null && path.startsWith("/registry/register")) {
                    return new MockResponse()
                            .setHeader("Content-Type", "application/json")
                            .setBody("""
                                    {
                                      "serviceName": "menu-service",
                                      "endpoint": "http://menu-service:8080",
                                      "status": "AVAILABLE"
                                    }
                                    """);
                }
                                if (path != null && path.startsWith("/registry/discover")) {
                                        String requestBody = request.getBody().readUtf8();
                                        lastRegistryDiscoverRequestBody.set(requestBody);
                    return new MockResponse()
                            .setHeader("Content-Type", "application/json")
                            .setBody("""
                                                                        {
                                                                            "service": "registry-service",
                                                                            "agent": "registry-agent",
                                                                            "summary": "query に一致した service を返しました。",
                                                                            "matches": [
                                                                                {"serviceName": "kitchen-service", "endpoint": "%s", "requestPath": "/internal/kitchen/check", "status": "AVAILABLE"}
                                                                            ]
                                                                        }
                                                                        """.formatted(trimTrailingSlash(kitchenServer.url("/").toString())));
                }
                return new MockResponse().setResponseCode(404);
            }
        };
    }

        private static String kitchenItemJson(String itemId, boolean crispyUnavailable) {
        if (crispyUnavailable && "combo-crispy".equals(itemId)) {
            return """
                {"itemId": "combo-crispy", "available": false, "prepMinutes": 14, "substituteItemId": "combo-teriyaki", "substituteName": "Teriyaki Chicken Box", "substitutePrice": 920.00}
                """;
        }
        return """
            {"itemId": "%s", "available": true, "prepMinutes": %d, "substituteItemId": null, "substituteName": null, "substitutePrice": null}
            """.formatted(itemId, prepMinutes(itemId));
        }

        private static BigDecimal total(List<MenuItem> items) {
        return items.stream()
            .map(item -> item.price().multiply(BigDecimal.valueOf(item.suggestedQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

    private static int prepMinutes(String itemId) {
        if (itemId.startsWith("drink-")) {
            return 4;
        }
        if (itemId.startsWith("side-")) {
            return 8;
        }
        if (itemId.startsWith("bowl-") || itemId.startsWith("wrap-")) {
            return 11;
        }
        return 14;
    }

    private static String createAccessToken() throws JOSEException {
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("demo-user")
                .audience("food-delivery-demo")
                .issueTime(java.util.Date.from(Instant.now()))
                .expirationTime(java.util.Date.from(Instant.now().plusSeconds(600)))
                .claim("scope", "orders.read orders.write")
                .build();

        SignedJWT signedJwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .type(JOSEObjectType.JWT)
                        .keyID(signingKey.getKeyID())
                        .build(),
                claimsSet);
        signedJwt.sign(new RSASSASigner(signingKey.toPrivateKey()));
        return signedJwt.serialize();
    }

    private void assertMetricWithTags(String path) {
        ResponseEntity<String> metricResponse = restTemplate.getForEntity(path, String.class);

        assertThat(metricResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(metricResponse.getBody()).contains("availableTags");
        assertThat(metricResponse.getBody()).contains("measurements");
    }
}