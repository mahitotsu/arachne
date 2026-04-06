package com.mahitotsu.arachne.samples.marketplace.shipmentservice;

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
class ShipmentArachneConfiguration {

    @Bean
    Tool shipmentRecordLookupTool(ShipmentRepository repository) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec(
                        "shipment_record_lookup",
                        "Read the shipment-service record for the current case before drafting shipment evidence or specialist review output.",
                        shipmentLookupSchema());
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("shipment_record_lookup", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                Map<String, Object> values = values(input);
                ShipmentRecord record = repository.findShipmentRecord(string(values, "caseId"))
                        .orElseThrow(() -> new IllegalStateException("Shipment record missing for case " + string(values, "caseId")));
                return ToolResult.success(context.toolUseId(), Map.of(
                        "trackingNumber", record.trackingNumber(),
                        "milestoneSummary", record.milestoneSummary(),
                        "deliveryConfidence", record.deliveryConfidence(),
                        "shippingExceptionSummary", record.shippingExceptionSummary()));
            }
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "marketplace.shipment-runtime.arachne", name = "model-mode", havingValue = "deterministic", matchIfMissing = true)
    Model marketplaceShipmentDeterministicModel() {
        return new ShipmentDeterministicModel();
    }

    private static ObjectNode shipmentLookupSchema() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("type", "object");
        ObjectNode properties = root.putObject("properties");
        properties.putObject("caseId").put("type", "string");
        properties.putObject("caseType").put("type", "string");
        properties.putObject("orderId").put("type", "string");
        properties.putObject("disputeSummary").put("type", "string");
        root.putArray("required").add("caseId").add("caseType").add("orderId").add("disputeSummary");
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

    private static final class ShipmentDeterministicModel implements Model {

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
            Map<String, Object> record = latestToolContent(messages, "shipment-record-lookup");
            if (record == null) {
                return List.of(
                        new ModelEvent.ToolUse(
                                "shipment-record-lookup",
                                "shipment_record_lookup",
                                Map.of(
                                        "caseId", prompt.getOrDefault("caseId", ""),
                                        "caseType", prompt.getOrDefault("caseType", ""),
                                        "orderId", prompt.getOrDefault("orderId", ""),
                                        "disputeSummary", prompt.getOrDefault("disputeSummary", ""))),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }
            if ("specialist-review".equalsIgnoreCase(prompt.get("mode"))) {
                return structuredOutput("shipment-review", Map.of(
                        "summary",
                        "shipment-agent reviewed the operator instruction \""
                                + prompt.getOrDefault("instruction", "")
                                + "\" against shipment-service records: "
                                + record.get("milestoneSummary")
                                + " Tracking number: "
                                + record.get("trackingNumber")
                                + ". "
                                + record.get("shippingExceptionSummary")));
            }
            return structuredOutput("shipment-evidence", Map.of(
                    "trackingNumber", record.get("trackingNumber"),
                    "milestoneSummary", record.get("milestoneSummary"),
                    "deliveryConfidence", record.get("deliveryConfidence"),
                    "shippingExceptionSummary", record.get("shippingExceptionSummary")));
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