package com.mahitotsu.arachne.samples.delivery.menuservice.config;

import static com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuTypes.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mahitotsu.arachne.strands.model.Model;
import com.mahitotsu.arachne.strands.model.ModelEvent;
import com.mahitotsu.arachne.strands.model.ToolSelection;
import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.tool.StructuredOutputTool;
import com.mahitotsu.arachne.strands.types.ContentBlock;
import com.mahitotsu.arachne.strands.types.Message;

final class MenuDeterministicModel implements Model {

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
        Map<String, String> requestArgs = latestRequestArgs(messages);
        String userText = requestArgs.getOrDefault("query", latestUserText(messages));
        boolean substitutionQuery = requestArgs.containsKey("unavailableItemId");
        boolean isFamilyQuery = isFamilyQuery(userText);

        if (!substitutionQuery && isFamilyQuery && !hasSkillActivation(messages)) {
            return List.of(
                    new ModelEvent.ToolUse("skill-family", "activate_skill", Map.of("name", "family-order-guide")),
                    new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
        }

        String toolUseId = substitutionQuery ? "menu-substitution-lookup" : "menu-lookup";
        Map<String, Object> toolContent = latestToolContent(messages, toolUseId);
        if (toolContent == null) {
            if (substitutionQuery) {
                return List.of(
                        new ModelEvent.ToolUse(
                                "menu-substitution-lookup",
                                "menu_substitution_lookup",
                                Map.of(
                                        "unavailableItemId", requestArgs.getOrDefault("unavailableItemId", ""),
                                        "customerMessage", requestArgs.getOrDefault("customerMessage", ""))),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }
            return List.of(
                    new ModelEvent.ToolUse("menu-lookup", "catalog_lookup_tool", Map.of("query", userText)),
                    new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
        }

                if (!substitutionQuery && latestToolContent(messages, "menu-total") == null) {
                    Object itemIds = toolContent.getOrDefault("itemIds", List.of());
            return List.of(
                    new ModelEvent.ToolUse("menu-total", "calculate_total_tool", Map.of("itemIds", itemIds)),
                    new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
        }

                if (structuredOutputRequested(tools)) {
                    if (substitutionQuery) {
                    return List.of(
                        new ModelEvent.ToolUse(
                            "structured-menu-substitution",
                            StructuredOutputTool.DEFAULT_NAME,
                            Map.of(
                                "selectedItemIds", toolContent.getOrDefault("itemIds", List.of()),
                                "summary", "menu-agent が kitchen-agent に検証させる代替品として "
                                    + toolContent.getOrDefault("substitutionSummary", "最も近い代替品")
                                    + " を提案しました。")),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
                    }
                    return List.of(
                        new ModelEvent.ToolUse(
                            "structured-menu-suggestion",
                            StructuredOutputTool.DEFAULT_NAME,
                            Map.of(
                                "selectedItemIds", toolContent.getOrDefault("itemIds", List.of()),
                                "skillTag", isFamilyQuery ? "family-order-guide" : "",
                                "recommendationReason", isFamilyQuery
                                    ? "人数と予算に合う構成を優先しました。"
                                    : "リクエストに最も近い候補を選びました。")),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
                }

        if (substitutionQuery) {
            return List.of(
                    new ModelEvent.TextDelta("menu-agent が kitchen-agent に検証させる代替品として "
                            + toolContent.getOrDefault("substitutionSummary", "最も近い代替品")
                            + " を提案しました。"),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        }

        String prefix = isFamilyQuery ? "[family-order-guide] " : "";
        return List.of(
                new ModelEvent.TextDelta(prefix + "menu-agent が " + toolContent.getOrDefault("matchSummary", "本日の人気コンボ")
                        + " をおすすめします。現在のメニューに沿った内容でチャットで簡単に確認できます。"),
                new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
    }

    private boolean isFamilyQuery(String text) {
        if (text == null) {
            return false;
        }
        return text.contains("子ども") || text.contains("家族") || text.contains("kids")
                || text.contains("2人") || text.contains("3人") || text.contains("4人");
    }

    private boolean hasSkillActivation(List<Message> messages) {
        for (Message message : messages) {
            for (ContentBlock block : message.content()) {
                if (block instanceof ContentBlock.ToolResult toolResult
                        && toolResult.content() instanceof Map<?, ?> content
                        && "skill_activation".equals(content.get("type"))) {
                    return true;
                }
            }
        }
        return false;
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
            if (message.role() != Message.Role.USER) {
                continue;
            }
            for (ContentBlock block : message.content()) {
                if (block instanceof ContentBlock.Text text) {
                    return text.text().replace("query=", "");
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