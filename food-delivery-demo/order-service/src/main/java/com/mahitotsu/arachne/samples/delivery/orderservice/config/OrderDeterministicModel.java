package com.mahitotsu.arachne.samples.delivery.orderservice.config;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mahitotsu.arachne.strands.model.Model;
import com.mahitotsu.arachne.strands.model.ModelEvent;
import com.mahitotsu.arachne.strands.model.ToolSelection;
import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.tool.StructuredOutputTool;
import com.mahitotsu.arachne.strands.types.ContentBlock;
import com.mahitotsu.arachne.strands.types.Message;

final class OrderDeterministicModel implements Model {

    private static final List<String> DIRECT_ITEM_KEYWORDS = List.of(
            "セット", "box", "ボックス", "burger", "バーガー", "wrap", "ラップ", "soda", "ソーダ",
            "latte", "ラテ", "フライ", "fries", "チキン", "chicken", "サーモン", "salmon", "bowl", "dessert");

    @Override
    public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools) {
        return converse(messages, tools, null, null);
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
        Map<String, String> args = latestRequestArgs(messages);
        String rawMessage = args.getOrDefault("raw_message", "");
        String previousProposalMessage = args.getOrDefault("previous_proposal_message", "");
        String structuredSummary = structuredSummary(args);
        String customerMessage = firstNonBlank(rawMessage, structuredSummary, previousProposalMessage, args.get("recent_order"));
        String intentMode = intentMode(args, customerMessage);
        String directItemHint = "DIRECT_ITEM".equals(intentMode) ? customerMessage : "";
        String menuQuery = buildMenuQuery(customerMessage, directItemHint, structuredSummary);
        String recentOrderSummary = needsRecentOrderContext(customerMessage) ? args.getOrDefault("recent_order", "") : "";
        Map<String, Object> content = Map.of(
                "customerMessage", customerMessage,
                "intentMode", intentMode,
                "menuQuery", menuQuery,
                "directItemHint", directItemHint,
                "partySize", parseInteger(args.get("party_size")),
                "budgetUpperBound", parseBigDecimal(args.get("budget_upper_bound")),
                "childCount", parseInteger(args.get("child_count")),
                "recentOrderSummary", recentOrderSummary.isBlank() ? null : recentOrderSummary,
                "rationale", rationale(intentMode));
        if (structuredOutputRequested(tools)) {
            return List.of(
                    new ModelEvent.ToolUse("structured-order-intent", StructuredOutputTool.DEFAULT_NAME, content),
                    new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
        }
        return List.of(
                new ModelEvent.TextDelta(String.valueOf(content)),
                new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
    }

    private String intentMode(Map<String, String> args, String customerMessage) {
        if (args.containsKey("refinement") && args.containsKey("previous_proposal_message")) {
            return "REFINEMENT";
        }
        if (needsRecentOrderContext(customerMessage)) {
            return "REORDER";
        }
        if (looksLikeDirectItemRequest(customerMessage)) {
            return "DIRECT_ITEM";
        }
        return "RECOMMENDATION";
    }

    private boolean looksLikeDirectItemRequest(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("おすすめ") || normalized.contains("何か") || normalized.contains("向け") || normalized.contains("いつもの")) {
            return false;
        }
        return DIRECT_ITEM_KEYWORDS.stream().anyMatch(normalized::contains);
    }

    private boolean needsRecentOrderContext(String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return normalized.contains("前回") || normalized.contains("いつもの") || normalized.contains("same as last time");
    }

    private String buildMenuQuery(String customerMessage, String directItemHint, String structuredSummary) {
        LinkedHashMap<String, String> parts = new LinkedHashMap<>();
        putIfPresent(parts, "direct", directItemHint);
        putIfPresent(parts, "customer", customerMessage);
        putIfPresent(parts, "structured", structuredSummary);
        return String.join("、", parts.values());
    }

    private void putIfPresent(Map<String, String> parts, String key, String value) {
        String normalized = blankToNull(value);
        if (normalized != null) {
            parts.put(key, normalized);
        }
    }

    private String structuredSummary(Map<String, String> args) {
        StringBuilder builder = new StringBuilder();
        append(builder, args.get("party_size") == null ? null : args.get("party_size") + "人");
        append(builder, args.get("budget_upper_bound") == null ? null : args.get("budget_upper_bound") + "円以内");
        append(builder, args.get("child_count") == null ? null : "子ども" + args.get("child_count") + "人");
        return builder.toString();
    }

    private void append(StringBuilder builder, String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('、');
        }
        builder.append(normalized);
    }

    private String rationale(String intentMode) {
        return switch (intentMode) {
            case "DIRECT_ITEM" -> "商品名らしい指定があるため catalog grounding に直接渡します。";
            case "REORDER" -> "再注文の文脈があるため前回注文を参照する形に正規化しました。";
            case "REFINEMENT" -> "既存提案の再調整として扱います。";
            default -> "人数や予算などの条件から recommendation planning として扱います。";
        };
    }

    private Integer parseInteger(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : Integer.valueOf(normalized);
    }

    private BigDecimal parseBigDecimal(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : new BigDecimal(normalized);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = blankToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return "";
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
        return "";
    }

    private Map<String, String> latestRequestArgs(List<Message> messages) {
        String raw = latestUserText(messages);
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (String line : raw.split("\\R")) {
            int separator = line.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            values.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
        }
        return values;
    }

    private boolean structuredOutputRequested(List<ToolSpec> tools) {
        return tools.stream().anyMatch(tool -> StructuredOutputTool.DEFAULT_NAME.equals(tool.name()));
    }
}