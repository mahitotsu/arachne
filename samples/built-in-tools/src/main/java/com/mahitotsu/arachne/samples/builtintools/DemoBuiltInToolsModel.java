package com.mahitotsu.arachne.samples.builtintools;

import java.util.List;
import java.util.Map;

import com.mahitotsu.arachne.strands.model.Model;
import com.mahitotsu.arachne.strands.model.ModelEvent;
import com.mahitotsu.arachne.strands.model.ToolSelection;
import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.types.ContentBlock;
import com.mahitotsu.arachne.strands.types.Message;

final class DemoBuiltInToolsModel implements Model {

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
        List<String> toolNames = tools.stream().map(ToolSpec::name).toList();
        String latestUserText = latestUserText(messages);

        if ("Run default profile.".equals(latestUserText)) {
            if (toolNames.contains("current_time") && latestToolResult(messages, "current_time") == null) {
                return List.of(
                        new ModelEvent.ToolUse("time-1", "current_time", Map.of("zoneId", "Asia/Tokyo")),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }
            if (toolNames.contains("resource_reader") && latestResourceContent(messages) == null) {
                return List.of(
                        new ModelEvent.ToolUse(
                                "read-1",
                                "resource_reader",
                                Map.of("location", "classpath:/builtin/release-note.md")),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }
            String zoneId = String.valueOf(valueFromToolResult(messages, "current_time", "zoneId"));
            String instant = String.valueOf(valueFromToolResult(messages, "current_time", "instant"));
            String note = latestResourceContent(messages);
            return List.of(
                    new ModelEvent.TextDelta("At " + instant + " in " + zoneId + " I read the sample note: " + firstLine(note)),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        }

                if ("Run calculator profile.".equals(latestUserText)) {
                    if (toolNames.contains("calculator") && latestToolResult(messages, "calculator") == null) {
                    return List.of(
                        new ModelEvent.ToolUse(
                            "calc-1",
                            "calculator",
                            Map.of("expression", "1 + 2 * (3 + 4)")),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
                    }
                    Object result = valueFromToolResult(messages, "calculator", "result");
                    return List.of(
                        new ModelEvent.TextDelta("Calculator agent computed 1 + 2 * (3 + 4) = " + result),
                        new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
                }

        if ("Run reader profile.".equals(latestUserText)) {
            if (toolNames.contains("resource_list") && latestToolResult(messages, "resource_list") == null) {
                return List.of(
                        new ModelEvent.ToolUse(
                                "list-1",
                                "resource_list",
                                Map.of("location", "classpath:/builtin/", "pattern", "**/*.md")),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }
            if (toolNames.contains("resource_reader") && latestResourceContent(messages) == null) {
                return List.of(
                        new ModelEvent.ToolUse(
                                "read-2",
                                "resource_reader",
                                Map.of("location", "classpath:/builtin/release-note.md")),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }
            Object listedResources = valueFromToolResult(messages, "resource_list", "resources");
            String note = latestResourceContent(messages);
            return List.of(
                    new ModelEvent.TextDelta("Reader agent found " + listedResources + " and read: " + firstLine(note)),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        }

        return List.of(
                new ModelEvent.TextDelta("No demo path matched."),
                new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
    }

    private static Map<?, ?> latestToolResult(List<Message> messages, String type) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            Message message = messages.get(index);
            for (ContentBlock block : message.content()) {
                if (block instanceof ContentBlock.ToolResult toolResult
                        && toolResult.content() instanceof Map<?, ?> content
                        && type.equals(content.get("type"))) {
                    return content;
                }
            }
        }
        return null;
    }

    private static Object valueFromToolResult(List<Message> messages, String type, String key) {
        Map<?, ?> content = latestToolResult(messages, type);
        return content == null ? "unknown" : content.get(key);
    }

    private static String latestUserText(List<Message> messages) {
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

    private static String latestResourceContent(List<Message> messages) {
        Map<?, ?> content = latestToolResult(messages, "resource");
        if (content == null) {
            return null;
        }
        Object body = content.get("content");
        return body instanceof String text ? text : null;
    }

    private static String firstLine(String text) {
        if (text == null || text.isBlank()) {
            return "(no content)";
        }
        return text.lines().findFirst().orElse(text).trim();
    }
}