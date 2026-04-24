package com.mahitotsu.arachne.samples.delivery.kitchenservice;

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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.web.client.TestRestTemplate;
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

    private static MockWebServer menuServer;
    private static MockWebServer jwkServer;
    private static RSAKey signingKey;
    private static String accessToken;

    private static final String ISSUER = "http://customer-service";

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @BeforeAll
    static void startServer() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("kitchen-test-key").generate();
        accessToken = createAccessToken();
        menuServer = new MockWebServer();
        jwkServer = new MockWebServer();
        jwkServer.setDispatcher(jwkDispatcher());
        menuServer.start();
        jwkServer.start();
    }

    @AfterAll
    static void stopServer() throws IOException {
        menuServer.shutdown();
        jwkServer.shutdown();
    }

    @BeforeEach
    void prepareAuthenticatedClient() throws InterruptedException {
        drainRequests(menuServer);
        restTemplate.getRestTemplate().setInterceptors(List.of((request, body, execution) -> {
            request.getHeaders().setBearerAuth(accessToken);
            return execution.execute(request, body);
        }));
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("MENU_SERVICE_BASE_URL", () -> menuServer.url("/").toString());
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
        assertThat(requireRequest(menuServer).getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + accessToken);
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.available()).isFalse();
            assertThat(item.substituteName()).isEqualTo("Nugget Share Box");
        });
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

    private static RecordedRequest requireRequest(MockWebServer server) {
        try {
            RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            return request;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Expected downstream request", ex);
        }
    }

    private static void drainRequests(MockWebServer server) throws InterruptedException {
        while (server.getRequestCount() > 0) {
            if (server.takeRequest(100, TimeUnit.MILLISECONDS) == null) {
                break;
            }
        }
    }
}