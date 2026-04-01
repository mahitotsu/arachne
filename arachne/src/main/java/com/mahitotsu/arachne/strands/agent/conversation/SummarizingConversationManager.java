package com.mahitotsu.arachne.strands.agent.conversation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.mahitotsu.arachne.strands.model.Model;
import com.mahitotsu.arachne.strands.model.ModelEvent;
import com.mahitotsu.arachne.strands.types.ContentBlock;
import com.mahitotsu.arachne.strands.types.Message;

/**
 * Replaces older conversation turns with a model-generated summary while preserving recent turns.
 */
public class SummarizingConversationManager implements ConversationManager {

        static final String DEFAULT_SYSTEM_PROMPT = "You summarize earlier conversation so the agent can continue the same task. "
            + "Keep stable user facts, unresolved requests, completed tool results, and current constraints. "
            + "Return concise plain text only and do not invent facts.";
        public static final String SUMMARY_MESSAGE_PREFIX = "Conversation summary:\n";

    private final Model summaryModel;
    private final int maxMessagesBeforeSummary;
    private final int preserveRecentMessages;
    private final String systemPrompt;
    private final ObjectMapper objectMapper;

    private String summary;
    private int summarizedMessageCount;

    public SummarizingConversationManager(Model summaryModel, int maxMessagesBeforeSummary, int preserveRecentMessages) {
        this(summaryModel, maxMessagesBeforeSummary, preserveRecentMessages, DEFAULT_SYSTEM_PROMPT);
    }

    public SummarizingConversationManager(
            Model summaryModel,
            int maxMessagesBeforeSummary,
            int preserveRecentMessages,
            String systemPrompt) {
        this(summaryModel, maxMessagesBeforeSummary, preserveRecentMessages, systemPrompt, new ObjectMapper());
    }

    SummarizingConversationManager(
            Model summaryModel,
            int maxMessagesBeforeSummary,
            int preserveRecentMessages,
            String systemPrompt,
            ObjectMapper objectMapper) {
        this.summaryModel = Objects.requireNonNull(summaryModel, "summaryModel must not be null");
        if (maxMessagesBeforeSummary < 2) {
            throw new IllegalArgumentException("maxMessagesBeforeSummary must be at least 2");
        }
        if (preserveRecentMessages < 1) {
            throw new IllegalArgumentException("preserveRecentMessages must be at least 1");
        }
        if (preserveRecentMessages >= maxMessagesBeforeSummary) {
            throw new IllegalArgumentException("preserveRecentMessages must be smaller than maxMessagesBeforeSummary");
        }
        this.maxMessagesBeforeSummary = maxMessagesBeforeSummary;
        this.preserveRecentMessages = preserveRecentMessages;
        this.systemPrompt = systemPrompt == null || systemPrompt.isBlank() ? DEFAULT_SYSTEM_PROMPT : systemPrompt;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public int getMaxMessagesBeforeSummary() {
        return maxMessagesBeforeSummary;
    }

    public int getPreserveRecentMessages() {
        return preserveRecentMessages;
    }

    public String getSummary() {
        return summary;
    }

    public int getSummarizedMessageCount() {
        return summarizedMessageCount;
    }

    @Override
    public void applyManagement(List<Message> messages) {
        if (messages.size() <= maxMessagesBeforeSummary) {
            return;
        }

        int splitIndex = findSplitIndex(messages, messages.size() - preserveRecentMessages);
        if (splitIndex <= 0) {
            return;
        }

        int existingSummaryMessageCount = hasSummaryMessage(messages) ? 1 : 0;
        if (splitIndex <= existingSummaryMessageCount) {
            return;
        }

        List<Message> messagesToSummarize = new ArrayList<>(messages.subList(existingSummaryMessageCount, splitIndex));
        if (messagesToSummarize.isEmpty()) {
            return;
        }

        String updatedSummary = summarize(messagesToSummarize);
        summarizedMessageCount += messagesToSummarize.size();
        summary = updatedSummary;

        List<Message> replacement = new ArrayList<>();
        replacement.add(summaryMessage(updatedSummary));
        replacement.addAll(messages.subList(splitIndex, messages.size()));

        messages.clear();
        messages.addAll(replacement);
    }

    @Override
    public Map<String, Object> getState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("type", getClass().getSimpleName());
        state.put("maxMessagesBeforeSummary", maxMessagesBeforeSummary);
        state.put("preserveRecentMessages", preserveRecentMessages);
        state.put("summary", summary);
        state.put("summarizedMessageCount", summarizedMessageCount);
        return state;
    }

