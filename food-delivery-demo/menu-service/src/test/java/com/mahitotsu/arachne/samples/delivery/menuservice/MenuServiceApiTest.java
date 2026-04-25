package com.mahitotsu.arachne.samples.delivery.menuservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.web.client.TestRestTemplate;
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
class MenuServiceApiTest {

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
        signingKey = new RSAKeyGenerator(2048).keyID("menu-test-key").generate();
        accessToken = createAccessToken();
        jwkServer = new MockWebServer();
        jwkServer.setDispatcher(jwkDispatcher());
        jwkServer.start();
    }

    @AfterAll
    static void stopServer() throws IOException {
        jwkServer.shutdown();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> jwkServer.url("/oauth2/jwks").toString());
    }

    @BeforeEach
    void prepareAuthenticatedClient() {
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