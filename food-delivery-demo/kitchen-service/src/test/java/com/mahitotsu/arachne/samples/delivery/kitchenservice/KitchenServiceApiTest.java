package com.mahitotsu.arachne.samples.delivery.kitchenservice;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.mahitotsu.arachne.samples.delivery.testsupport.MockWebServerTestSupport.drainRequests;
import static com.mahitotsu.arachne.samples.delivery.testsupport.MockWebServerTestSupport.recordedPaths;
import static com.mahitotsu.arachne.samples.delivery.testsupport.MockWebServerTestSupport.trimTrailingSlash;
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
        properties = {"delivery.model.mode=deterministic"})
class KitchenServiceApiTest {

    private static final String MENU_SUBSTITUTION_META_PATH = "/meta/menu-substitutes";

    private static MockWebServer menuServer;
    private static MockWebServer registryServer;
    private static MockWebServer jwkServer;
    private static RSAKey signingKey;
    private static String accessToken;

    private static final String ISSUER = "http://customer-service";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private KitchenRepository kitchenRepository;

    @Autowired
    private RegistryServiceEndpointResolver endpointResolver;

    @LocalServerPort
    private int port;

    @BeforeAll
    static void startServer() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("kitchen-test-key").generate();
        accessToken = createAccessToken();
        menuServer = new MockWebServer();
        registryServer = new MockWebServer();
        registryServer.setDispatcher(registryDispatcher());
        jwkServer = new MockWebServer();
        jwkServer.setDispatcher(jwkDispatcher());
        menuServer.start();
        registryServer.start();
        jwkServer.start();
    }

    @AfterAll
    static void stopServer() throws IOException {
        menuServer.shutdown();
        registryServer.shutdown();
        jwkServer.shutdown();
    }

    @BeforeEach
    void prepareAuthenticatedClient() throws InterruptedException {
        endpointResolver.clearCache();
        drainRequests(registryServer);
        drainRequests(menuServer);
        restTemplate.getRestTemplate().setInterceptors(List.of((request, body, execution) -> {
            request.getHeaders().setBearerAuth(accessToken);
            return execution.execute(request, body);
        }));
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("DELIVERY_REGISTRY_BASE_URL", () -> registryServer.url("/").toString());
        registry.add("MENU_SERVICE_NAME", () -> "legacy-menu-service");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> jwkServer.url("/oauth2/jwks").toString());
    }

    @Test
    void rejectsUnauthenticatedKitchenChecks() {
        TestRestTemplate anonymous = new TestRestTemplate();

        ResponseEntity<String> response = anonymous.postForEntity(
                "http://localhost:" + port + "/internal/kitchen/check",
                new KitchenCheckRequest("session-unauth", "ポテトも付けて", List.of("side-fries")),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void reportsSubstitutionWhenItemIsUnavailable() throws InterruptedException {
        menuServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "service": "menu-service",
                          "agent": "menu-agent",
                          "headline": "menu-agent prepared 2 fallback options",
                          "summary": "menu-agent suggested Nugget Share Box and Garden Wrap for kitchen-agent to validate.",
                          "items": [
                                                        {"id": "side-nuggets", "name": "Nugget Share Box", "description": "Ten-piece nugget box with sauces.", "price": 640.0, "suggestedQuantity": 1, "category": "side", "tags": ["share", "chicken"]},
                                                        {"id": "wrap-garden", "name": "Garden Wrap", "description": "Fresh veggie wrap with yogurt sauce.", "price": 760.0, "suggestedQuantity": 1, "category": "wrap", "tags": ["veggie"]}
                          ]
                        }
                        """));

        KitchenCheckResponse response = restTemplate.postForObject(
                "/internal/kitchen/check",
                new KitchenCheckRequest("session-1", "ポテトも付けて", List.of("side-fries")),
                KitchenCheckResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.agent()).isEqualTo("kitchen-agent");
        assertThat(response.summary()).contains("menu-agent");
        assertThat(response.collaborations()).singleElement().satisfies(trace -> {
            assertThat(trace.service()).isEqualTo("menu-service/support");
            assertThat(trace.agent()).isEqualTo("menu-agent");
        });
        assertThat(recordedPaths(registryServer)).anyMatch(path -> path.startsWith("/registry/services"));
        RecordedRequest menuRequest = menuServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(menuRequest).isNotNull();
        assertThat(menuRequest.getPath()).isEqualTo(MENU_SUBSTITUTION_META_PATH);
        assertThat(menuRequest.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + accessToken);
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.available()).isFalse();
            assertThat(item.substituteName()).isEqualTo("Nugget Share Box");
        });
    }

    @Test
    void warnsAboutGrillCongestionAndSuggestsAssemblyAlternatives() {
        kitchenRepository.setQueueDepth("grill", 4);

        KitchenCheckResponse response = restTemplate.postForObject(
                "/internal/kitchen/check",
                new KitchenCheckRequest("session-rush", "ランチで急ぎです", List.of("combo-smash")),
                KitchenCheckResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.readyInMinutes()).isGreaterThan(30);
        assertThat(response.summary())
                .contains("grill-line")
                .contains("assembly")
                .contains("サーモン丼");
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
                                      "serviceName": "kitchen-service",
                                      "endpoint": "http://kitchen-service:8080",
                                      "status": "AVAILABLE"
                                    }
                                    """);
                }
                if (path != null && path.startsWith("/registry/services")) {
                    return new MockResponse()
                            .setHeader("Content-Type", "application/json")
                            .setBody("""
                                    [
                                                {"serviceName": "legacy-menu-service", "endpoint": "%s", "capability": "メニュー提案、問い合わせ受付、欠品時の代替候補提示を扱う。", "agentName": "menu-agent", "requestMethod": "POST", "requestPath": "%s", "status": "AVAILABLE"}
                                    ]
                                                                        """.formatted(trimTrailingSlash(menuServer.url("/").toString()), MENU_SUBSTITUTION_META_PATH));
                }
                return new MockResponse().setResponseCode(404);
            }
        };
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

}