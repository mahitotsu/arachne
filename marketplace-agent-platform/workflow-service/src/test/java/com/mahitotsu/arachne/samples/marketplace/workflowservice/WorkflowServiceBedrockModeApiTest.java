package com.mahitotsu.arachne.samples.marketplace.workflowservice;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
import com.mahitotsu.arachne.strands.model.ModelEvent;
import com.mahitotsu.arachne.strands.model.ToolSelection;
import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.types.ContentBlock;
import com.mahitotsu.arachne.strands.types.Message;

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
            .andExpect(jsonPath("$.evidence.policyReference").value("policy://marketplace/disputes/item-not-received"))
            .andExpect(jsonPath("$.activities[*].kind").value(org.hamcrest.Matchers.hasItem("STREAM_PROGRESS")))
            .andExpect(jsonPath("$.activities[*].message").value(org.hamcrest.Matchers.hasItem("case-workflow-agent listed the packaged marketplace guidance before updating the recommendation.")));

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
            return new LoopingWorkflowModel();
        }
    }

    private static final class LoopingWorkflowModel implements Model {

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools) {
            throw new AssertionError("Expected the system-prompt-aware overload");
        }

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools, String systemPrompt) {
            return converse(messages, tools, systemPrompt, null);
        }

        @Override
        public Iterable<ModelEvent> converse(
                List<Message> messages,
                List<ToolSpec> tools,
                String systemPrompt,
                ToolSelection toolSelection) {
            String agentName = agentName(systemPrompt, latestUserText(messages));
            return switch (agentName) {
            case "shipment-agent" -> shipmentSummary(messages);
            case "escrow-agent" -> escrowSummary(messages);
            case "risk-agent" -> riskSummary(messages);
                default -> workflowDecision(messages, toolSelection);
            };
        }

        private Iterable<ModelEvent> shipmentSummary(List<Message> messages) {
            Map<String, String> prompt = promptValues(messages);
            Map<String, Object> evidence = latestToolContent(messages, "shipment-evidence-lookup");
            if (evidence == null) {
            return List.of(
                new ModelEvent.ToolUse(
                    "shipment-evidence-lookup",
                    ShipmentEvidenceLookupTool.TOOL_NAME,
                    Map.of(
                        "caseId", prompt.getOrDefault("caseId", ""),
                        "caseType", prompt.getOrDefault("caseType", ""),
                        "orderId", prompt.getOrDefault("orderId", ""),
                        "disputeSummary", prompt.getOrDefault("disputeSummary", ""))),
                new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }
            return List.of(
                new ModelEvent.ToolUse(
                    "shipment-summary",
                    "structured_output",
                    Map.of(
                        "summary",
                        evidence.get("milestoneSummary") + " Tracking number: " + evidence.get("trackingNumber") + ". "
                            + evidence.get("shippingExceptionSummary"))),
                    new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
        }

        private Iterable<ModelEvent> escrowSummary(List<Message> messages) {
            Map<String, String> prompt = promptValues(messages);
            Map<String, Object> evidence = latestToolContent(messages, "escrow-evidence-lookup");
            if (evidence == null) {
            return List.of(
                new ModelEvent.ToolUse(
                    "escrow-evidence-lookup",
                    EscrowEvidenceLookupTool.TOOL_NAME,
                    Map.of(
                        "caseId", prompt.getOrDefault("caseId", ""),
                        "caseType", prompt.getOrDefault("caseType", ""),
                        "orderId", prompt.getOrDefault("orderId", ""),
                        "disputeSummary", prompt.getOrDefault("disputeSummary", ""),
                        "amount", new BigDecimal(prompt.getOrDefault("amount", "0")),
                        "currency", prompt.getOrDefault("currency", ""),
                        "operatorId", prompt.getOrDefault("operatorId", ""),
                        "operatorRole", prompt.getOrDefault("operatorRole", ""))),
                new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }
            return List.of(
                new ModelEvent.ToolUse(
                    "escrow-summary",
                    "structured_output",
                    Map.of(
                        "summary",
                        evidence.get("summary") + " Hold state: " + evidence.get("holdState") + ". Eligibility: "
                            + evidence.get("settlementEligibility") + ".")),
                new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
        }

        private Iterable<ModelEvent> riskSummary(List<Message> messages) {
            Map<String, String> prompt = promptValues(messages);
            Map<String, Object> evidence = latestToolContent(messages, "risk-review-lookup");
            if (evidence == null) {
            return List.of(
                new ModelEvent.ToolUse(
                    "risk-review-lookup",
                    RiskReviewLookupTool.TOOL_NAME,
                    Map.of(
                        "caseId", prompt.getOrDefault("caseId", ""),
                        "caseType", prompt.getOrDefault("caseType", ""),
                        "orderId", prompt.getOrDefault("orderId", ""),
                        "disputeSummary", prompt.getOrDefault("disputeSummary", ""),
                        "operatorRole", prompt.getOrDefault("operatorRole", ""))),
                new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }
            return List.of(
                new ModelEvent.ToolUse(
                    "risk-summary",
                    "structured_output",
                    Map.of(
                        "summary",
                        evidence.get("summary") + " Indicators: " + evidence.get("indicatorSummary") + ". Flags: "
                            + String.join(",", castList(evidence.get("policyFlags"))) + ".")),
                new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
        }

        private Iterable<ModelEvent> workflowDecision(List<Message> messages, ToolSelection toolSelection) {
            if (toolSelection != null) {
                return List.of(
                        new ModelEvent.ToolUse(
                                "workflow-decision",
                                toolSelection.toolName(),
                                Map.of(
                                        "recommendation", "CONTINUED_HOLD",
                                        "recommendationMessage", "case-workflow-agent recommends keeping the hold until finance control confirms the next step under the packaged settlement policy.",
                                        "approvalMessage", "Finance control approval is required for settlement progression after the workflow reviewed the packaged threshold reference.",
                                        "policyReference", WorkflowRuntimeAdapter.POLICY_REFERENCE)),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }

            int toolResultsSeen = countToolResults(messages);
            if (toolResultsSeen == 0) {
                return List.of(
                        new ModelEvent.ToolUse(
                                "workflow-resources",
                                "resource_list",
                                Map.of("location", "classpath:/marketplace-workflow/", "pattern", "**/*.md")),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }

            return List.of(
                    new ModelEvent.ToolUse(
                            "workflow-loop-" + toolResultsSeen,
                            "resource_reader",
                            Map.of("location", "classpath:/marketplace-workflow/policies/settlement-policy-summary.md")),
                    new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
        }

        private int countToolResults(List<Message> messages) {
            int count = 0;
            for (Message message : messages) {
                for (ContentBlock block : message.content()) {
                    if (block instanceof ContentBlock.ToolResult) {
                        count++;
                    }
                }
            }
            return count;
        }

        private Map<String, String> promptValues(List<Message> messages) {
            java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>();
            String text = latestUserText(messages);
            for (String line : text.split("\\R")) {
                int separator = line.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                values.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
            }
            return values;
        }

        private Map<String, Object> latestToolContent(List<Message> messages, String toolUseId) {
            for (int index = messages.size() - 1; index >= 0; index--) {
                Message message = messages.get(index);
                for (ContentBlock block : message.content()) {
                    if (block instanceof ContentBlock.ToolResult toolResult
                            && toolUseId.equals(toolResult.toolUseId())
                            && toolResult.content() instanceof Map<?, ?> content) {
                        java.util.LinkedHashMap<String, Object> values = new java.util.LinkedHashMap<>();
                        content.forEach((key, value) -> values.put(String.valueOf(key), value));
                        return values;
                    }
                }
            }
            return null;
        }

        private String latestUserText(List<Message> messages) {
            for (int index = messages.size() - 1; index >= 0; index--) {
                Message message = messages.get(index);
                for (ContentBlock block : message.content()) {
                    if (block instanceof ContentBlock.Text text) {
                        return text.text();
                    }
                }
            }
            return "";
        }

        private List<String> castList(Object value) {
            if (value instanceof List<?> values) {
                return values.stream().map(String::valueOf).toList();
            }
            return List.of();
        }

        private String agentName(String systemPrompt, String latestUserText) {
            String combined = (systemPrompt == null ? "" : systemPrompt) + "\n" + latestUserText;
            if (combined.contains("shipment-agent")) {
                return "shipment-agent";
            }
            if (combined.contains("escrow-agent")) {
                return "escrow-agent";
            }
            if (combined.contains("risk-agent")) {
                return "risk-agent";
            }
            return "case-workflow-agent";
        }
    }
}