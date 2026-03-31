package io.arachne.samples.marketplace.caseservice;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.testcontainers.containers.PostgreSQLContainer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

@SpringBootTest
@AutoConfigureMockMvc
class CaseServiceApiTest {

    private static MockWebServer workflowServiceServer;

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("marketplace_case_service")
            .withUsername("marketplace")
            .withPassword("marketplace");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CaseProjectionRepository repository;

        @BeforeAll
        static void startInfrastructure() throws IOException {
                postgres.start();
                workflowServiceServer = new MockWebServer();
                workflowServiceServer.start();
        }

        @AfterAll
        static void stopInfrastructure() throws IOException {
                workflowServiceServer.shutdown();
                postgres.stop();
        }

        @DynamicPropertySource
        static void registerInfrastructure(DynamicPropertyRegistry registry) {
                registry.add("workflow-service.base-url", () -> workflowServiceServer.url("/").toString());
                registry.add("spring.datasource.url", postgres::getJdbcUrl);
                registry.add("spring.datasource.username", postgres::getUsername);
                registry.add("spring.datasource.password", postgres::getPassword);
        }

    @BeforeEach
    void resetRepository() {
        repository.deleteAll();
    }

    @Test
    void createListAndFetchCaseProjection() throws Exception {
                enqueueWorkflowStartResponse();

        var caseId = createCase();

                var startRequest = workflowServiceServer.takeRequest();
                assertThat(startRequest.getPath()).isEqualTo("/internal/workflows");
                assertThat(startRequest.getBody().readUtf8()).contains("ITEM_NOT_RECEIVED", "order-1001", "CASE_OPERATOR");

        mockMvc.perform(get("/api/cases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].caseId").value(caseId))
                .andExpect(jsonPath("$[0].caseStatus").value("AWAITING_APPROVAL"))
                .andExpect(jsonPath("$[0].approvalStatus").value("PENDING_FINANCE_CONTROL"))
                .andExpect(jsonPath("$[0].requestedRole").value("FINANCE_CONTROL"))
                .andExpect(jsonPath("$[0].outcomeType").doesNotExist());

        mockMvc.perform(options("/api/cases")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));

