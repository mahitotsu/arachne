package com.mahitotsu.arachne.samples.delivery.supportservice;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static com.mahitotsu.arachne.samples.delivery.testsupport.MockWebServerTestSupport.drainRequests;
import static com.mahitotsu.arachne.samples.delivery.testsupport.MockWebServerTestSupport.recordedPaths;
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

import com.mahitotsu.arachne.samples.delivery.supportservice.api.SupportChatRequest;
import com.mahitotsu.arachne.samples.delivery.supportservice.api.SupportChatResponse;
import com.mahitotsu.arachne.samples.delivery.supportservice.api.SupportFeedbackRequest;
import com.mahitotsu.arachne.samples.delivery.supportservice.api.SupportFeedbackResponse;
import com.mahitotsu.arachne.samples.delivery.supportservice.api.SupportStatusResponse;
import com.mahitotsu.arachne.samples.delivery.supportservice.domain.SupportExecutionHistoryTypes.SupportExecutionHistoryEvent;
import com.mahitotsu.arachne.samples.delivery.supportservice.domain.SupportExecutionHistoryTypes.SupportExecutionHistoryResponse;
import com.mahitotsu.arachne.samples.delivery.supportservice.infrastructure.RegistryServiceEndpointResolver;
import com.mahitotsu.arachne.samples.delivery.supportservice.domain.CampaignSummary;
import com.mahitotsu.arachne.samples.delivery.supportservice.domain.CustomerOrderHistoryEntry;
import com.mahitotsu.arachne.samples.delivery.supportservice.domain.ServiceHealthSummary;
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
class SupportServiceApiTest {

