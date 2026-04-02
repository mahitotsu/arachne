package com.mahitotsu.arachne.samples.marketplace.workflowservice;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahitotsu.arachne.strands.model.Model;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

@SpringBootTest(properties = {
        "marketplace.workflow-runtime.arachne.enabled=true",
        "marketplace.workflow-runtime.arachne.model-mode=bedrock"
})
@SpringJUnitConfig(classes = WorkflowServiceBedrockModeApiTest.BedrockModeTestConfiguration.class)
@AutoConfigureMockMvc
class WorkflowServiceBedrockModeApiTest {

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
    void alternateModelModeStillUsesTheArachneWorkflowRuntimeBoundary() throws Exception {
        enqueueStandardEvidenceResponses(BigDecimal.valueOf(149.95), "USD");

        mockMvc.perform(post("/internal/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startWorkflowRequest("case-bedrock-mode", BigDecimal.valueOf(149.95), "USD")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowStatus").value("AWAITING_APPROVAL"))
                .andExpect(jsonPath("$.activities[0].source").value("case-workflow-agent"))
                .andExpect(jsonPath("$.evidence.policyReference").value("policy://marketplace/disputes/item-not-received"));

        assertThat(shipmentServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
        assertThat(escrowServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
        assertThat(riskServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
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

    @TestConfiguration(proxyBeanMethods = false)
    static class BedrockModeTestConfiguration {

        @Bean
        @Primary
        Model alternateWorkflowModeModel() {
            return new MarketplaceWorkflowArachneModel();
        }
    }
}