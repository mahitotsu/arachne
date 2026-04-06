package com.mahitotsu.arachne.samples.marketplace.notificationservice;

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
class NotificationArachneConfiguration {

    @Bean
    Tool notificationTemplateLookupTool() {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec(
                        "notification_template_lookup",
                        "Read the notification-service template guidance for the current case outcome before producing structured notification composition.",
                        notificationTemplateLookupSchema());
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("notification_template_lookup", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                Map<String, Object> values = values(input);
                String outcomeType = string(values, "outcomeType");
                String settlementReference = string(values, "settlementReference");
                return ToolResult.success(context.toolUseId(), template(outcomeType, settlementReference));
            }
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "marketplace.notification-runtime.arachne", name = "model-mode", havingValue = "deterministic", matchIfMissing = true)
    Model marketplaceNotificationDeterministicModel() {
        return new NotificationDeterministicModel();
    }

    private static ObjectNode notificationTemplateLookupSchema() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("type", "object");
        ObjectNode properties = root.putObject("properties");
        properties.putObject("caseId").put("type", "string");
        properties.putObject("outcomeType").put("type", "string");
        properties.putObject("outcomeStatus").put("type", "string");
        properties.putObject("settlementReference").put("type", "string");
        root.putArray("required").add("caseId").add("outcomeType").add("outcomeStatus").add("settlementReference");
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

    private static Map<String, Object> template(String outcomeType, String settlementReference) {
        String participantSummary;
        String operatorSummary;
        if ("REFUND_EXECUTED".equalsIgnoreCase(outcomeType)) {
            participantSummary = "Send a participant update confirming the refund for settlement reference " + settlementReference + ".";
            operatorSummary = "Send an operator update confirming the refund for settlement reference " + settlementReference + ".";
        }
        else if ("CONTINUED_HOLD_RECORDED".equalsIgnoreCase(outcomeType)) {
            participantSummary = "Send a participant update confirming the continued hold for settlement reference " + settlementReference + ".";
            operatorSummary = "Send an operator update confirming the continued hold for settlement reference " + settlementReference + ".";
        }
        else {
            participantSummary = "Send a participant update for settlement reference " + settlementReference + ".";
            operatorSummary = "Send an operator update for settlement reference " + settlementReference + ".";
        }
        return Map.of(
                "participantChannel", "EMAIL",
                "operatorChannel", "INTERNAL_DASHBOARD",
                "participantSummary", participantSummary,
                "operatorSummary", operatorSummary,
                "dispatchSummary", "Notification service queued participant and operator notifications for settlement reference " + settlementReference + ".");
    }

    private static final class NotificationDeterministicModel implements Model {

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
            Map<String, Object> template = latestToolContent(messages, "notification-template-lookup");
            if (template == null) {
                return List.of(
                        new ModelEvent.ToolUse(
                                "notification-template-lookup",
                                "notification_template_lookup",
                                Map.of(
                                        "caseId", prompt.getOrDefault("caseId", ""),
                                        "outcomeType", prompt.getOrDefault("outcomeType", ""),
                                        "outcomeStatus", prompt.getOrDefault("outcomeStatus", ""),
                                        "settlementReference", prompt.getOrDefault("settlementReference", ""))),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }
            return List.of(
                    new ModelEvent.ToolUse(
                            "notification-composition",
                            "structured_output",
                            Map.of(
                                    "participantChannel", template.get("participantChannel"),
                                    "operatorChannel", template.get("operatorChannel"),
                                    "participantSummary", template.get("participantSummary"),
                                    "operatorSummary", template.get("operatorSummary"),
                                    "summary", template.get("dispatchSummary"))),
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