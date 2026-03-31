package io.arachne.samples.marketplace.workflowservice;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

class WorkflowReplicaRedisContinuityIntegrationTest {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private MockWebServer escrowServer;
    private MockWebServer shipmentServer;
    private MockWebServer riskServer;
    private MockWebServer notificationServer;

    @Test
    void redisBackedWorkflowStateSurvivesAcrossReplicaHandoff() throws Exception {
        Assumptions.assumeTrue(dockerAvailable(),
                "Docker is required for the Redis continuity integration test.");

        startServers();
        GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"));
        redis.withExposedPorts(6379);

        try (redis) {
            redis.start();

            try (RunningReplica firstReplica = startReplica(redis, "replica-1");
                 RunningReplica secondReplica = startReplica(redis, "replica-2")) {
                enqueueStandardEvidenceResponses(BigDecimal.valueOf(49.95), "USD");

                JsonNode startResponse = postJson(
                        firstReplica.baseUrl(),
                        "/internal/workflows",
                        startWorkflowRequest("case-replica-handoff", BigDecimal.valueOf(49.95), "USD"));

                assertThat(startResponse.path("workflowStatus").asText()).isEqualTo("AWAITING_APPROVAL");
                assertThat(startResponse.path("currentRecommendation").asText()).isEqualTo("REFUND");
                assertThat(startResponse.path("approvalState").path("approvalStatus").asText())
                        .isEqualTo("PENDING_FINANCE_CONTROL");

                drainStartRequests();

                JsonNode continueResponse = postJson(
                        secondReplica.baseUrl(),
                        "/internal/workflows/case-replica-handoff/messages",
                        """
                                {
                                  "message": "Keep going on the same case.",
                                  "operatorId": "operator-1",
                                  "operatorRole": "CASE_OPERATOR",
                                  "requestedAt": "2026-03-30T12:03:00Z"
                                }
                                """);

                assertThat(continueResponse.path("currentRecommendation").asText()).isEqualTo("REFUND");
                assertThat(continueResponse.path("evidence").path("shipmentEvidence").asText())
                        .contains("TRACK-order-1001");
                assertThat(continueResponse.path("approvalState").path("requestedRole").asText())
                        .isEqualTo("FINANCE_CONTROL");

                assertThat(shipmentServer.takeRequest(100, TimeUnit.MILLISECONDS)).isNull();
                assertThat(escrowServer.takeRequest(100, TimeUnit.MILLISECONDS)).isNull();
                assertThat(riskServer.takeRequest(100, TimeUnit.MILLISECONDS)).isNull();

                enqueueJson(escrowServer, new DownstreamContracts.SettlementOutcome(
                        "REFUND_EXECUTED",
                        "SUCCEEDED",
                        OffsetDateTime.parse("2026-03-30T12:05:01Z"),
                        "refund-case-replica-handoff",
                        "Escrow executed a refund after finance control approval."));
                enqueueJson(notificationServer, new DownstreamContracts.NotificationDispatchResult(
                        "QUEUED",
                        "PENDING_DELIVERY",
                        "Notification service queued participant and operator notifications."));

                JsonNode approvalResponse = postJson(
                        secondReplica.baseUrl(),
                        "/internal/workflows/case-replica-handoff/approvals",
                        """
                                {
                                  "decision": "APPROVE",
                                  "comment": "Refund the buyer.",
                                  "actorId": "finance-7",
                                  "actorRole": "FINANCE_CONTROL",
                                  "requestedAt": "2026-03-30T12:05:00Z"
                                }
                                """);

                assertThat(approvalResponse.path("workflowStatus").asText()).isEqualTo("COMPLETED");
                assertThat(approvalResponse.path("currentRecommendation").asText()).isEqualTo("REFUND");
                assertThat(approvalResponse.path("outcome").path("outcomeType").asText())
                        .isEqualTo("REFUND_EXECUTED");
                assertThat(approvalResponse.path("approvalState").path("decisionBy").asText())
                        .isEqualTo("finance-7");

                RecordedRequest settlementRequest = escrowServer.takeRequest(1, TimeUnit.SECONDS);
                assertThat(settlementRequest).isNotNull();
                if (settlementRequest == null) {
                    throw new AssertionError("Expected settlement request to be recorded");
                }
                assertThat(settlementRequest.getBody().readUtf8())
                        .contains("REFUND", "49.95", "USD", "case-replica-handoff");
                assertThat(notificationServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
            }
        }
        finally {
            stopServers();
        }
    }

    private RunningReplica startReplica(GenericContainer<?> redis, String replicaName) {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(WorkflowServiceApplication.class)
            .run(
                "--server.port=0",
                "--spring.application.name=marketplace-workflow-service-" + replicaName,
                "--workflow-session.store=redis",
                "--spring.data.redis.host=" + redis.getHost(),
                "--spring.data.redis.port=" + redis.getMappedPort(6379),
                "--downstream.escrow-base-url=" + escrowServer.url("/").toString(),
                "--downstream.shipment-base-url=" + shipmentServer.url("/").toString(),
                "--downstream.risk-base-url=" + riskServer.url("/").toString(),
                "--downstream.notification-base-url=" + notificationServer.url("/").toString());
        int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
        return new RunningReplica(context, "http://127.0.0.1:" + port);
    }

    private JsonNode postJson(String baseUrl, String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return OBJECT_MAPPER.readTree(response.body());
    }

    private void startServers() throws IOException {
        escrowServer = new MockWebServer();
        shipmentServer = new MockWebServer();
        riskServer = new MockWebServer();
        notificationServer = new MockWebServer();
        escrowServer.start();
        shipmentServer.start();
        riskServer.start();
        notificationServer.start();
    }

    private void stopServers() throws IOException {
        if (escrowServer != null) {
            escrowServer.shutdown();
        }
        if (shipmentServer != null) {
            shipmentServer.shutdown();
        }
        if (riskServer != null) {
            riskServer.shutdown();
        }
        if (notificationServer != null) {
            notificationServer.shutdown();
        }
    }

    private void enqueueStandardEvidenceResponses(BigDecimal amount, String currency) throws Exception {
        enqueueJson(shipmentServer, new DownstreamContracts.ShipmentEvidenceSummary(
                "TRACK-order-1001",
                "Carrier tracking shows label creation and in-transit milestones but no final delivery scan.",
                "LOW",
                "Shipment remains in a not-delivered state for the current case."));
        enqueueJson(escrowServer, new DownstreamContracts.EscrowEvidenceSummary(
                "HELD",
                "ELIGIBLE_FOR_CONTINUED_HOLD",
                amount,
                currency,
                "NO_PRIOR_REFUND",
                "Escrow still holds the authorized funds and no prior refund has been executed."));
        enqueueJson(riskServer, new DownstreamContracts.RiskReviewSummary(
                "No elevated fraud signal detected for the current order.",
                true,
                java.util.List.of("FINANCE_CONTROL_REVIEW_REQUIRED"),
                "Risk review found no elevated fraud signal but requires finance control confirmation for settlement-changing actions."));
    }

    private String startWorkflowRequest(String caseId, BigDecimal amount, String currency) {
        return """
                {
                  "caseId": "%s",
                  "caseType": "ITEM_NOT_RECEIVED",
                  "orderId": "order-1001",
                  "amount": %s,
                  "currency": "%s",
                  "initialMessage": "Buyer reports item not received.",
                  "operatorId": "operator-1",
                  "operatorRole": "CASE_OPERATOR",
                  "requestedAt": "2026-03-30T12:00:00Z"
                }
                """.formatted(caseId, amount, currency);
    }

    private void drainStartRequests() throws InterruptedException {
        assertThat(shipmentServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
        assertThat(escrowServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
        assertThat(riskServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
    }

    private void enqueueJson(MockWebServer server, Object body) throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody(OBJECT_MAPPER.writeValueAsString(body)));
    }

    private boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        }
        catch (IllegalStateException exception) {
            return false;
        }
    }

    private record RunningReplica(ConfigurableApplicationContext context, String baseUrl) implements AutoCloseable {

        @Override
        public void close() {
            context.close();
        }
    }
}