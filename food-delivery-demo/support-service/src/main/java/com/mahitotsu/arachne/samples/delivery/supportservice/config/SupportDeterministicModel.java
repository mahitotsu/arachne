package com.mahitotsu.arachne.samples.delivery.supportservice.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mahitotsu.arachne.samples.delivery.supportservice.domain.HandoffInstruction;
import com.mahitotsu.arachne.samples.delivery.supportservice.domain.SupportIntent;
import com.mahitotsu.arachne.strands.model.Model;
import com.mahitotsu.arachne.strands.model.ModelEvent;
import com.mahitotsu.arachne.strands.model.ToolSelection;
import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.tool.StructuredOutputTool;
import com.mahitotsu.arachne.strands.types.ContentBlock;
import com.mahitotsu.arachne.strands.types.Message;

final class SupportDeterministicModel implements Model {

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
        String query = latestUserText(messages);
        SupportIntent intent = SupportIntent.fromMessage(query);
        if (intent.includesCampaigns() && latestToolContent(messages, "campaign-lookup") == null) {
            return List.of(
                    new ModelEvent.ToolUse("campaign-lookup", "campaign_lookup", Map.of()),
                    new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
        }
        if (intent.includesStatuses() && latestToolContent(messages, "status-lookup") == null) {
            return List.of(
                    new ModelEvent.ToolUse("status-lookup", "service_status_lookup", Map.of()),
                    new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
        }
        if (intent.includesOrderHistory() && latestToolContent(messages, "history-lookup") == null) {
            return List.of(
                    new ModelEvent.ToolUse(
                            "history-lookup",
                            "order_history_lookup",
                            Map.of()),
                    new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
        }
        if (intent.includesFeedback() && latestToolContent(messages, "feedback-lookup") == null) {
            return List.of(
                    new ModelEvent.ToolUse("feedback-lookup", "feedback_lookup", Map.of("query", query, "limit", 3)),
                    new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
        }
        if (intent.includesFaq() && latestToolContent(messages, "faq-lookup") == null) {
            return List.of(
                    new ModelEvent.ToolUse("faq-lookup", "faq_lookup", Map.of("query", query, "limit", 3)),
                    new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
        }

        String summary = buildSummary(
                query,
                latestToolContent(messages, "faq-lookup"),
                latestToolContent(messages, "campaign-lookup"),
                latestToolContent(messages, "status-lookup"),
                latestToolContent(messages, "history-lookup"),
                latestToolContent(messages, "feedback-lookup"));
        HandoffInstruction handoff = HandoffInstruction.fromMessage(query);
        if (structuredOutputRequested(tools)) {
            return List.of(
                new ModelEvent.ToolUse(
                    "structured-support",
                    StructuredOutputTool.DEFAULT_NAME,
                    Map.of(
                        "summary", summary,
                        "handoffTarget", handoff.target(),
                        "handoffMessage", handoff.message())),
                new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
        }
        return List.of(
                new ModelEvent.TextDelta(summary),
                new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
    }

    private String buildSummary(
            String query,
            Map<String, Object> faqResult,
            Map<String, Object> campaignResult,
            Map<String, Object> statusResult,
            Map<String, Object> historyResult,
            Map<String, Object> feedbackResult) {
        List<String> sections = new ArrayList<>();
        List<String> faqAnswers = extractStrings(faqResult, "matches", "answer");
        if (!faqAnswers.isEmpty()) {
            sections.add("FAQ: " + faqAnswers.getFirst());
        }
        List<String> campaigns = extractStrings(campaignResult, "campaigns", "title");
        if (!campaigns.isEmpty()) {
            sections.add("キャンペーン: " + String.join("、", campaigns));
        }
        List<String> statuses = extractPairs(statusResult, "services", "serviceName", "status");
        if (!statuses.isEmpty()) {
            sections.add("稼働状況: " + String.join("、", statuses));
        }
        List<String> orders = extractStrings(historyResult, "orders", "itemSummary");
        if (!orders.isEmpty()) {
            sections.add("直近注文: " + orders.getFirst());
        }
        List<String> feedback = extractStrings(feedbackResult, "entries", "category");
        if (!feedback.isEmpty()) {
            sections.add("類似問い合わせ: " + String.join("、", feedback));
        }
        if (sections.isEmpty()) {
            sections.add("お問い合わせを受け付けました。状況や注文番号が分かれば追加で案内できます。");
        }
        HandoffInstruction handoff = HandoffInstruction.fromMessage(query);
        if (!handoff.target().isBlank()) {
            sections.add("[HANDOFF: " + handoff.target() + "]\n" + handoff.message());
        }
        return String.join(" ", sections);
    }

    private Map<String, Object> latestToolContent(List<Message> messages, String toolUseId) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            Message message = messages.get(index);
            for (ContentBlock block : message.content()) {
                if (block instanceof ContentBlock.ToolResult result
                        && toolUseId.equals(result.toolUseId())
                        && result.content() instanceof Map<?, ?> content) {
                    LinkedHashMap<String, Object> values = new LinkedHashMap<>();
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
            List<String> texts = message.content().stream()
                    .filter(ContentBlock.Text.class::isInstance)
                    .map(ContentBlock.Text.class::cast)
                    .map(ContentBlock.Text::text)
                    .toList();
            if (!texts.isEmpty()) {
                return String.join(" ", texts);
            }
        }
        return "";
    }

    private List<String> extractStrings(Map<String, Object> toolResult, String listKey, String valueKey) {
        if (toolResult == null) {
            return List.of();
        }
        Object rawItems = toolResult.get(listKey);
        if (!(rawItems instanceof List<?> items)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof Map<?, ?> entry) {
                Object value = entry.get(valueKey);
                if (value != null && !String.valueOf(value).isBlank()) {
                    values.add(String.valueOf(value));
                }
            }
        }
        return values;
    }

    private List<String> extractPairs(Map<String, Object> toolResult, String listKey, String leftKey, String rightKey) {
        if (toolResult == null) {
            return List.of();
        }
        Object rawItems = toolResult.get(listKey);
        if (!(rawItems instanceof List<?> items)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof Map<?, ?> entry) {
                Object left = entry.get(leftKey);
                Object right = entry.get(rightKey);
                if (left != null && right != null) {
                    values.add(left + "=" + right);
                }
            }
        }
        return values;
    }

    private boolean structuredOutputRequested(List<ToolSpec> tools) {
        return tools.stream().anyMatch(tool -> StructuredOutputTool.DEFAULT_NAME.equals(tool.name()));
    }
}