    private static MockWebServer jwkServer;
    private static MockWebServer registryServer;
    private static MockWebServer orderServer;
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
        signingKey = new RSAKeyGenerator(2048).keyID("support-test-key").generate();
        accessToken = createAccessToken();
        jwkServer = new MockWebServer();
        jwkServer.setDispatcher(jwkDispatcher());
        registryServer = new MockWebServer();
        registryServer.setDispatcher(registryDispatcher());
        orderServer = new MockWebServer();
        orderServer.setDispatcher(orderDispatcher());
        jwkServer.start();
        registryServer.start();
        orderServer.start();
    }

    @AfterAll
    static void stopServer() throws IOException {
        jwkServer.shutdown();
        registryServer.shutdown();
        orderServer.shutdown();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> jwkServer.url("/oauth2/jwks").toString());
        registry.add("DELIVERY_REGISTRY_BASE_URL", () -> registryServer.url("/").toString());
        registry.add("ORDER_SERVICE_NAME", () -> "order-service");
    }

    @BeforeEach
    void prepareAuthenticatedClient() throws InterruptedException {
        endpointResolver.clearCache();
        drainRequests(registryServer);
        drainRequests(orderServer);
        restTemplate.getRestTemplate().setInterceptors(List.of((request, body, execution) -> {
            request.getHeaders().setBearerAuth(accessToken);
            return execution.execute(request, body);
        }));
    }

    @Test
    void rejectsUnauthenticatedSupportChatRequests() {
        TestRestTemplate anonymous = new TestRestTemplate();

        ResponseEntity<String> response = anonymous.postForEntity(
                "http://localhost:" + port + "/api/support/chat",
                new SupportChatRequest("session-unauth", "キャンペーンを教えて"),
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
                .contains("/api/support/chat")
                .contains("x-ai-prompt-contract")
            .contains("自然言語の問い合わせ")
                .contains("List active campaigns");
    }

    @Test
    void chatReturnsCampaignsStatusesAndRecentOrdersForAuthenticatedCustomer() throws InterruptedException {
        SupportChatResponse response = restTemplate.postForObject(
                "/api/support/chat",
                new SupportChatRequest("session-support", "使えるキャンペーンと配送の稼働状況、最近の注文を教えて"),
                SupportChatResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.agent()).isEqualTo("support-agent");
        assertThat(response.campaigns()).extracting(CampaignSummary::title)
                .contains("雨の日ポイント2倍");
        assertThat(response.serviceStatuses()).extracting(ServiceHealthSummary::serviceName, ServiceHealthSummary::status)
                .contains(org.assertj.core.groups.Tuple.tuple("delivery-service", "AVAILABLE"));
        assertThat(response.recentOrders()).extracting(CustomerOrderHistoryEntry::orderId)
                .contains("ord-1001");
        assertThat(response.summary()).contains("雨の日ポイント2倍", "delivery-service", "照り焼き");
        assertThat(recordedPaths(registryServer)).anyMatch(path -> path.startsWith("/registry/services"));
        assertThat(recordedPaths(orderServer)).anyMatch(path -> path.startsWith("/api/orders/history"));
    }

    @Test
    void feedbackEndpointClassifiesDelayComplaintsAndMarksEscalation() {
        SupportFeedbackResponse response = restTemplate.postForObject(
                "/api/support/feedback",
                new SupportFeedbackRequest("ord-1001", 2, "配送が遅くてポテトも冷めていました"),
                SupportFeedbackResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.classification()).isEqualTo("DELAY");
        assertThat(response.escalationRequired()).isTrue();
        assertThat(response.summary()).contains("エスカレーション");
    }

    @Test
    void actuatorMetricsExposeAgentInvocationAndToolCallsAfterChat() {
        SupportChatResponse response = restTemplate.postForObject(
                "/api/support/chat",
                new SupportChatRequest("session-metrics", "使えるキャンペーンを教えて"),
                SupportChatResponse.class);

        ResponseEntity<String> metricsIndex = restTemplate.getForEntity("/actuator/metrics", String.class);

        assertThat(response).isNotNull();
        assertThat(metricsIndex.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(metricsIndex.getBody()).contains("delivery.agent.invocation", "delivery.agent.tool.call", "delivery.agent.usage.tokens");
        assertMetricWithTags("/actuator/metrics/delivery.agent.invocation?tag=service:support-service&tag=agent:support-agent&tag=outcome:success");
        assertMetricWithTags("/actuator/metrics/delivery.agent.tool.call?tag=service:support-service&tag=agent:support-agent&tag=tool:campaign_lookup&tag=outcome:success");
        assertMetricWithTags("/actuator/metrics/delivery.agent.usage.tokens?tag=service:support-service&tag=agent:support-agent&tag=type:input");
        assertMetricWithTags("/actuator/metrics/delivery.agent.usage.tokens?tag=service:support-service&tag=agent:support-agent&tag=type:output");
        assertMetricWithTags("/actuator/metrics/delivery.support.downstream?tag=target:order-service&tag=operation:recent-orders&tag=outcome:success");
        assertMetricWithTags("/actuator/metrics/delivery.support.registry.lookup?tag=target:registry-service&tag=operation:resolve-endpoint&tag=outcome:success");
        assertMetricWithTags("/actuator/metrics/delivery.support.registry.lookup?tag=target:registry-service&tag=operation:health-status&tag=outcome:success");
    }

        @Test
        void executionHistoryCapturesSupportAgentAndFeedbackEvents() {
        SupportChatResponse chatResponse = restTemplate.postForObject(
            "/api/support/chat",
            new SupportChatRequest("session-history", "使えるキャンペーンを教えて"),
            SupportChatResponse.class);
        SupportFeedbackResponse feedbackResponse = restTemplate.postForObject(
            "/api/support/feedback",
            new SupportFeedbackRequest("session-history", "ord-1001", 2, "配送が遅くてポテトも冷めていました"),
            SupportFeedbackResponse.class);

        ResponseEntity<SupportExecutionHistoryResponse> historyResponse = restTemplate.getForEntity(
            "/api/support/execution-history/session-history",
            SupportExecutionHistoryResponse.class);

        assertThat(chatResponse).isNotNull();
        assertThat(feedbackResponse).isNotNull();
        assertThat(historyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(historyResponse.getBody()).isNotNull();
        assertThat(historyResponse.getBody().events())
            .extracting(SupportExecutionHistoryEvent::category)
            .contains("agent", "model", "tool", "service");
        assertThat(historyResponse.getBody().events())
            .extracting(SupportExecutionHistoryEvent::operation, SupportExecutionHistoryEvent::outcome)
            .contains(org.assertj.core.groups.Tuple.tuple("feedback", "success"));
        }

    private static Dispatcher jwkDispatcher() {
        return new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path != null && path.startsWith("/oauth2/jwks")) {
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
                                      "serviceName": "support-service",
                                      "endpoint": "http://support-service:8080",
                                      "capability": "FAQ回答、キャンペーン案内、問い合わせ受付、サービス稼働状況共有を扱う。",
                                      "agentName": "support-agent",
                                      "systemPrompt": "FAQ、キャンペーン、問い合わせ、稼働状況を整理し、必要なら注文履歴も参照して案内する。",
                                      "skills": [],
                                      "requestMethod": "POST",
                                      "requestPath": "/api/support/chat",
                                      "status": "AVAILABLE"
                                    }
                                    """);
                }
                if (path != null && path.startsWith("/registry/health")) {
                    return new MockResponse()
                            .setHeader("Content-Type", "application/json")
                            .setBody("""
                                    {
                                      "services": [
                                        {
                                          "serviceName": "delivery-service",
                                          "status": "AVAILABLE",
                                          "healthEndpoint": "http://delivery-service:8080/actuator/health"
                                        },
                                        {
                                          "serviceName": "payment-service",
                                          "status": "AVAILABLE",
                                          "healthEndpoint": "http://payment-service:8080/actuator/health"
                                        }
                                      ]
                                    }
                                    """);
                }
                                if (path != null && path.startsWith("/registry/services")) {
                                        return new MockResponse()
                                                        .setHeader("Content-Type", "application/json")
                                                        .setBody("""
                                                                        [
                                                                            {
                                                                                "serviceName": "order-service",
                                                                                "endpoint": "%s",
                                                                                "status": "AVAILABLE"
                                                                            }
                                                                        ]
                                                                        """.formatted(orderServer.url("/").toString().replaceAll("/$", "")));
                                }
                return new MockResponse().setResponseCode(404);
            }
        };
    }

    private static Dispatcher orderDispatcher() {
        return new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path != null && path.startsWith("/api/orders/history")) {
                    return new MockResponse()
                            .setHeader("Content-Type", "application/json")
                            .setBody("""
                                    [
                                      {
                                        "orderId": "ord-1001",
                                        "itemSummary": "照り焼きチキンセット x1",
                                        "total": 1420.00,
                                        "etaLabel": "18 分・自社エクスプレス",
                                        "paymentStatus": "CHARGED",
                                        "createdAt": "2026-04-25T08:30:00Z"
                                      }
                                    ]
                                    """);
                }
                return new MockResponse().setResponseCode(404);
            }
        };
    }

    private static String createAccessToken() throws JOSEException {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("cust-demo-001")
                .audience("food-delivery-demo")
                .issueTime(java.util.Date.from(now))
                .expirationTime(java.util.Date.from(now.plusSeconds(3600)))
                .claim("scope", "orders.read orders.write profile.read")
                .claim("preferred_username", "demo")
                .claim("name", "Aoi Sato")
                .build();

        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .type(JOSEObjectType.JWT)
                        .keyID(signingKey.getKeyID())
                        .build(),
                claims);
        jwt.sign(new RSASSASigner(signingKey.toPrivateKey()));
        return jwt.serialize();
    }

    private void assertMetricWithTags(String path) {
        ResponseEntity<String> metricResponse = restTemplate.getForEntity(path, String.class);

        assertThat(metricResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(metricResponse.getBody()).contains("availableTags");
        assertThat(metricResponse.getBody()).contains("measurements");
    }

}