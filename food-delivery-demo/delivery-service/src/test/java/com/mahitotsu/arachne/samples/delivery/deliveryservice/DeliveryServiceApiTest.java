package com.mahitotsu.arachne.samples.delivery.deliveryservice;

import static com.mahitotsu.arachne.samples.delivery.deliveryservice.domain.DeliveryTypes.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
class DeliveryServiceApiTest {

    private static MockWebServer jwkServer;
    private static MockWebServer registryServer;
    private static MockWebServer hermesServer;
    private static MockWebServer idatenServer;
    private static RSAKey signingKey;
    private static String accessToken;
    private static volatile String registryDiscoverBody;
    private static volatile String hermesEtaBody;
    private static volatile int hermesEtaStatus;
    private static volatile String idatenEtaBody;
    private static final AtomicInteger hermesEtaRequests = new AtomicInteger();

    private static final String ISSUER = "http://customer-service";

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @BeforeAll
    static void startServer() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("delivery-test-key").generate();
        accessToken = createAccessToken();
        jwkServer = new MockWebServer();
        jwkServer.setDispatcher(jwkDispatcher());
        jwkServer.start();
        registryServer = new MockWebServer();
        registryServer.setDispatcher(registryDispatcher());
        registryServer.start();
        hermesServer = new MockWebServer();
        hermesServer.setDispatcher(hermesDispatcher());
        hermesServer.start();
        idatenServer = new MockWebServer();
        idatenServer.setDispatcher(idatenDispatcher());
        idatenServer.start();
    }

    @AfterAll
    static void stopServer() throws IOException {
        jwkServer.shutdown();
        registryServer.shutdown();
        hermesServer.shutdown();
        idatenServer.shutdown();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> jwkServer.url("/oauth2/jwks").toString());
        registry.add("DELIVERY_REGISTRY_BASE_URL", () -> registryServer.url("").toString());
    }

    @BeforeEach
    void prepareAuthenticatedClient() {
        restTemplate.getRestTemplate().setInterceptors(List.of((request, body, execution) -> {
            request.getHeaders().setBearerAuth(accessToken);
            return execution.execute(request, body);
        }));
        registryDiscoverBody = registryDiscoverResponse(
                serviceDescriptor("hermes-adapter", hermesServer.url("/adapter/eta").toString()),
                serviceDescriptor("idaten-adapter", idatenServer.url("/adapter/eta").toString()));
        hermesEtaStatus = 200;
        hermesEtaBody = """
                {
                  "service": "hermes-adapter",
                  "status": "AVAILABLE",
                  "etaMinutes": 22,
                  "congestion": "medium",
                  "fee": 350.0,
                  "note": "Hermes は高速配送を提供しています。"
                }
                """;
        idatenEtaBody = """
                {
                  "service": "idaten-adapter",
                  "status": "AVAILABLE",
                  "etaMinutes": 34,
                  "congestion": "low",
                  "fee": 180.0,
                  "note": "Idaten は低価格配送を提供しています。"
                }
                """;
        hermesEtaRequests.set(0);
    }

    @Test
    void rejectsUnauthenticatedDeliveryQuotes() {
        TestRestTemplate anonymous = new TestRestTemplate();

        ResponseEntity<String> response = anonymous.postForEntity(
                "http://localhost:" + port + "/internal/delivery/quote",
                new DeliveryQuoteRequest("session-unauth", "最速配送でお願い", List.of("Crispy Chicken Box")),
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
                .contains("/internal/delivery/quote")
                .contains("x-ai-prompt-contract")
                .contains("Natural-language delivery preference");
    }

    @Test
    void returnsExpressOptionFirstForFastestRequests() {
        DeliveryQuoteResponse response = restTemplate.postForObject(
                "/internal/delivery/quote",
                new DeliveryQuoteRequest("session-1", "最速配送でお願い", List.of("Crispy Chicken Box")),
                DeliveryQuoteResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.agent()).isEqualTo("delivery-agent");
        assertThat(response.options()).extracting(DeliveryOption::code)
            .containsExactly("express", "hermes", "idaten");
        assertThat(response.recommendedTier()).isEqualTo("express");
        assertThat(response.recommendationReason()).contains("急いで");
        assertThat(response.summary()).contains("レジストリ検索", "Hermes スピード便", "Idaten エコノミー");
        }

        @Test
        void prefersCheapestAvailableExternalOptionAndDoesNotCallExcludedHermes() {
        registryDiscoverBody = registryDiscoverResponse(
            serviceDescriptor("idaten-adapter", idatenServer.url("/adapter/eta").toString()));

        DeliveryQuoteResponse response = restTemplate.postForObject(
            "/internal/delivery/quote",
            new DeliveryQuoteRequest("session-2", "できるだけ安く届けて", List.of("Teriyaki Chicken Box")),
            DeliveryQuoteResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.options()).extracting(DeliveryOption::code)
            .containsExactly("idaten", "express");
        assertThat(response.recommendedTier()).isEqualTo("idaten");
        assertThat(response.recommendationReason()).contains("安く");
        assertThat(hermesEtaRequests.get()).isZero();
    }

    @Test
    void actuatorMetricsExposeAgentInvocationAndToolCallsAfterQuote() {
        DeliveryQuoteResponse response = restTemplate.postForObject(
                "/internal/delivery/quote",
                new DeliveryQuoteRequest("session-metrics", "最速配送でお願い", List.of("Crispy Chicken Box")),
                DeliveryQuoteResponse.class);

        ResponseEntity<String> metricsIndex = restTemplate.getForEntity("/actuator/metrics", String.class);

        assertThat(response).isNotNull();
        assertThat(metricsIndex.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(metricsIndex.getBody()).contains("delivery.agent.invocation", "delivery.agent.tool.call", "delivery.agent.usage.tokens");
        assertMetricWithTags("/actuator/metrics/delivery.agent.invocation?tag=service:delivery-service&tag=agent:delivery-agent&tag=outcome:success");
        assertMetricWithTags("/actuator/metrics/delivery.agent.tool.call?tag=service:delivery-service&tag=agent:delivery-agent&tag=tool:discover_eta_services&tag=outcome:success");
        assertMetricWithTags("/actuator/metrics/delivery.agent.usage.tokens?tag=service:delivery-service&tag=agent:delivery-agent&tag=type:input");
        assertMetricWithTags("/actuator/metrics/delivery.agent.usage.tokens?tag=service:delivery-service&tag=agent:delivery-agent&tag=type:output");
        assertMetricWithTags("/actuator/metrics/delivery.delivery.registry.lookup?tag=target:registry-service&tag=operation:discover-eta-services&tag=outcome:success");
        assertMetricWithTags("/actuator/metrics/delivery.delivery.downstream?tag=target:hermes-adapter&tag=operation:quote&tag=outcome:success");
    }

    @Test
    void registersDeliveryRoutingSkillMetadataForRegistryViewer() throws Exception {
        RecordedRequest registration = registryServer.takeRequest();

        assertThat(registration).isNotNull();
        assertThat(registration.getPath()).isEqualTo("/registry/register");
        String body = registration.getBody().readUtf8();
        assertThat(body).contains("delivery-routing");
        assertThat(body).contains("discover_eta_services");
        assertThat(body).contains("call_eta_service");
        assertThat(body).contains("存在しない候補");
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
                if (request.getPath() != null && request.getPath().startsWith("/registry/register")) {
                    return new MockResponse()
                            .setHeader("Content-Type", "application/json")
                            .setBody("""
                                    {
                                      "serviceName": "delivery-service",
                                      "endpoint": "http://delivery-service:8080",
                                      "capability": "配送見積もり",
                                      "agentName": "delivery-agent",
                                      "systemPrompt": "prompt",
                                      "skills": [],
                                      "requestMethod": "POST",
                                      "requestPath": "/internal/delivery/quote",
                                      "status": "AVAILABLE"
                                    }
                                    """);
                }
                if (request.getPath() != null && request.getPath().startsWith("/registry/discover")) {
                    return new MockResponse()
                            .setHeader("Content-Type", "application/json")
                            .setBody(registryDiscoverBody);
                }
                return new MockResponse().setResponseCode(404);
            }
        };
    }

    private static Dispatcher hermesDispatcher() {
        return new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (request.getPath() != null && request.getPath().startsWith("/adapter/eta")) {
                    hermesEtaRequests.incrementAndGet();
                    return new MockResponse()
                            .setResponseCode(hermesEtaStatus)
                            .setHeader("Content-Type", "application/json")
                            .setBody(hermesEtaBody);
                }
                return new MockResponse().setResponseCode(404);
            }
        };
    }

    private static Dispatcher idatenDispatcher() {
        return new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (request.getPath() != null && request.getPath().startsWith("/adapter/eta")) {
                    return new MockResponse()
                            .setHeader("Content-Type", "application/json")
                            .setBody(idatenEtaBody);
                }
                return new MockResponse().setResponseCode(404);
            }
        };
    }

    private static String registryDiscoverResponse(String... descriptors) {
        return """
                {
                  "service": "registry-service",
                  "agent": "capability-registry-agent",
                  "summary": "external eta providers",
                  "matches": [%s]
                }
                """.formatted(String.join(",", descriptors));
    }

    private void assertMetricWithTags(String path) {
        ResponseEntity<String> metricResponse = restTemplate.getForEntity(path, String.class);

        assertThat(metricResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(metricResponse.getBody()).contains("availableTags");
        assertThat(metricResponse.getBody()).contains("measurements");
    }

    private static String serviceDescriptor(String serviceName, String url) {
        return """
                {
                  "serviceName": "%s",
                  "endpoint": "%s",
                  "capability": "外部ETAを提供するサービス",
                  "agentName": "%s",
                  "systemPrompt": "prompt",
                  "skills": [],
                  "requestMethod": "POST",
                  "requestPath": "",
                  "status": "AVAILABLE"
                }
                """.formatted(serviceName, url, serviceName);
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