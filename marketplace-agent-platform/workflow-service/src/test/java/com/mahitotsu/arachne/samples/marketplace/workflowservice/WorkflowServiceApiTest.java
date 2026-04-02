package com.mahitotsu.arachne.samples.marketplace.workflowservice;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

@SpringBootTest
@AutoConfigureMockMvc
class WorkflowServiceApiTest {

    private static MockWebServer escrowServer;
    private static MockWebServer shipmentServer;
    private static MockWebServer riskServer;
    private static MockWebServer notificationServer;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    static void startServers() throws IOException {
        escrowServer = new MockWebServer();
        shipmentServer = new MockWebServer();
        riskServer = new MockWebServer();
        notificationServer = new MockWebServer();
        escrowServer.start();
        shipmentServer.start();
        riskServer.start();
        notificationServer.start();
    }

    @AfterAll
    static void stopServers() throws IOException {
        escrowServer.shutdown();
        shipmentServer.shutdown();
        riskServer.shutdown();
        notificationServer.shutdown();
    }

    @DynamicPropertySource
    static void registerBaseUrls(DynamicPropertyRegistry registry) {
        registry.add("downstream.escrow-base-url", () -> escrowServer.url("/").toString());
        registry.add("downstream.shipment-base-url", () -> shipmentServer.url("/").toString());
        registry.add("downstream.risk-base-url", () -> riskServer.url("/").toString());
        registry.add("downstream.notification-base-url", () -> notificationServer.url("/").toString());
        registry.add("workflow-session.store", () -> "memory");
    }

