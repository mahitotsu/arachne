package com.mahitotsu.arachne.samples.marketplace.workflowservice;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
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

@SpringBootTest(properties = {
        "marketplace.workflow-runtime.arachne.enabled=true",
        "marketplace.workflow-runtime.arachne.model-mode=deterministic"
})
@AutoConfigureMockMvc
class WorkflowServiceArachneApiTest {

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
    void startWorkflowUsesNamedAgentsSkillsAndResourceToolsWhenEnabled() throws Exception {
        enqueueStandardEvidenceResponses(BigDecimal.valueOf(149.95), "USD");

        mockMvc.perform(post("/internal/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startWorkflowRequest("case-arachne", BigDecimal.valueOf(149.95), "USD")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowStatus").value("AWAITING_APPROVAL"))
                .andExpect(jsonPath("$.currentRecommendation").value("CONTINUED_HOLD"))
                .andExpect(jsonPath("$.activities[0].source").value("case-workflow-agent"))
                .andExpect(jsonPath("$.activities[1].source").value("shipment-agent"))
                .andExpect(jsonPath("$.activities[2].source").value("escrow-agent"))
                .andExpect(jsonPath("$.activities[3].source").value("risk-agent"))
                .andExpect(jsonPath("$.evidence.policyReference").value("policy://marketplace/disputes/item-not-received"));

        assertThat(shipmentServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
        assertThat(escrowServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
        assertThat(riskServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void startWorkflowStillReturnsRefundRecommendationOnEnabledArachnePath() throws Exception {
        enqueueStandardEvidenceResponses(BigDecimal.valueOf(49.95), "USD");

        mockMvc.perform(post("/internal/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startWorkflowRequest("case-arachne-refund", BigDecimal.valueOf(49.95), "USD")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentRecommendation").value("REFUND"))
                .andExpect(jsonPath("$.activities[*].kind", hasItem("SETTLEMENT_SHORTCUT_ATTEMPTED")))
                .andExpect(jsonPath("$.activities[*].kind", hasItem("STEERING_APPLIED")))
                .andExpect(jsonPath("$.activities[*].message", hasItem("Automatic settlement is blocked on the workflow path. Redirect the case to finance control approval before any settlement-changing action.")));

        assertThat(shipmentServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
        assertThat(escrowServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
        assertThat(riskServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void startWorkflowPublishesStreamingActivitiesFromNativeWorkflowPath() throws Exception {
        enqueueStandardEvidenceResponses(BigDecimal.valueOf(149.95), "USD");

        mockMvc.perform(post("/internal/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startWorkflowRequest("case-arachne-stream", BigDecimal.valueOf(149.95), "USD")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities[*].message", hasItem("case-workflow-agent listed the packaged marketplace guidance before updating the recommendation.")))
                .andExpect(jsonPath("$.activities[*].message", hasItem("case-workflow-agent consulted the item-not-received runbook for the active case.")))
                .andExpect(jsonPath("$.activities[*].message", hasItem("case-workflow-agent reviewed the packaged settlement policy summary.")))
                .andExpect(jsonPath("$.activities[*].message", hasItem("case-workflow-agent reviewed finance-control thresholds before any settlement-changing action.")));

        assertThat(shipmentServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
        assertThat(escrowServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
        assertThat(riskServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void approvalResumePreservesCompletedOutcomeOnEnabledArachnePath() throws Exception {
        enqueueStandardEvidenceResponses(BigDecimal.valueOf(49.95), "USD");

        mockMvc.perform(post("/internal/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(startWorkflowRequest("case-arachne-approve", BigDecimal.valueOf(49.95), "USD")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.workflowStatus").value("AWAITING_APPROVAL"))
            .andExpect(jsonPath("$.currentRecommendation").value("REFUND"));

        drainStartRequests();

        enqueueJson(escrowServer, new DownstreamContracts.SettlementOutcome(
            "REFUND_EXECUTED",
            "SUCCEEDED",
            OffsetDateTime.parse("2026-03-30T12:05:01Z"),
            "refund-case-arachne-approve",
            "Escrow executed a refund after finance control approval."));
        enqueueJson(notificationServer, new DownstreamContracts.NotificationDispatchResult(
            "QUEUED",
            "PENDING_DELIVERY",
            "Notification service queued participant and operator notifications."));

        mockMvc.perform(post("/internal/workflows/{caseId}/approvals", "case-arachne-approve")
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
            .andExpect(jsonPath("$.outcome.outcomeType").value("REFUND_EXECUTED"))
            .andExpect(jsonPath("$.approvalState.approvalStatus").value("APPROVED"));

        assertThat(escrowServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
        assertThat(notificationServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void approvalRejectionPreservesEvidenceGatheringOutcomeOnEnabledArachnePath() throws Exception {
        enqueueStandardEvidenceResponses(BigDecimal.valueOf(149.95), "USD");

        mockMvc.perform(post("/internal/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(startWorkflowRequest("case-arachne-reject", BigDecimal.valueOf(149.95), "USD")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.workflowStatus").value("AWAITING_APPROVAL"));

        drainStartRequests();

        mockMvc.perform(post("/internal/workflows/{caseId}/approvals", "case-arachne-reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "decision": "REJECT",
                      "comment": "Need more shipment evidence before settlement.",
                      "actorId": "finance-8",
                      "actorRole": "FINANCE_CONTROL",
                      "requestedAt": "2026-03-30T12:06:00Z"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.workflowStatus").value("GATHERING_EVIDENCE"))
            .andExpect(jsonPath("$.currentRecommendation").value("PENDING_MORE_EVIDENCE"))
            .andExpect(jsonPath("$.approvalState.approvalStatus").value("REJECTED"));

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

    private void enqueueJson(MockWebServer server, Object body) throws Exception {
        server.enqueue(new MockResponse()
                .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody(objectMapper.writeValueAsString(body)));
    }

    private void drainStartRequests() throws InterruptedException {
        assertThat(shipmentServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
        assertThat(escrowServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
        assertThat(riskServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
    }
}