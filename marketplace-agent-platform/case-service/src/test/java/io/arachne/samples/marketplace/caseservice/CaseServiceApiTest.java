package io.arachne.samples.marketplace.caseservice;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
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
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.testcontainers.containers.PostgreSQLContainer;

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
                .andExpect(jsonPath("$[0].caseStatus").value("AWAITING_APPROVAL"));

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

    private String createCase() throws Exception {
        var response = mockMvc.perform(post("/api/cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseType": "ITEM_NOT_RECEIVED",
                                  "orderId": "order-1001",
                                  "amount": 149.95,
                                  "currency": "USD",
                                  "initialMessage": "Buyer reports item not received.",
                                  "operatorId": "operator-1",
                                  "operatorRole": "CASE_OPERATOR"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseStatus").value("AWAITING_APPROVAL"))
                .andExpect(jsonPath("$.currentRecommendation").value("CONTINUED_HOLD"))
                .andExpect(jsonPath("$.approvalState.approvalStatus").value("PENDING_FINANCE_CONTROL"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("caseId").asText();
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

    private void enqueueJson(Object body) throws Exception {
        workflowServiceServer.enqueue(new MockResponse()
                .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody(objectMapper.writeValueAsString(body)));
    }
}