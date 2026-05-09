package com.mahitotsu.arachne.strands.agent.conversation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mahitotsu.arachne.strands.hooks.HookRegistrar;
import com.mahitotsu.arachne.strands.types.ContentBlock;
import com.mahitotsu.arachne.strands.types.Message;

/**
 * Maintains a bounded message window while avoiding dangling tool-result pairs.
 */
public class SlidingWindowConversationManager implements ConversationManager {

    private final int windowSize;
    private final boolean proactiveCompression;
    private int removedMessageCount;

    public SlidingWindowConversationManager() {
        this(40);
    }

    public SlidingWindowConversationManager(int windowSize) {
        this(windowSize, false);
    }

    public SlidingWindowConversationManager(int windowSize, boolean proactiveCompression) {
        if (windowSize < 0) {
            throw new IllegalArgumentException("windowSize must be zero or greater");
        }
        this.windowSize = windowSize;
        this.proactiveCompression = proactiveCompression;
    }

    public int getWindowSize() {
        return windowSize;
    }

    public int getRemovedMessageCount() {
        return removedMessageCount;
    }

    @Override
    public void registerHooks(HookRegistrar registrar) {
        if (!proactiveCompression) {
            return;
        }
        registrar.beforeModelCall(event -> applyManagement(event.messages()));
    }

    @Override
    public void applyManagement(List<Message> messages) {
        if (windowSize == 0) {
            removedMessageCount += messages.size();
            messages.clear();
            return;
        }

        if (messages.size() <= windowSize) {
            return;
        }

        int trimIndex = findTrimIndex(messages, messages.size() - windowSize);
        if (trimIndex >= messages.size()) {
            throw new ContextWindowOverflowException("Unable to reduce conversation history to a valid window.");
        }

        removedMessageCount += trimIndex;
        messages.subList(0, trimIndex).clear();
    }

    @Override
    public Map<String, Object> getState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("type", getClass().getSimpleName());
        state.put("windowSize", windowSize);
        state.put("removedMessageCount", removedMessageCount);
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
        Object restoredWindowSize = state.get("windowSize");
        if (restoredWindowSize instanceof Number number && number.intValue() != windowSize) {
            throw new ConversationStateException("Conversation manager window size mismatch: " + restoredWindowSize);
        }
        Object restoredRemovedCount = state.get("removedMessageCount");
        if (restoredRemovedCount instanceof Number number) {
            this.removedMessageCount = number.intValue();
        }
    }

    private int findTrimIndex(List<Message> messages, int candidate) {
        int trimIndex = candidate;
        while (trimIndex < messages.size()) {
            Message start = messages.get(trimIndex);
            boolean startsWithToolResult = containsToolResult(start);
            boolean startsWithIncompleteToolUse = containsToolUse(start)
                    && (trimIndex + 1 >= messages.size() || !containsToolResult(messages.get(trimIndex + 1)));
            if (!startsWithToolResult && !startsWithIncompleteToolUse) {
                return trimIndex;
            }
            trimIndex++;
        }
        // Fall back to the original candidate when no clean boundary exists in tool-heavy history.
        return candidate;
    }

    private boolean containsToolUse(Message message) {
        return message.content().stream().anyMatch(ContentBlock.ToolUse.class::isInstance);
    }

    private boolean containsToolResult(Message message) {
        return message.content().stream().anyMatch(ContentBlock.ToolResult.class::isInstance);
    }
}