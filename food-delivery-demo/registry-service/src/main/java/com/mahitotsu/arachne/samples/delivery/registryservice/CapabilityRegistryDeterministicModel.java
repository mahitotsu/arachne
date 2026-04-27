package com.mahitotsu.arachne.samples.delivery.registryservice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mahitotsu.arachne.strands.model.Model;
import com.mahitotsu.arachne.strands.model.ModelEvent;
import com.mahitotsu.arachne.strands.model.ToolSelection;
import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.types.ContentBlock;
import com.mahitotsu.arachne.strands.types.Message;

final class CapabilityRegistryDeterministicModel implements Model {

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
        Map<String, Object> capabilityResult = latestToolContent(messages, "capability-match");
        if (capabilityResult == null) {
            return List.of(
                    new ModelEvent.ToolUse("capability-match", "capability_match", Map.of(
                            "query", latestUserText(messages),
                            "availableOnly", true)),
                    new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
        }
        List<String> names = extractServiceNames(capabilityResult);
        String summary = names.isEmpty()
                ? "利用可能な候補は見つかりませんでした。"
                : "候補: " + String.join("、", names);
        return List.of(
                new ModelEvent.TextDelta(summary),
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

    private List<String> extractServiceNames(Map<String, Object> capabilityResult) {
        Object rawMatches = capabilityResult.get("matches");
        if (!(rawMatches instanceof List<?> matches)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (Object match : matches) {
            if (match instanceof Map<?, ?> matchMap) {
                Object serviceName = matchMap.get("serviceName");
                if (serviceName != null) {
                    names.add(String.valueOf(serviceName));
                }
            }
        }
        return names;
    }
}