    @Test
    void startWorkflowReturnsStructuredUpdate() throws Exception {
        enqueueStandardEvidenceResponses(BigDecimal.valueOf(149.95), "USD");

        mockMvc.perform(post("/internal/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(startWorkflowRequest("case-1", BigDecimal.valueOf(149.95), "USD")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowStatus").value("AWAITING_APPROVAL"))
                .andExpect(jsonPath("$.currentRecommendation").value("CONTINUED_HOLD"))
            .andExpect(jsonPath("$.approvalState.approvalStatus").value("PENDING_FINANCE_CONTROL"))
            .andExpect(jsonPath("$.activities[0].source").value("workflow-service"))
            .andExpect(jsonPath("$.evidence.shipmentEvidence").value(org.hamcrest.Matchers.containsString("TRACK-order-1001")));

        assertThat(shipmentServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
        assertThat(escrowServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
        assertThat(riskServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void startWorkflowReturnsRefundRecommendationForLowerValueItemNotReceivedCase() throws Exception {
        enqueueStandardEvidenceResponses(BigDecimal.valueOf(49.95), "USD");

        mockMvc.perform(post("/internal/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(startWorkflowRequest("case-refund-start", BigDecimal.valueOf(49.95), "USD")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowStatus").value("AWAITING_APPROVAL"))
                .andExpect(jsonPath("$.currentRecommendation").value("REFUND"))
                .andExpect(jsonPath("$.approvalState.approvalStatus").value("PENDING_FINANCE_CONTROL"));

        assertThat(shipmentServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
        assertThat(escrowServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
        assertThat(riskServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
        void continueWorkflowUsesPersistedSessionState() throws Exception {
        enqueueStandardEvidenceResponses(BigDecimal.valueOf(149.95), "USD");

        mockMvc.perform(post("/internal/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(startWorkflowRequest("case-continue", BigDecimal.valueOf(149.95), "USD")))
            .andExpect(status().isOk());

        drainStartRequests();

        mockMvc.perform(post("/internal/workflows/{caseId}/messages", "case-continue")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "message": "Please keep going.",
                      "operatorId": "operator-1",
                      "operatorRole": "CASE_OPERATOR",
                      "requestedAt": "2026-03-30T12:03:00Z"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.evidence.shipmentEvidence").value(org.hamcrest.Matchers.containsString("TRACK-order-1001")))
                    .andExpect(jsonPath("$.approvalState.requestedRole").value("FINANCE_CONTROL"))
                    .andExpect(jsonPath("$.activities[0].kind").value("OPERATOR_INSTRUCTION_RECEIVED"))
                    .andExpect(jsonPath("$.activities[1].kind").value("AGENT_RESPONSE"))
                    .andExpect(jsonPath("$.activities[2].kind").value("OPERATOR_REQUEST_COMPLETED"))
                    .andExpect(jsonPath("$.activities[*].message", hasItem(containsString("kept the case on the continued hold path"))))
                    .andExpect(jsonPath("$.activities[0].structuredPayload").value(containsString("shipment-agent")));

        assertThat(shipmentServer.takeRequest(100, TimeUnit.MILLISECONDS)).isNull();
        assertThat(escrowServer.takeRequest(100, TimeUnit.MILLISECONDS)).isNull();
        assertThat(riskServer.takeRequest(100, TimeUnit.MILLISECONDS)).isNull();
        }

        @Test
        void approvalResumeUsesPersistedSessionAmountAndCurrency() throws Exception {
        enqueueStandardEvidenceResponses(BigDecimal.valueOf(200.50), "EUR");

        mockMvc.perform(post("/internal/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(startWorkflowRequest("case-approve", BigDecimal.valueOf(200.50), "EUR")))
            .andExpect(status().isOk());

        drainStartRequests();

        enqueueJson(escrowServer, new DownstreamContracts.SettlementOutcome(
                "CONTINUED_HOLD_RECORDED",
                "SUCCEEDED",
                OffsetDateTime.parse("2026-03-30T12:05:01Z"),
            "hold-case-approve",
                "Escrow recorded the continued hold after finance control approval."));
        enqueueJson(notificationServer, new DownstreamContracts.NotificationDispatchResult(
                "QUEUED",
                "PENDING_DELIVERY",
                "Notification service queued participant and operator notifications."));

        mockMvc.perform(post("/internal/workflows/{caseId}/approvals", "case-approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "comment": "Proceed.",
                                  "actorId": "finance-1",
                                  "actorRole": "FINANCE_CONTROL",
                                  "requestedAt": "2026-03-30T12:05:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.outcome.outcomeType").value("CONTINUED_HOLD_RECORDED"));

        RecordedRequest settlementRequest = escrowServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(settlementRequest).isNotNull();
        if (settlementRequest == null) {
            throw new AssertionError("Expected settlement request to be recorded");
        }
        assertThat(settlementRequest.getBody().readUtf8()).contains("200.5", "EUR", "case-approve");
        assertThat(notificationServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void approvalResumeExecutesRefundWhenWorkflowRecommendedRefund() throws Exception {
        enqueueStandardEvidenceResponses(BigDecimal.valueOf(49.95), "USD");

        mockMvc.perform(post("/internal/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(startWorkflowRequest("case-refund", BigDecimal.valueOf(49.95), "USD")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentRecommendation").value("REFUND"));

        drainStartRequests();

        enqueueJson(escrowServer, new DownstreamContracts.SettlementOutcome(
                "REFUND_EXECUTED",
                "SUCCEEDED",
                OffsetDateTime.parse("2026-03-30T12:05:01Z"),
                "refund-case-refund",
                "Escrow executed a refund after finance control approval."));
        enqueueJson(notificationServer, new DownstreamContracts.NotificationDispatchResult(
                "QUEUED",
                "PENDING_DELIVERY",
                "Notification service queued participant and operator notifications."));

        mockMvc.perform(post("/internal/workflows/{caseId}/approvals", "case-refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "comment": "Refund the buyer.",
                                  "actorId": "finance-3",
                                  "actorRole": "FINANCE_CONTROL",
                                  "requestedAt": "2026-03-30T12:05:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.currentRecommendation").value("REFUND"))
                .andExpect(jsonPath("$.outcome.outcomeType").value("REFUND_EXECUTED"));

        RecordedRequest settlementRequest = escrowServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(settlementRequest).isNotNull();
        if (settlementRequest == null) {
            throw new AssertionError("Expected refund settlement request to be recorded");
        }
        assertThat(settlementRequest.getBody().readUtf8()).contains("REFUND", "49.95", "USD", "case-refund");
        assertThat(notificationServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void approvalRejectionReturnsWorkflowToEvidenceGatheringWithoutSettlement() throws Exception {
        enqueueStandardEvidenceResponses(BigDecimal.valueOf(149.95), "USD");

        mockMvc.perform(post("/internal/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(startWorkflowRequest("case-reject", BigDecimal.valueOf(149.95), "USD")))
            .andExpect(status().isOk());

        drainStartRequests();

        mockMvc.perform(post("/internal/workflows/{caseId}/approvals", "case-reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "REJECT",
                                  "comment": "Need more shipment evidence before settlement.",
                                  "actorId": "finance-2",
                                  "actorRole": "FINANCE_CONTROL",
                                  "requestedAt": "2026-03-30T12:06:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowStatus").value("GATHERING_EVIDENCE"))
                .andExpect(jsonPath("$.currentRecommendation").value("PENDING_MORE_EVIDENCE"))
                .andExpect(jsonPath("$.approvalState.approvalStatus").value("REJECTED"))
                .andExpect(jsonPath("$.approvalState.comment").value("Need more shipment evidence before settlement."))
                .andExpect(jsonPath("$.outcome").value(nullValue()))
                .andExpect(jsonPath("$.message").value("Approval rejection accepted and workflow returned to evidence gathering."));

        assertThat(escrowServer.takeRequest(100, TimeUnit.MILLISECONDS)).isNull();
        assertThat(notificationServer.takeRequest(100, TimeUnit.MILLISECONDS)).isNull();
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
                .setBody(objectMapper.writeValueAsString(body)));
    }
}