package com.mahitotsu.arachne.samples.marketplace.workflowservice;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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

    @BeforeEach
    void drainServers() throws Exception {
        while (shipmentServer.takeRequest(10, TimeUnit.MILLISECONDS) != null) {
        }
        while (escrowServer.takeRequest(10, TimeUnit.MILLISECONDS) != null) {
        }
        while (riskServer.takeRequest(10, TimeUnit.MILLISECONDS) != null) {
        }
        while (notificationServer.takeRequest(10, TimeUnit.MILLISECONDS) != null) {
        }
    }

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
            .andExpect(jsonPath("$.activities[*].kind", hasItem("CONTEXT_PROPAGATED")))
            .andExpect(jsonPath("$.activities[*].message", hasItem("workflow-service propagated operator authorization context operator-1/CASE_OPERATOR into parallel tool execution for shipment-delegation.")))
            .andExpect(jsonPath("$.activities[*].message", hasItem("workflow-service propagated operator authorization context operator-1/CASE_OPERATOR into parallel tool execution for risk-delegation.")))
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
        void startWorkflowLeavesDeliveredDamagedCaseInEvidenceGatheringOnEnabledArachnePath() throws Exception {
        enqueueJson(shipmentServer, new DownstreamContracts.ShipmentEvidenceSummary(
            "TRACK-order-dmg-2007",
            "Carrier tracking shows final delivery on the doorstep with photo proof captured the same day.",
            "HIGH",
            "Shipment was delivered, but the package exterior shows impact damage and moisture exposure."));
        enqueueJson(escrowServer, new DownstreamContracts.EscrowEvidenceSummary(
            "HELD",
            "REQUIRES_DAMAGE_REVIEW",
            BigDecimal.valueOf(189.00),
            "USD",
            "NO_PRIOR_REFUND",
            "Escrow still holds the funds while damage evidence and seller response are collected."));
        enqueueJson(riskServer, new DownstreamContracts.RiskReviewSummary(
            "No elevated fraud signal detected, but the damage dispute needs seller and inspection evidence.",
            false,
            java.util.List.of("DAMAGE_EVIDENCE_REQUIRED"),
            "Risk review found no fraud escalation, but the damage claim still needs corroborating evidence before settlement changes."));

        mockMvc.perform(post("/internal/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(startWorkflowRequest(
                    "case-arachne-damaged",
                    "DELIVERED_BUT_DAMAGED",
                    "order-dmg-2007",
                    BigDecimal.valueOf(189.00),
                    "USD",
                    "Buyer says the package arrived crushed and wet and asks what evidence is still needed.")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.workflowStatus").value("GATHERING_EVIDENCE"))
            .andExpect(jsonPath("$.currentRecommendation").value("PENDING_MORE_EVIDENCE"))
            .andExpect(jsonPath("$.approvalState.approvalRequired").value(false))
            .andExpect(jsonPath("$.approvalState.approvalStatus").value("NOT_REQUIRED"));

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
    void startWorkflowPublishesEachResourceListProgressOnlyOnce() throws Exception {
        enqueueStandardEvidenceResponses(BigDecimal.valueOf(149.95), "USD");

        String response = mockMvc.perform(post("/internal/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startWorkflowRequest("case-arachne-no-duplicate-progress", BigDecimal.valueOf(149.95), "USD")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        WorkflowContracts.WorkflowStartResult result = objectMapper.readValue(response, WorkflowContracts.WorkflowStartResult.class);
        List<WorkflowContracts.WorkflowActivity> resourceListActivities = result.activities().stream()
                .filter(activity -> "STREAM_PROGRESS".equals(activity.kind()))
                .filter(activity -> activity.structuredPayload().contains("\"toolName\":\"resource_list\""))
                .toList();

        assertThat(resourceListActivities).hasSize(1);

        assertThat(shipmentServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
        assertThat(escrowServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
        assertThat(riskServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void startWorkflowPublishesApprovalHookAndToolActivities() throws Exception {
        enqueueStandardEvidenceResponses(BigDecimal.valueOf(149.95), "USD");

        mockMvc.perform(post("/internal/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startWorkflowRequest("case-arachne-approval-events", BigDecimal.valueOf(149.95), "USD")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities[*].kind", hasItem("HOOK_FORCED_TOOL_SELECTION")))
                .andExpect(jsonPath("$.activities[*].kind", hasItem("TOOL_CALL_RECORDED")))
                .andExpect(jsonPath("$.activities[*].kind", hasItem("APPROVAL_INTERRUPT_REGISTERED")))
                .andExpect(jsonPath("$.activities[*].structuredPayload", hasItem(containsString("hook_event"))))
                .andExpect(jsonPath("$.activities[*].structuredPayload", hasItem(containsString("finance_control_approval"))))
                .andExpect(jsonPath("$.activities[*].structuredPayload", hasItem(containsString("financeControlApproval"))));

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

    @Test
    void continueWorkflowDelegatesOperatorInstructionToTargetAgentOnEnabledArachnePath() throws Exception {
        enqueueStandardEvidenceResponses(BigDecimal.valueOf(149.95), "USD");

        mockMvc.perform(post("/internal/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startWorkflowRequest("case-arachne-follow-up", BigDecimal.valueOf(149.95), "USD")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowStatus").value("AWAITING_APPROVAL"));

        drainStartRequests();
    enqueueJson(shipmentServer, new DownstreamContracts.ShipmentSpecialistReview(
        "shipment-agent reviewed the operator instruction \"Please summarize the shipment evidence.\" against shipment-service records: Carrier tracking shows label creation and in-transit milestones but no final delivery scan. Tracking number: TRACK-order-1001. Shipment remains in a not-delivered state for the current case."));

        mockMvc.perform(post("/internal/workflows/{caseId}/messages", "case-arachne-follow-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Please summarize the shipment evidence.",
                                  "operatorId": "operator-1",
                                  "operatorRole": "CASE_OPERATOR",
                                  "requestedAt": "2026-03-30T12:03:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities[0].kind").value("OPERATOR_INSTRUCTION_RECEIVED"))
                .andExpect(jsonPath("$.activities[1].kind").value("DELEGATION_ROUTED"))
                .andExpect(jsonPath("$.activities[2].kind").value("AGENT_RESPONSE"))
                .andExpect(jsonPath("$.activities[3].kind").value("OPERATOR_REQUEST_COMPLETED"))
                .andExpect(jsonPath("$.activities[2].source").value("shipment-agent"))
                .andExpect(jsonPath("$.activities[2].message", containsString("shipment-agent reviewed the operator instruction")))
                .andExpect(jsonPath("$.activities[0].structuredPayload", containsString("shipment-agent")))
                .andExpect(jsonPath("$.activities[3].structuredPayload", containsString("workflow_completion")));

            assertThat(shipmentServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
        assertThat(escrowServer.takeRequest(100, TimeUnit.MILLISECONDS)).isNull();
        assertThat(riskServer.takeRequest(100, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void continueWorkflowDelegatesActiveResolutionQuestionToSpecialistsAndAggregatesSummary() throws Exception {
        enqueueStandardEvidenceResponses(BigDecimal.valueOf(149.95), "USD");

        mockMvc.perform(post("/internal/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startWorkflowRequest("case-arachne-resolution-active", BigDecimal.valueOf(149.95), "USD")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowStatus").value("AWAITING_APPROVAL"));

        drainStartRequests();
    enqueueJson(shipmentServer, new DownstreamContracts.ShipmentSpecialistReview(
        "shipment-agent reviewed the operator instruction \"どうしたら解決できますか。\" against shipment-service records: Carrier tracking shows label creation and in-transit milestones but no final delivery scan. Tracking number: TRACK-order-1001. Shipment remains in a not-delivered state for the current case."));
    enqueueJson(escrowServer, new DownstreamContracts.EscrowSpecialistReview(
        "escrow-agent reviewed the operator instruction \"どうしたら解決できますか。\" against escrow-service records: Escrow still holds the authorized funds and no prior refund has been executed. Hold state: HELD. Eligibility: ELIGIBLE_FOR_CONTINUED_HOLD."));
    enqueueJson(riskServer, new DownstreamContracts.RiskSpecialistReview(
        "risk-agent reviewed the operator instruction \"どうしたら解決できますか。\" against risk-service records: Risk review found no elevated fraud signal but requires finance control confirmation for settlement-changing actions. Indicators: No elevated fraud signal detected for the current order.. Flags: FINANCE_CONTROL_REVIEW_REQUIRED."));

        mockMvc.perform(post("/internal/workflows/{caseId}/messages", "case-arachne-resolution-active")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "どうしたら解決できますか。",
                                  "operatorId": "operator-1",
                                  "operatorRole": "CASE_OPERATOR",
                                  "requestedAt": "2026-03-30T12:03:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities", hasSize(8)))
                .andExpect(jsonPath("$.activities[0].kind").value("OPERATOR_INSTRUCTION_RECEIVED"))
                .andExpect(jsonPath("$.activities[0].structuredPayload", containsString("shipment-agent")))
                .andExpect(jsonPath("$.activities[0].structuredPayload", containsString("escrow-agent")))
                .andExpect(jsonPath("$.activities[0].structuredPayload", containsString("risk-agent")))
                .andExpect(jsonPath("$.activities[1].kind").value("DELEGATION_ROUTED"))
                .andExpect(jsonPath("$.activities[2].source").value("shipment-agent"))
                .andExpect(jsonPath("$.activities[3].kind").value("DELEGATION_ROUTED"))
                .andExpect(jsonPath("$.activities[4].source").value("escrow-agent"))
                .andExpect(jsonPath("$.activities[5].kind").value("DELEGATION_ROUTED"))
                .andExpect(jsonPath("$.activities[6].source").value("risk-agent"))
                .andExpect(jsonPath("$.activities[7].kind").value("OPERATOR_REQUEST_COMPLETED"))
                .andExpect(jsonPath("$.activities[7].message", containsString("After consulting shipment-agent, escrow-agent, risk-agent")))
                .andExpect(jsonPath("$.activities[7].message", containsString("The next step to resolve this case is finance control approval.")))
                .andExpect(jsonPath("$.activities[7].message", containsString("shipment-agent reported:")))
                .andExpect(jsonPath("$.activities[7].message", containsString("escrow-agent reported:")))
                .andExpect(jsonPath("$.activities[7].message", containsString("risk-agent reported:")));

            assertThat(shipmentServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
            assertThat(escrowServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
            assertThat(riskServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void continueWorkflowAnswersJapaneseResolutionQuestionFromCompletedCaseState() throws Exception {
        enqueueStandardEvidenceResponses(BigDecimal.valueOf(149.95), "USD");

        mockMvc.perform(post("/internal/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startWorkflowRequest("case-arachne-resolution-follow-up", BigDecimal.valueOf(149.95), "USD")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowStatus").value("AWAITING_APPROVAL"));

        drainStartRequests();

        enqueueJson(escrowServer, new DownstreamContracts.SettlementOutcome(
            "CONTINUED_HOLD_RECORDED",
            "SUCCEEDED",
            OffsetDateTime.parse("2026-03-30T12:05:01Z"),
            "hold-case-arachne-resolution-follow-up",
            "Escrow recorded the continued hold after finance control approval."));
        enqueueJson(notificationServer, new DownstreamContracts.NotificationDispatchResult(
            "QUEUED",
            "PENDING_DELIVERY",
            "Notification service queued participant and operator notifications."));

        mockMvc.perform(post("/internal/workflows/{caseId}/approvals", "case-arachne-resolution-follow-up")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "decision": "APPROVE",
                      "comment": "Proceed with the continued hold.",
                      "actorId": "finance-7",
                      "actorRole": "FINANCE_CONTROL",
                      "requestedAt": "2026-03-30T12:05:00Z"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.workflowStatus").value("COMPLETED"))
            .andExpect(jsonPath("$.approvalState.approvalStatus").value("APPROVED"))
            .andExpect(jsonPath("$.outcome.outcomeType").value("CONTINUED_HOLD_RECORDED"));

        assertThat(escrowServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
        assertThat(notificationServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();

        mockMvc.perform(post("/internal/workflows/{caseId}/messages", "case-arachne-resolution-follow-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "どうすれば解決できるのか知りたいです。",
                                  "operatorId": "operator-1",
                                  "operatorRole": "CASE_OPERATOR",
                                  "requestedAt": "2026-03-30T12:07:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities", hasSize(2)))
                .andExpect(jsonPath("$.activities[0].kind").value("OPERATOR_INSTRUCTION_RECEIVED"))
                .andExpect(jsonPath("$.activities[0].message", containsString("prepared a workflow answer from the current case state")))
                .andExpect(jsonPath("$.activities[0].structuredPayload", containsString("どうすれば解決できるのか知りたいです。")))
                .andExpect(jsonPath("$.activities[1].kind").value("OPERATOR_REQUEST_COMPLETED"))
                .andExpect(jsonPath("$.activities[1].message", containsString("This case is already resolved.")))
                .andExpect(jsonPath("$.activities[1].message", containsString("No further workflow action is required unless new evidence is introduced.")))
                .andExpect(jsonPath("$.activities[1].structuredPayload", containsString("\"delegatedAgents\":[]")));

        assertThat(shipmentServer.takeRequest(100, TimeUnit.MILLISECONDS)).isNull();
        assertThat(escrowServer.takeRequest(100, TimeUnit.MILLISECONDS)).isNull();
        assertThat(riskServer.takeRequest(100, TimeUnit.MILLISECONDS)).isNull();
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
        return startWorkflowRequest(
            caseId,
            "ITEM_NOT_RECEIVED",
            "order-1001",
            amount,
            currency,
            "Buyer reports item not received.");
        }

        private String startWorkflowRequest(
            String caseId,
            String caseType,
            String orderId,
            BigDecimal amount,
            String currency,
            String initialMessage) {
        return """
                {
                  "caseId": "%s",
              "caseType": "%s",
              "orderId": "%s",
                  "amount": %s,
                  "currency": "%s",
              "initialMessage": "%s",
                  "operatorId": "operator-1",
                  "operatorRole": "CASE_OPERATOR",
                  "requestedAt": "2026-03-30T12:00:00Z"
                }
            """.formatted(caseId, caseType, orderId, amount, currency, initialMessage);
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