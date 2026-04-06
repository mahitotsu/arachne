package com.mahitotsu.arachne.samples.marketplace.escrowservice;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mahitotsu.arachne.strands.model.Model;
import com.mahitotsu.arachne.strands.model.ModelEvent;
import com.mahitotsu.arachne.strands.model.ToolSelection;
import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.tool.Tool;
import com.mahitotsu.arachne.strands.tool.ToolInvocationContext;
import com.mahitotsu.arachne.strands.tool.ToolResult;
import com.mahitotsu.arachne.strands.types.ContentBlock;
import com.mahitotsu.arachne.strands.types.Message;

@Configuration
class EscrowArachneConfiguration {

    @Bean
    Tool escrowCaseLookupTool(EscrowRepository repository) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec(
                        "escrow_case_lookup",
                        "Read the escrow-service case record before producing structured escrow evidence or specialist review output.",
                        escrowLookupSchema());
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("escrow_case_lookup", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                Map<String, Object> values = values(input);
                EscrowRecord record = repository.findCaseRecord(string(values, "caseId"))
                        .orElseThrow(() -> new IllegalStateException("Escrow record missing for case " + string(values, "caseId")));
                String caseType = string(values, "caseType");
                return ToolResult.success(context.toolUseId(), Map.of(
                        "holdState", record.holdState(),
                    "settlementEligibility", settlementEligibility(record.holdState(), caseType),
                        "amount", record.amount(),
                        "currency", record.currency(),
                        "priorSettlementStatus", record.priorSettlementStatus(),
                    "summary", summary(record, caseType)));
            }
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "marketplace.escrow-runtime.arachne", name = "model-mode", havingValue = "deterministic", matchIfMissing = true)
    Model marketplaceEscrowDeterministicModel() {
        return new EscrowDeterministicModel();
    }

    private static ObjectNode escrowLookupSchema() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("type", "object");
        ObjectNode properties = root.putObject("properties");
        properties.putObject("caseId").put("type", "string");
        properties.putObject("caseType").put("type", "string");
        properties.putObject("orderId").put("type", "string");
        properties.putObject("disputeSummary").put("type", "string");
        properties.putObject("amount").put("type", "number");
        properties.putObject("currency").put("type", "string");
        properties.putObject("operatorId").put("type", "string");
        properties.putObject("operatorRole").put("type", "string");
        root.putArray("required").add("caseId").add("caseType").add("amount").add("currency");
        root.put("additionalProperties", false);
        return root;
    }

    private static Map<String, Object> values(Object input) {
        if (input instanceof Map<?, ?> rawValues) {
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            rawValues.forEach((key, value) -> values.put(String.valueOf(key), value));
            return values;
        }
        return Map.of();
    }

    private static String string(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static String settlementEligibility(String holdState, String caseType) {
        if ("DELIVERED_BUT_DAMAGED".equalsIgnoreCase(caseType)) {
            return "REQUIRES_DAMAGE_REVIEW";
        }
        if ("HIGH_RISK_SETTLEMENT_HOLD".equalsIgnoreCase(caseType)) {
            return "RESTRICTED_PENDING_RISK_CLEARANCE";
        }
        if ("SELLER_CANCELLATION_AFTER_AUTHORIZATION".equalsIgnoreCase(caseType)) {
            return "ELIGIBLE_FOR_REFUND_REVIEW";
        }
        return "HELD".equalsIgnoreCase(holdState)
                ? "ELIGIBLE_FOR_CONTINUED_HOLD"
                : "ELIGIBLE_FOR_REFUND_REVIEW";
    }

    private static String summary(EscrowRecord record, String caseType) {
        if ("REFUNDED".equalsIgnoreCase(record.holdState())) {
            return "Escrow recorded a completed refund for the case and retains the settlement audit trail.";
        }
        if ("CONTINUED_HOLD_RECORDED".equalsIgnoreCase(record.priorSettlementStatus())) {
            return "Escrow still holds the funds and the latest settlement decision recorded a continued hold.";
        }
        if ("DELIVERED_BUT_DAMAGED".equalsIgnoreCase(caseType)) {
            return "Escrow still holds the funds while damage evidence and seller response are collected.";
        }
        if ("HIGH_RISK_SETTLEMENT_HOLD".equalsIgnoreCase(caseType)) {
            return "Escrow still holds the funds because the case is under a high-risk settlement hold.";
        }
        if ("SELLER_CANCELLATION_AFTER_AUTHORIZATION".equalsIgnoreCase(caseType)) {
            return "Escrow still holds the authorized funds because the seller cancelled before fulfillment completed.";
        }
        return "Escrow still holds the authorized funds and no prior refund has been executed.";
    }

    private static final class EscrowDeterministicModel implements Model {

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools) {
            return converse(messages, tools, null, null);
        }

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools, String systemPrompt) {
            return converse(messages, tools, systemPrompt, null);
        }

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools, String systemPrompt, ToolSelection toolSelection) {
            Map<String, String> prompt = promptValues(messages);
            Map<String, Object> record = latestToolContent(messages, "escrow-case-lookup");
            if (record == null) {
                return List.of(
                        new ModelEvent.ToolUse(
                                "escrow-case-lookup",
                                "escrow_case_lookup",
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
            if ("specialist-review".equalsIgnoreCase(prompt.get("mode"))) {
                return structuredOutput("escrow-review", Map.of(
                        "summary",
                        "escrow-agent reviewed the operator instruction \""
                                + prompt.getOrDefault("instruction", "")
                                + "\" against escrow-service records: "
                                + record.get("summary")
                                + " Hold state: "
                                + record.get("holdState")
                                + ". Eligibility: "
                                + record.get("settlementEligibility")
                                + "."));
            }
            return structuredOutput("escrow-evidence", Map.of(
                    "holdState", record.get("holdState"),
                    "settlementEligibility", record.get("settlementEligibility"),
                    "amount", record.get("amount"),
                    "currency", record.get("currency"),
                    "priorSettlementStatus", record.get("priorSettlementStatus"),
                    "summary", record.get("summary")));
        }

        private Iterable<ModelEvent> structuredOutput(String toolUseId, Map<String, Object> payload) {
            return List.of(
                    new ModelEvent.ToolUse(toolUseId, "structured_output", payload),
                    new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
        }

        private Map<String, String> promptValues(List<Message> messages) {
            LinkedHashMap<String, String> values = new LinkedHashMap<>();
            String text = latestUserText(messages);
            if (text == null) {
                return values;
            }
            for (String line : text.split("\\R")) {
                int separator = line.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                values.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
            }
            return values;
        }

        private String latestUserText(List<Message> messages) {
            for (int index = messages.size() - 1; index >= 0; index--) {
                Message message = messages.get(index);
                if (message.role() != Message.Role.USER) {
                    continue;
                }
                for (ContentBlock block : message.content()) {
                    if (block instanceof ContentBlock.Text text) {
                        return text.text();
                    }
                }
            }
            return null;
        }

        private Map<String, Object> latestToolContent(List<Message> messages, String toolUseId) {
            for (int index = messages.size() - 1; index >= 0; index--) {
                Message message = messages.get(index);
                for (ContentBlock block : message.content()) {
                    if (block instanceof ContentBlock.ToolResult toolResult
                            && toolUseId.equals(toolResult.toolUseId())
                            && toolResult.content() instanceof Map<?, ?> content) {
                        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
                        content.forEach((key, value) -> values.put(String.valueOf(key), value));
                        return values;
                    }
                }
            }
            return null;
        }
    }
}