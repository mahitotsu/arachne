package io.arachne.samples.skillactivation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.arachne.strands.model.Model;
import io.arachne.strands.model.ModelEvent;
import io.arachne.strands.model.ToolSelection;
import io.arachne.strands.model.ToolSpec;
import io.arachne.strands.types.ContentBlock;
import io.arachne.strands.types.Message;

final class DemoSkillsModel implements Model {

    private final List<String> systemPrompts = new ArrayList<>();

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
        systemPrompts.add(systemPrompt == null ? "" : systemPrompt);

        if (!hasSkillActivation(messages)) {
            return List.of(
                    new ModelEvent.ToolUse("skill-1", "activate_skill", Map.of("name", "release-checklist")),
                    new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
        }

        if (!hasSkillResource(messages)) {
            return List.of(
                new ModelEvent.ToolUse(
                    "resource-1",
                    "read_skill_resource",
                    Map.of("name", "release-checklist", "path", "references/release-template.md")),
                new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
        }

        String latestUserText = latestUserText(messages);
        String referenceSummary = latestSkillResourceContent(messages);
        String text = "What should I do next?".equals(latestUserText)
            ? "Reusing loaded release-checklist. Run mvn test before merging and summarize the remaining risk. Reference says: " + referenceSummary
            : "Loaded release-checklist. Run mvn test before merging and summarize the remaining risk. Reference says: " + referenceSummary;
        return List.of(
                new ModelEvent.TextDelta(text),
                new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
    }

    List<String> systemPrompts() {
        return List.copyOf(systemPrompts);
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

    private boolean hasSkillResource(List<Message> messages) {
        for (Message message : messages) {
            for (ContentBlock block : message.content()) {
                if (block instanceof ContentBlock.ToolResult toolResult
                        && toolResult.content() instanceof Map<?, ?> content
                        && "skill_resource".equals(content.get("type"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String latestSkillResourceContent(List<Message> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            Message message = messages.get(index);
            for (ContentBlock block : message.content()) {
                if (block instanceof ContentBlock.ToolResult toolResult
                        && toolResult.content() instanceof Map<?, ?> content
                        && "skill_resource".equals(content.get("type"))
                        && content.get("content") instanceof String body) {
                    return body.replace('\n', ' ').trim();
                }
            }
        }
        return "(no resource loaded)";
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
}