    @Override
    public void restore(Map<String, Object> state) {
        if (state == null || state.isEmpty()) {
            return;
        }
        Object type = state.get("type");
        if (type != null && !getClass().getSimpleName().equals(type)) {
            throw new ConversationStateException("Conversation manager state type mismatch: " + type);
        }
        assertMatchingNumber(state.get("maxMessagesBeforeSummary"), maxMessagesBeforeSummary, "maxMessagesBeforeSummary");
        assertMatchingNumber(state.get("preserveRecentMessages"), preserveRecentMessages, "preserveRecentMessages");
        Object restoredSummary = state.get("summary");
        this.summary = restoredSummary instanceof String value && !value.isBlank() ? value : null;
        Object restoredCount = state.get("summarizedMessageCount");
        if (restoredCount instanceof Number number) {
            this.summarizedMessageCount = number.intValue();
        }
    }

    private void assertMatchingNumber(Object restoredValue, int expected, String label) {
        if (restoredValue instanceof Number number && number.intValue() != expected) {
            throw new ConversationStateException("Conversation manager " + label + " mismatch: " + restoredValue);
        }
    }

    private boolean hasSummaryMessage(List<Message> messages) {
        if (messages.isEmpty()) {
            return false;
        }
        Message first = messages.getFirst();
        if (first.role() != Message.Role.ASSISTANT || first.content().size() != 1) {
            return false;
        }
        ContentBlock block = first.content().getFirst();
        if (!(block instanceof ContentBlock.Text text)) {
            return false;
        }
        return text.text().startsWith(SUMMARY_MESSAGE_PREFIX);
    }

    private Message summaryMessage(String summaryText) {
        return Message.assistant(SUMMARY_MESSAGE_PREFIX + summaryText);
    }

    private String summarize(List<Message> messagesToSummarize) {
        String prompt = buildSummaryPrompt(messagesToSummarize);
        Iterable<ModelEvent> events = summaryModel.converse(List.of(Message.user(prompt)), List.of(), systemPrompt);
        StringBuilder text = new StringBuilder();
        for (ModelEvent event : events) {
            if (event instanceof ModelEvent.TextDelta delta) {
                text.append(delta.delta());
                continue;
            }
            if (event instanceof ModelEvent.ToolUse toolUse) {
                throw new ConversationException("Summary model attempted tool use: " + toolUse.name());
            }
        }
        String summarizedText = text.toString().trim();
        if (summarizedText.isEmpty()) {
            throw new ConversationException("Summary model returned an empty summary.");
        }
        return summarizedText;
    }

    private String buildSummaryPrompt(List<Message> messagesToSummarize) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Update the running conversation summary for future turns.\n\n");
        if (summary != null && !summary.isBlank()) {
            prompt.append("Existing summary:\n");
            prompt.append(summary);
            prompt.append("\n\n");
        }
        prompt.append("New conversation turns to fold into the summary:\n");
        prompt.append(renderTranscript(messagesToSummarize));
        prompt.append("\n\nReturn only the new summary text.");
        return prompt.toString();
    }

    private String renderTranscript(List<Message> messagesToSummarize) {
        StringBuilder transcript = new StringBuilder();
        for (Message message : messagesToSummarize) {
            transcript.append(message.role().name()).append(":\n");
            for (ContentBlock block : message.content()) {
                transcript.append("- ").append(renderBlock(block)).append("\n");
            }
        }
        return transcript.toString().trim();
    }

    private String renderBlock(ContentBlock block) {
        return switch (block) {
            case ContentBlock.Text text -> text.text();
            case ContentBlock.ToolUse toolUse -> "tool_use " + toolUse.name() + " input=" + writeJson(toolUse.input());
            case ContentBlock.ToolResult toolResult -> "tool_result status=" + toolResult.status() + " content=" + writeJson(toolResult.content());
        };
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ConversationException("Failed to serialize conversation content for summarization.", exception);
        }
    }

    private int findSplitIndex(List<Message> messages, int candidate) {
        int splitIndex = candidate;
        while (splitIndex < messages.size()) {
            Message start = messages.get(splitIndex);
            boolean startsWithToolResult = containsToolResult(start);
            boolean startsWithIncompleteToolUse = containsToolUse(start)
                    && (splitIndex + 1 >= messages.size() || !containsToolResult(messages.get(splitIndex + 1)));
            if (!startsWithToolResult && !startsWithIncompleteToolUse) {
                return splitIndex;
            }
            splitIndex++;
        }
        return messages.size();
    }

    private boolean containsToolUse(Message message) {
        return message.content().stream().anyMatch(ContentBlock.ToolUse.class::isInstance);
    }

    private boolean containsToolResult(Message message) {
        return message.content().stream().anyMatch(ContentBlock.ToolResult.class::isInstance);
    }
}