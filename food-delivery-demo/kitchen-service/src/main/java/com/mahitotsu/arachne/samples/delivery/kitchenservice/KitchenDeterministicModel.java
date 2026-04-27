package com.mahitotsu.arachne.samples.delivery.kitchenservice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mahitotsu.arachne.strands.model.Model;
import com.mahitotsu.arachne.strands.model.ModelEvent;
import com.mahitotsu.arachne.strands.model.ToolSelection;
import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.types.ContentBlock;
import com.mahitotsu.arachne.strands.types.Message;

final class KitchenDeterministicModel implements Model {

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
        Map<String, String> requestArgs = latestRequestArgs(messages);
        Map<String, Object> toolContent = latestToolContent(messages, "kitchen-lookup");
        if (toolContent == null) {
            return List.of(
                    new ModelEvent.ToolUse(
                            "kitchen-lookup",
                            "kitchen_inventory_lookup",
                            Map.of("items", requestArgs.getOrDefault("items", ""))),
                    new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
        }

        Map<String, Object> scheduleContent = latestToolContent(messages, "prep-scheduler");
        if (scheduleContent == null) {
            return List.of(
                    new ModelEvent.ToolUse(
                            "prep-scheduler",
                            "prep_scheduler",
                            Map.of("items", requestArgs.getOrDefault("items", ""))),
                    new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
        }

        List<String> unavailableItemIds = KitchenArachneConfiguration.stringList(toolContent.get("unavailableItemIds"));
        Map<String, Object> substitutionContent = latestToolContent(messages, "menu-substitution-lookup");
        if (!unavailableItemIds.isEmpty() && substitutionContent == null) {
            return List.of(
                    new ModelEvent.ToolUse(
                            "menu-substitution-lookup",
                            "menu_substitution_lookup",
                            Map.of(
                                    "unavailableItemIds", unavailableItemIds,
                                    "customerMessage", requestArgs.getOrDefault("message", ""))),
                    new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
        }

        String inventorySummary = String.valueOf(toolContent.getOrDefault("inventorySummary", "kitchen is ready"));
        String scheduleSummary = String.valueOf(scheduleContent.getOrDefault("scheduleSummary", ""));
        if (substitutionContent != null) {
            return List.of(
                    new ModelEvent.TextDelta("kitchen-agent がラインを確認しました: "
                            + inventorySummary
                            + " " + scheduleSummary
                            + ". menu-agent に相談して "
                            + substitutionContent.getOrDefault("substitutionSummary", "最適な代替品")
                            + " を承認しました。"),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        }
        return List.of(
                new ModelEvent.TextDelta("kitchen-agent がラインを確認しました: " + inventorySummary + " " + scheduleSummary),
                new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
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
                    return text.text();
                }
            }
        }
        return "";
    }

    private Map<String, String> latestRequestArgs(List<Message> messages) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (String line : latestUserText(messages).split("\\R")) {
            int separator = line.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            values.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
        }
        return values;
    }
}