package com.mahitotsu.arachne.strands.agent.conversation;

import java.util.List;
import java.util.Map;

import com.mahitotsu.arachne.strands.hooks.HookProvider;
import com.mahitotsu.arachne.strands.hooks.HookRegistrar;
import com.mahitotsu.arachne.strands.types.Message;

/**
 * Strategy for managing conversation history over time.
 */
public interface ConversationManager extends HookProvider {

    void applyManagement(List<Message> messages);

    default Map<String, Object> getState() {
        return Map.of("type", getClass().getSimpleName());
    }

    default void restore(Map<String, Object> state) {
    }

    @Override
    default void registerHooks(HookRegistrar registrar) {
    }
}