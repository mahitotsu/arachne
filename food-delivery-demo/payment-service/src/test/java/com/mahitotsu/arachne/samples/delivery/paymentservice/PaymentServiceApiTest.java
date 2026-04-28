package com.mahitotsu.arachne.samples.delivery.paymentservice;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.mahitotsu.arachne.samples.delivery.paymentservice.domain.PaymentTypes.*;
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

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class PaymentServiceApiTest {

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
        signingKey = new RSAKeyGenerator(2048).keyID("payment-test-key").generate();
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
    void rejectsUnauthenticatedPaymentPreparation() {
        TestRestTemplate anonymous = new TestRestTemplate();

        ResponseEntity<String> response = anonymous.postForEntity(
                "http://localhost:" + port + "/internal/payment/prepare",
                new PaymentPrepareRequest("session-unauth", "この内容でApple Payで確定", new BigDecimal("3280.00"), true),
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
                .contains("/internal/payment/prepare")
                .contains("x-ai-prompt-contract")
            .contains("自然言語の支払い指示またはチェックアウト指示");
    }

    @Test
    void chargesWhenTheRequestIsConfirmed() {
        PaymentPrepareResponse response = restTemplate.postForObject(
                "/internal/payment/prepare",
                new PaymentPrepareRequest("session-1", "この内容でApple Payで確定", new BigDecimal("3280.00"), true),
                PaymentPrepareResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.agent()).isEqualTo("payment-service");
        assertThat(response.charged()).isTrue();
        assertThat(response.authorizationId()).isNotBlank();
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