        mockMvc.perform(get("/api/cases")
                        .header("Origin", "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));

        mockMvc.perform(get("/api/cases/{caseId}", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(caseId))
                .andExpect(jsonPath("$.caseSummary.orderId").value("order-1001"))
                .andExpect(jsonPath("$.evidence.policyReference").value("policy://marketplace/disputes/item-not-received"))
                .andExpect(jsonPath("$.activityHistory", hasSize(greaterThanOrEqualTo(5))));
    }

    @Test
    void messageAndApprovalUpdateProjection() throws Exception {
                enqueueWorkflowStartResponse();
        var caseId = createCase();

                workflowServiceServer.takeRequest();

                enqueueWorkflowProgressResponse();

        mockMvc.perform(post("/api/cases/{caseId}/messages", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Please summarize the shipment evidence.",
                                  "operatorId": "operator-1",
                                  "operatorRole": "CASE_OPERATOR"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseStatus").value("AWAITING_APPROVAL"))
                .andExpect(jsonPath("$.currentRecommendation").value("CONTINUED_HOLD"));

        var messageRequest = workflowServiceServer.takeRequest();
        assertThat(messageRequest.getPath()).isEqualTo("/internal/workflows/" + caseId + "/messages");
        assertThat(messageRequest.getBody().readUtf8()).contains("Please summarize the shipment evidence.");

        enqueueWorkflowResumeResponse();

        mockMvc.perform(post("/api/cases/{caseId}/approvals", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "comment": "Continue the hold.",
                                  "actorId": "finance-1",
                                  "actorRole": "FINANCE_CONTROL"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(caseId))
                .andExpect(jsonPath("$.approvalState.approvalStatus").value("APPROVED"))
                .andExpect(jsonPath("$.workflowStatus").value("COMPLETED"));

        var approvalRequest = workflowServiceServer.takeRequest();
        assertThat(approvalRequest.getPath()).isEqualTo("/internal/workflows/" + caseId + "/approvals");
        assertThat(approvalRequest.getBody().readUtf8()).contains("APPROVE", "finance-1", "FINANCE_CONTROL");

        mockMvc.perform(get("/api/cases/{caseId}", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.outcome.outcomeType").value("CONTINUED_HOLD_RECORDED"));
    }

    @Test
    void approvedRefundOutcomeUpdatesProjection() throws Exception {
                enqueueWorkflowRefundStartResponse();
        var caseId = createCase("49.95", "REFUND");

                workflowServiceServer.takeRequest();

        enqueueWorkflowRefundResumeResponse();

        mockMvc.perform(post("/api/cases/{caseId}/approvals", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "comment": "Refund the buyer.",
                                  "actorId": "finance-3",
                                  "actorRole": "FINANCE_CONTROL"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(caseId))
                .andExpect(jsonPath("$.approvalState.approvalStatus").value("APPROVED"))
                .andExpect(jsonPath("$.workflowStatus").value("COMPLETED"));

        var approvalRequest = workflowServiceServer.takeRequest();
        assertThat(approvalRequest.getPath()).isEqualTo("/internal/workflows/" + caseId + "/approvals");
        assertThat(approvalRequest.getBody().readUtf8()).contains("APPROVE", "finance-3", "FINANCE_CONTROL");

        mockMvc.perform(get("/api/cases/{caseId}", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.currentRecommendation").value("REFUND"))
                .andExpect(jsonPath("$.outcome.outcomeType").value("REFUND_EXECUTED"));
    }

    @Test
    void approvalRequiresFinanceControlRole() throws Exception {
                enqueueWorkflowStartResponse();
        var caseId = createCase();

                workflowServiceServer.takeRequest();

        mockMvc.perform(post("/api/cases/{caseId}/approvals", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "comment": "I should not be allowed to do this.",
                                  "actorId": "operator-1",
                                  "actorRole": "CASE_OPERATOR"
                                }
                                """))
                .andExpect(status().isForbidden());

        assertThat(workflowServiceServer.takeRequest(100, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void rejectedApprovalReturnsCaseToEvidenceGathering() throws Exception {
                enqueueWorkflowStartResponse();
        var caseId = createCase();

                workflowServiceServer.takeRequest();

        enqueueWorkflowRejectedResumeResponse();

        mockMvc.perform(post("/api/cases/{caseId}/approvals", caseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "REJECT",
                                  "comment": "Need more shipment evidence before settlement.",
                                  "actorId": "finance-2",
                                  "actorRole": "FINANCE_CONTROL"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(caseId))
                .andExpect(jsonPath("$.approvalState.approvalStatus").value("REJECTED"))
                .andExpect(jsonPath("$.workflowStatus").value("GATHERING_EVIDENCE"))
                .andExpect(jsonPath("$.message").value("Approval rejection accepted and workflow returned to evidence gathering."));

        var approvalRequest = workflowServiceServer.takeRequest();
        assertThat(approvalRequest.getPath()).isEqualTo("/internal/workflows/" + caseId + "/approvals");
        assertThat(approvalRequest.getBody().readUtf8()).contains("REJECT", "finance-2", "FINANCE_CONTROL");

        mockMvc.perform(get("/api/cases/{caseId}", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseStatus").value("GATHERING_EVIDENCE"))
                .andExpect(jsonPath("$.currentRecommendation").value("PENDING_MORE_EVIDENCE"))
                .andExpect(jsonPath("$.approvalState.approvalStatus").value("REJECTED"))
                .andExpect(jsonPath("$.approvalState.comment").value("Need more shipment evidence before settlement."))
                .andExpect(jsonPath("$.outcome").value(nullValue()));
    }

    @Test
    void searchUsesDeterministicStructuredStateTerms() throws Exception {
        insertCaseProjection(
                "case-await-001",
                "order-3001",
                CaseContracts.CaseStatus.AWAITING_APPROVAL,
                CaseContracts.Recommendation.CONTINUED_HOLD,
                CaseContracts.ApprovalStatus.PENDING_FINANCE_CONTROL,
                "FINANCE_CONTROL",
                null,
                OffsetDateTime.parse("2026-03-30T12:00:00Z"));
        insertCaseProjection(
                "case-reject-001",
                "order-3002",
                CaseContracts.CaseStatus.GATHERING_EVIDENCE,
                CaseContracts.Recommendation.PENDING_MORE_EVIDENCE,
                CaseContracts.ApprovalStatus.REJECTED,
                "FINANCE_CONTROL",
                null,
                OffsetDateTime.parse("2026-03-30T12:05:00Z"));
        insertCaseProjection(
                "case-refund-001",
                "order-3003",
                CaseContracts.CaseStatus.COMPLETED,
                CaseContracts.Recommendation.REFUND,
                CaseContracts.ApprovalStatus.APPROVED,
                "FINANCE_CONTROL",
                CaseContracts.OutcomeType.REFUND_EXECUTED,
                OffsetDateTime.parse("2026-03-30T12:10:00Z"));

        mockMvc.perform(get("/api/cases").param("q", "pending approval"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].caseId").value("case-await-001"))
                .andExpect(jsonPath("$[1].caseId").value("case-reject-001"));

        mockMvc.perform(get("/api/cases").param("q", "needs more evidence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].caseId").value("case-reject-001"));

        mockMvc.perform(get("/api/cases").param("q", "refund executed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].caseId").value("case-refund-001"));
    }

    @Test
    void searchRanksExactIdentifierMatchAheadOfBroaderPrefixMatches() throws Exception {
        insertCaseProjection(
                "case-match-exact",
                "order-410",
                CaseContracts.CaseStatus.AWAITING_APPROVAL,
                CaseContracts.Recommendation.CONTINUED_HOLD,
                CaseContracts.ApprovalStatus.PENDING_FINANCE_CONTROL,
                "FINANCE_CONTROL",
                null,
                OffsetDateTime.parse("2026-03-30T12:00:00Z"));
        insertCaseProjection(
                "case-match-prefix",
                "order-410-extra",
                CaseContracts.CaseStatus.AWAITING_APPROVAL,
                CaseContracts.Recommendation.CONTINUED_HOLD,
                CaseContracts.ApprovalStatus.PENDING_FINANCE_CONTROL,
                "FINANCE_CONTROL",
                null,
                OffsetDateTime.parse("2026-03-30T12:20:00Z"));

        mockMvc.perform(get("/api/cases").param("q", "order 410"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].caseId").value("case-match-exact"))
                .andExpect(jsonPath("$[1].caseId").value("case-match-prefix"));
    }

                private String createCase() throws Exception {
                                return createCase("149.95", "CONTINUED_HOLD");
                }

                private String createCase(String amount, String expectedRecommendation) throws Exception {
        var response = mockMvc.perform(post("/api/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseType": "ITEM_NOT_RECEIVED",
                                  "orderId": "order-1001",
                                                                                                                                        "amount": %s,
                                  "currency": "USD",
                                  "initialMessage": "Buyer reports item not received.",
                                  "operatorId": "operator-1",
                                  "operatorRole": "CASE_OPERATOR"
                                }
                                                                                                                                """.formatted(amount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseStatus").value("AWAITING_APPROVAL"))
                                                                .andExpect(jsonPath("$.currentRecommendation").value(expectedRecommendation))
                .andExpect(jsonPath("$.approvalState.approvalStatus").value("PENDING_FINANCE_CONTROL"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("caseId").asText();
    }

    private void insertCaseProjection(
            String caseId,
            String orderId,
            CaseContracts.CaseStatus caseStatus,
            CaseContracts.Recommendation recommendation,
            CaseContracts.ApprovalStatus approvalStatus,
            String requestedRole,
            CaseContracts.OutcomeType outcomeType,
            OffsetDateTime updatedAt) {
        var approvalState = new CaseContracts.ApprovalStateView(
                approvalStatus != CaseContracts.ApprovalStatus.NOT_REQUIRED,
                approvalStatus,
                requestedRole,
                updatedAt.minusMinutes(2),
                approvalStatus == CaseContracts.ApprovalStatus.PENDING_FINANCE_CONTROL ? null : updatedAt.minusMinutes(1),
                approvalStatus == CaseContracts.ApprovalStatus.PENDING_FINANCE_CONTROL ? null : "finance-1",
                approvalStatus == CaseContracts.ApprovalStatus.REJECTED ? "Need more shipment evidence before settlement." : null);
        var outcome = outcomeType == null
                ? null
                : new CaseContracts.OutcomeView(
                        outcomeType,
                        "SUCCEEDED",
                        updatedAt,
                        "settlement-" + caseId,
                        "Deterministic settlement recorded for operator visibility.");
        var detail = new CaseContracts.CaseDetailView(
                caseId,
                "ITEM_NOT_RECEIVED",
                caseStatus,
                orderId,
                "txn-" + caseId.substring(0, 8),
                new BigDecimal("149.95"),
                "USD",
                recommendation,
                new CaseContracts.CaseSummaryView(
                        caseId,
                        "ITEM_NOT_RECEIVED",
                        caseStatus,
                        orderId,
                        "txn-" + caseId.substring(0, 8),
                        new BigDecimal("149.95"),
                        "USD",
                        recommendation),
                new CaseContracts.EvidenceView(
                        "Shipment evidence summary",
                        "Escrow evidence summary",
                        "Risk evidence summary",
                        "policy://marketplace/disputes/item-not-received"),
                List.of(),
                approvalState,
                outcome);
        repository.insertCase(detail, updatedAt);
    }

    private void enqueueWorkflowStartResponse() throws Exception {
        var now = OffsetDateTime.parse("2026-03-30T12:00:00Z");
        var response = new WorkflowContracts.WorkflowStartResult(
                CaseContracts.CaseStatus.AWAITING_APPROVAL,
                CaseContracts.Recommendation.CONTINUED_HOLD,
                new CaseContracts.EvidenceView(
                        "Carrier tracking shows label creation but no confirmed delivery scan for the order.",
                        "Escrow still holds the authorized funds and no prior refund has been executed.",
                        "Risk review found no elevated fraud signal but requires finance control confirmation for settlement-changing actions.",
                        "policy://marketplace/disputes/item-not-received"),
                new CaseContracts.ApprovalStateView(
                        true,
                        CaseContracts.ApprovalStatus.PENDING_FINANCE_CONTROL,
                        "FINANCE_CONTROL",
                        now,
                        null,
                        null,
                        null),
                null,
                java.util.List.of(
                        new WorkflowContracts.WorkflowActivity("CASE_CREATED", "case-service", "Case created from operator request.", "{}", now),
                        new WorkflowContracts.WorkflowActivity("DELEGATION_STARTED", "workflow-service", "Workflow delegated evidence gathering across shipment, escrow, and risk services.", "{}", now.plusSeconds(1)),
                        new WorkflowContracts.WorkflowActivity("EVIDENCE_RECEIVED", "workflow-service", "Structured shipment, escrow, and risk evidence collected.", "{}", now.plusSeconds(2)),
                        new WorkflowContracts.WorkflowActivity("RECOMMENDATION_UPDATED", "workflow-service", "Workflow recommends keeping the hold until finance control confirms the next step.", "{}", now.plusSeconds(3)),
                        new WorkflowContracts.WorkflowActivity("APPROVAL_REQUESTED", "workflow-service", "Finance control approval is required before settlement progression.", "{}", now.plusSeconds(4))));
        enqueueJson(response);
    }

    private void enqueueWorkflowProgressResponse() throws Exception {
        var now = OffsetDateTime.parse("2026-03-30T12:10:00Z");
        var response = new WorkflowContracts.WorkflowProgressUpdate(
                CaseContracts.CaseStatus.AWAITING_APPROVAL,
                CaseContracts.Recommendation.CONTINUED_HOLD,
                new CaseContracts.EvidenceView(
                        "Carrier tracking shows label creation but no confirmed delivery scan for the order.",
                        "Escrow still holds the authorized funds and no prior refund has been executed.",
                        "Risk review found no elevated fraud signal but requires finance control confirmation for settlement-changing actions.",
                        "policy://marketplace/disputes/item-not-received"),
                new CaseContracts.ApprovalStateView(
                        true,
                        CaseContracts.ApprovalStatus.PENDING_FINANCE_CONTROL,
                        "FINANCE_CONTROL",
                        now,
                        null,
                        null,
                        null),
                null,
                java.util.List.of(new WorkflowContracts.WorkflowActivity(
                        "RECOMMENDATION_UPDATED",
                        "workflow-service",
                        "Workflow reviewed operator follow-up and kept the case on the finance-control approval path.",
                        "{}",
                        now)));
        enqueueJson(response);
    }

    private void enqueueWorkflowRefundStartResponse() throws Exception {
        var now = OffsetDateTime.parse("2026-03-30T12:00:00Z");
        var response = new WorkflowContracts.WorkflowStartResult(
                CaseContracts.CaseStatus.AWAITING_APPROVAL,
                CaseContracts.Recommendation.REFUND,
                new CaseContracts.EvidenceView(
                        "Carrier tracking shows label creation but no confirmed delivery scan for the order.",
                        "Escrow still holds the authorized funds and no prior refund has been executed.",
                        "Risk review found no elevated fraud signal but requires finance control confirmation for settlement-changing actions.",
                        "policy://marketplace/disputes/item-not-received"),
                new CaseContracts.ApprovalStateView(
                        true,
                        CaseContracts.ApprovalStatus.PENDING_FINANCE_CONTROL,
                        "FINANCE_CONTROL",
                        now,
                        null,
                        null,
                        null),
                null,
                java.util.List.of(
                        new WorkflowContracts.WorkflowActivity("CASE_CREATED", "case-service", "Case created from operator request.", "{}", now),
                        new WorkflowContracts.WorkflowActivity("DELEGATION_STARTED", "workflow-service", "Workflow delegated evidence gathering across shipment, escrow, and risk services.", "{}", now.plusSeconds(1)),
                        new WorkflowContracts.WorkflowActivity("EVIDENCE_RECEIVED", "workflow-service", "Structured shipment, escrow, and risk evidence collected.", "{}", now.plusSeconds(2)),
                        new WorkflowContracts.WorkflowActivity("RECOMMENDATION_UPDATED", "workflow-service", "Workflow recommends refunding the buyer after confirming non-delivery and a low-value exposure path.", "{}", now.plusSeconds(3)),
                        new WorkflowContracts.WorkflowActivity("APPROVAL_REQUESTED", "workflow-service", "Finance control approval is required before settlement progression.", "{}", now.plusSeconds(4))));
        enqueueJson(response);
    }

    private void enqueueWorkflowResumeResponse() throws Exception {
        var now = OffsetDateTime.parse("2026-03-30T12:15:00Z");
        var response = new WorkflowContracts.WorkflowResumeResult(
                CaseContracts.CaseStatus.COMPLETED,
                CaseContracts.Recommendation.CONTINUED_HOLD,
                new CaseContracts.EvidenceView(
                        "Carrier tracking shows label creation but no confirmed delivery scan for the order.",
                        "Escrow still holds the authorized funds and no prior refund has been executed.",
                        "Risk review found no elevated fraud signal but requires finance control confirmation for settlement-changing actions.",
                        "policy://marketplace/disputes/item-not-received"),
                new CaseContracts.ApprovalStateView(
                        true,
                        CaseContracts.ApprovalStatus.APPROVED,
                        "FINANCE_CONTROL",
                        now.minusMinutes(5),
                        now,
                        "finance-1",
                        "Continue the hold."),
                new CaseContracts.OutcomeView(
                        CaseContracts.OutcomeType.CONTINUED_HOLD_RECORDED,
                        "SUCCEEDED",
                        now.plusSeconds(1),
                        "settlement-case",
                        "Finance control approved the continued hold and escrow recorded the outcome."),
                java.util.List.of(
                        new WorkflowContracts.WorkflowActivity("APPROVAL_SUBMITTED", "case-service", "Finance control approval submitted to workflow-service.", "{}", now),
                        new WorkflowContracts.WorkflowActivity("SETTLEMENT_COMPLETED", "escrow-service", "Escrow service recorded the continued hold outcome.", "{}", now.plusSeconds(1)),
                        new WorkflowContracts.WorkflowActivity("NOTIFICATION_DISPATCHED", "notification-service", "Notification service queued participant and operator notifications.", "{}", now.plusSeconds(2))),
                "Approval accepted and settlement progression completed.");
        enqueueJson(response);
    }

    private void enqueueWorkflowRefundResumeResponse() throws Exception {
        var now = OffsetDateTime.parse("2026-03-30T12:15:00Z");
        var response = new WorkflowContracts.WorkflowResumeResult(
                CaseContracts.CaseStatus.COMPLETED,
                CaseContracts.Recommendation.REFUND,
                new CaseContracts.EvidenceView(
                        "Carrier tracking shows label creation but no confirmed delivery scan for the order.",
                        "Escrow still holds the authorized funds and no prior refund has been executed.",
                        "Risk review found no elevated fraud signal but requires finance control confirmation for settlement-changing actions.",
                        "policy://marketplace/disputes/item-not-received"),
                new CaseContracts.ApprovalStateView(
                        true,
                        CaseContracts.ApprovalStatus.APPROVED,
                        "FINANCE_CONTROL",
                        now.minusMinutes(5),
                        now,
                        "finance-3",
                        "Refund the buyer."),
                new CaseContracts.OutcomeView(
                        CaseContracts.OutcomeType.REFUND_EXECUTED,
                        "SUCCEEDED",
                        now.plusSeconds(1),
                        "refund-case",
                        "Finance control approved the refund and escrow recorded the outcome."),
                java.util.List.of(
                        new WorkflowContracts.WorkflowActivity("APPROVAL_SUBMITTED", "case-service", "Finance control approval submitted to workflow-service.", "{}", now),
                        new WorkflowContracts.WorkflowActivity("SETTLEMENT_COMPLETED", "escrow-service", "Escrow service executed the refund outcome.", "{}", now.plusSeconds(1)),
                        new WorkflowContracts.WorkflowActivity("NOTIFICATION_DISPATCHED", "notification-service", "Notification service queued participant and operator notifications.", "{}", now.plusSeconds(2))),
                "Approval accepted and settlement completed.");
        enqueueJson(response);
    }

    private void enqueueWorkflowRejectedResumeResponse() throws Exception {
        var now = OffsetDateTime.parse("2026-03-30T12:16:00Z");
        var response = new WorkflowContracts.WorkflowResumeResult(
                CaseContracts.CaseStatus.GATHERING_EVIDENCE,
                CaseContracts.Recommendation.PENDING_MORE_EVIDENCE,
                new CaseContracts.EvidenceView(
                        "Carrier tracking shows label creation but no confirmed delivery scan for the order.",
                        "Escrow still holds the authorized funds and no prior refund has been executed.",
                        "Risk review found no elevated fraud signal but requires finance control confirmation for settlement-changing actions.",
                        "policy://marketplace/disputes/item-not-received"),
                new CaseContracts.ApprovalStateView(
                        true,
                        CaseContracts.ApprovalStatus.REJECTED,
                        "FINANCE_CONTROL",
                        now.minusMinutes(6),
                        now,
                        "finance-2",
                        "Need more shipment evidence before settlement."),
                null,
                java.util.List.of(new WorkflowContracts.WorkflowActivity(
                        "RECOMMENDATION_UPDATED",
                        "workflow-service",
                        "Workflow returned to evidence gathering after finance control rejected the current recommendation.",
                        "{}",
                        now)),
                "Approval rejection accepted and workflow returned to evidence gathering.");
        enqueueJson(response);
    }

    private void enqueueJson(Object body) throws Exception {
        workflowServiceServer.enqueue(new MockResponse()
                .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody(objectMapper.writeValueAsString(body)));
    }
}